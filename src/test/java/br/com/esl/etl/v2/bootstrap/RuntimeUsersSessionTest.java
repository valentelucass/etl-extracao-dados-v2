package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeUsersJdbc;
import br.com.esl.etl.v2.plataforma.fonte.graphql.Bloco58GraphQlAccess;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlContractObservationConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchBinding;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatcher;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlanItem;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionResult;
import br.com.esl.etl.v2.plataforma.persistencia.controle.JdbcSqlServerRuntimeRecovery;
import br.com.esl.etl.v2.plataforma.persistencia.observabilidade.JdbcSqlServerObservabilityGateway;
import br.com.esl.etl.v2.plataforma.persistencia.usuarios.JdbcSqlServerUsuarioPromotionGateway;
import br.com.esl.etl.v2.plataforma.persistencia.usuarios.JdbcSqlServerUsuarioStagingGateway;
import br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Session/dispatcher layer; authority is separately traversed by RuntimeUsersIntegrationTest. */
class RuntimeUsersSessionTest {
    @Test
    void sourcePartitionMismatchFailsBeforeFactoryOrStaging() throws Exception {
        final var fixture = new Fixture();
        final var p = fixture.item.partition();
        fixture.partition =
                new ExecutionPartitionKey(
                        p.environment(),
                        p.sourceInstance(),
                        "foreign-tenant",
                        p.entity(),
                        p.mode(),
                        p.partitionStart(),
                        p.partitionEndExclusive());
        assertEquals(RuntimeExecutionResult.Status.FAILED, fixture.run().status());
        assertEquals(0, fixture.factories);
        assertEquals(0, fixture.fetches);
        assertEquals(
                0,
                fixture.jdbc.operations().stream()
                        .filter(op -> op.startsWith("stg.") || op.startsWith("core."))
                        .count());
    }

    @Test
    void leaseIsRenewedDuringTraversalAndTokensRemainIdenticalAcrossGateAndStreamer()
            throws Exception {
        final var fixture = new Fixture();
        fixture.jdbc.afterBatch(
                () -> fixture.clock.now = RuntimeUsersOperationalRequestTest.NOW.plusSeconds(21));
        assertEquals(RuntimeExecutionResult.Status.PUBLISHED, fixture.run().status());
        assertEquals(2, fixture.fetches);
        assertTrue(fixture.jdbc.heartbeats() >= 2);
        assertEquals(0, fixture.jdbc.openConnections());
    }

    @Test
    void usersLivePermitCannotRepeatAnUncertainApplyWithoutDurableReadback() throws Exception {
        final var fixture = new Fixture();
        fixture.jdbc.lose("apply");
        final var result = fixture.run();
        assertEquals(RuntimeExecutionResult.Status.RECOVERY_REQUIRED, result.status());
        final var calls = fixture.jdbc.operations();
        assertThrows(
                IllegalStateException.class,
                () -> result.recovery().orElseThrow().recoverPromotion());
        assertEquals(calls, fixture.jdbc.operations());
        assertEquals(1, fixture.jdbc.appliedOccurrences());
    }

    @Test
    void typedNoopReceiptIsReturnedWithoutSubstitutingGenericInsertedCounts() throws Exception {
        final var fixture = new Fixture();
        fixture.jdbc.lose("typed-noop");
        final var result = fixture.run();
        assertEquals(RuntimeExecutionResult.Status.PUBLISHED, result.status());
        assertEquals(0, result.publication().orElseThrow().insertedRows());
        assertEquals(2, result.publication().orElseThrow().noopRows());
        final var snapshot =
                fixture.recovery.read(
                        new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryRequest(
                                fixture.start,
                                fixture.request.plan.fingerprint(),
                                fixture.request.binding,
                                fixture.request.quality),
                        new CancellationSignal());
        assertEquals(result.publication(), snapshot.publication());
    }

    private static final class MutableClock extends Clock {
        Instant now = RuntimeUsersOperationalRequestTest.NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class Fixture {
        final MutableClock clock = new MutableClock();
        final RuntimeOperationalRequest request =
                RuntimeOperationalRequest.fromDocument(
                        RuntimeUsersOperationalRequestTest.configuration(),
                        RuntimeUsersOperationalRequestTest.document());
        final RuntimeUsersJdbc jdbc = new RuntimeUsersJdbc(RuntimeUsersOperationalRequestTest.NOW);
        final RuntimeExecutionPlanItem item;
        final ControlPlaneStart start;
        final JdbcSqlServerRuntimeRecovery recovery =
                new JdbcSqlServerRuntimeRecovery(
                        jdbc.dataSource(), Duration.ofSeconds(10), Duration.ofSeconds(20));
        ExecutionPartitionKey partition;
        int factories;
        int fetches;

        Fixture() throws Exception {
            final RuntimeExecutionPlanItem[] selected = new RuntimeExecutionPlanItem[1];
            request.plan.forEach(value -> selected[0] = value);
            item = selected[0];
            partition = item.partition();
            start =
                    new ControlPlaneStart(
                            item.request().executionId(),
                            request.plan.cycleId(),
                            partition,
                            item.request().windowStrategy().name(),
                            item.definition().contract(),
                            item.definition().configuration(),
                            item.request().idempotencyKey(),
                            item.request().replayOfExecutionId(),
                            item.definition().leaseDuration(),
                            clock.instant());
        }

        RuntimeExecutionResult run() {
            final var guard =
                    new ContractRunGuard(
                            request.binding,
                            request.release,
                            request.compatibility,
                            start,
                            ignored -> {});
            final var observation =
                    new GraphQlContractObservationConfiguration(
                            GraphQlReadOperation.USERS_SNAPSHOT,
                            ContractObservationLimits.runtimeDefaults(),
                            ContractResponsePathBoundary.forRuntime(
                                    request.release, request.compatibility),
                            request.binding.runtimeConfigurationFingerprint());
            final var handler =
                    new LocalUsuariosRuntime(
                            partition,
                            guard,
                            request.users.limits(),
                            (bound, token) -> {
                                factories++;
                                return Bloco58GraphQlAccess.enforce(
                                        page -> {
                                            fetches++;
                                            return Bloco58GraphQlAccess.parse(
                                                    RuntimeUsersIntegrationTest.page(
                                                            fetches == 1,
                                                            fetches == 1 ? "synthetic-a" : null,
                                                            "{\"id\":" + fetches + "}"),
                                                    page,
                                                    observation);
                                        },
                                        observation,
                                        bound,
                                        token);
                            },
                            new JdbcSqlServerUsuarioStagingGateway(jdbc.dataSource()),
                            new JdbcSqlServerUsuarioPromotionGateway(jdbc.dataSource()),
                            new FailClosedDataQualityEngine(
                                    new JdbcSqlServerObservabilityGateway(
                                            jdbc.dataSource(), clock, Duration.ofSeconds(10))),
                            request.quality);
            return new RuntimeDispatcher(
                            new JdbcSqlServerControlPlane(jdbc.dataSource()),
                            clock,
                            recovery,
                            new RuntimeDispatchBinding(item.definition().id(), handler))
                    .dispatch(request.plan, new CancellationSignal())
                    .result(item.definition().id());
        }
    }
}
