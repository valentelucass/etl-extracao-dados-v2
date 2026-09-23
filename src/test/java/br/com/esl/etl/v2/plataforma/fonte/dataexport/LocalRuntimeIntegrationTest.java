package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.LocalColetasFretesRuntime;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchBinding;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchResult;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatcher;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlan;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionResult;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePlanningRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadDefinition;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadHandler;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadId;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaPromotionGateway;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.fretes.JdbcSqlServerFretePromotionGateway;
import br.com.esl.etl.v2.plataforma.persistencia.fretes.JdbcSqlServerFreteStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.observabilidade.JdbcSqlServerObservabilityGateway;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Aplicação e adapters JDBC reais sobre gateways sintéticos; não é uma IT de SQL Server. */
class LocalRuntimeIntegrationTest {
    static final Instant NOW = Instant.parse("2036-03-19T18:42:10Z");
    static final UUID CYCLE = UUID.fromString("00000000-0000-0000-0000-000000000100");
    static final ImmutableFingerprint PLAN = new ImmutableFingerprint("plan-1", "a".repeat(64));
    static final DataQualityPolicyReference POLICY =
            new DataQualityPolicyReference("synthetic-quality-1", "b".repeat(64));

    @Test
    void executesRealVerticalsSeriallyAndPublishesOnlyAfterGates() {
        final Fixture fixture = new Fixture();
        final Work coletas = fixture.work(DataExportTemplate.COLETAS, "aa-coletas");
        final Work fretes = fixture.work(DataExportTemplate.FRETES, "bb-fretes", coletas.id);
        final RuntimeDispatchResult result = fixture.dispatch(coletas, fretes);
        assertEquals(2, result.outcome().publishedEntities());
        assertEquals(RuntimeExitCategory.SUCCESS, result.outcome().exitCategory());
        assertEquals(List.of(100, 100, 51, 100, 100, 51), fixture.jdbc.batchSizes);
        final int publication =
                fixture.jdbc.operations.indexOf("core.usp_apply_reconcile_publish_coletas");
        final int freightBatch = fixture.jdbc.operations.indexOf("stg.usp_stage_frete_record");
        assertTrue(publication < freightBatch);
        assertEquals(0, fixture.jdbc.openConnections);
        assertEquals(1, fixture.jdbc.attempts.get(coletas.execution.toString()).applies);
        assertTrue(result.result(fretes.id).publication().isPresent());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void stageFailureCannotPublishAndRollsBackCurrentBatch(final DataExportTemplate template) {
        final Fixture fixture = new Fixture();
        fixture.jdbc.failBatch = 2;
        final Work work = fixture.work(template, "aa-work");
        final var result = fixture.dispatch(work).result(work.id);
        assertEquals(RuntimeExecutionResult.Status.FAILED, result.status());
        assertEquals(100, fixture.jdbc.attempts.get(work.execution.toString()).rows);
        assertEquals(1, fixture.jdbc.rollbacks);
        assertFalse(
                fixture.jdbc.operations.stream().anyMatch(op -> op.startsWith("core.usp_prepare")));
        assertEquals(0, fixture.jdbc.openConnections);
    }

    @Test
    void blocksDependentButPreservesIndependentConfirmedPublication() {
        final Fixture fixture = new Fixture();
        final Work independent = fixture.work(DataExportTemplate.FRETES, "aa-independent");
        final Work coletas = fixture.work(DataExportTemplate.COLETAS, "bb-coletas");
        final Work dependent = fixture.work(DataExportTemplate.FRETES, "cc-dependent", coletas.id);
        fixture.jdbc.failBatch = 5;
        final var result = fixture.dispatch(independent, coletas, dependent);
        assertEquals(1, result.outcome().publishedEntities());
        assertEquals(1, result.outcome().failedEntities());
        assertEquals(1, result.outcome().blockedEntities());
        assertEquals(0, dependent.fetches.get());
        assertEquals(
                ExecutionState.BLOCKED,
                fixture.jdbc.attempts.get(dependent.execution.toString()).state);
    }

    @Test
    void callbackWithoutPublicationDoesNotReleaseDependency() {
        final Fixture fixture = new Fixture();
        final Work first = fixture.work(DataExportTemplate.COLETAS, "aa-first");
        first.handler = session -> {};
        final Work dependent = fixture.work(DataExportTemplate.FRETES, "bb-next", first.id);
        final var result = fixture.dispatch(first, dependent);
        assertEquals(0, result.outcome().publishedEntities());
        assertEquals(RuntimeExecutionResult.Status.BLOCKED, result.result(dependent.id).status());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void cancellationDuringBatchRollsBackAndDoesNotPrepare(final DataExportTemplate template) {
        final Fixture fixture = new Fixture();
        fixture.jdbc.afterBatch = fixture.cancellation::cancel;
        final Work work = fixture.work(template, "aa-work");
        assertEquals(
                RuntimeExecutionResult.Status.CANCELLED,
                fixture.dispatch(work).result(work.id).status());
        assertEquals(0, fixture.jdbc.attempts.get(work.execution.toString()).rows);
        assertEquals(1, fixture.jdbc.rollbacks);
    }

    @Test
    void cancellationBeforeSourceDoesNotFetch() {
        final Fixture fixture = new Fixture();
        fixture.cancellation.cancel();
        final Work work = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        assertEquals(
                RuntimeExecutionResult.Status.CANCELLED,
                fixture.dispatch(work).result(work.id).status());
        assertEquals(0, work.fetches.get());
    }

    @Test
    void cancellationAfterCommitPreservesReceipt() {
        final Fixture fixture = new Fixture();
        fixture.jdbc.afterApply = fixture.cancellation::cancel;
        final Work work = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        assertEquals(
                RuntimeExecutionResult.Status.PUBLISHED,
                fixture.dispatch(work).result(work.id).status());
    }

    @Test
    void unavailablePageAuditFailsClosed() {
        final Fixture fixture = new Fixture();
        fixture.jdbc.failOperation = "ctl.usp_control_plane_record_page";
        final Work work = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        assertEquals(
                RuntimeExecutionResult.Status.FAILED,
                fixture.dispatch(work).result(work.id).status());
        assertEquals(0, fixture.jdbc.batches);
    }

    @Test
    void qualityFailureDoesNotCallApply() {
        final Fixture fixture = new Fixture();
        fixture.jdbc.failQuality = true;
        final Work work = fixture.work(DataExportTemplate.FRETES, "aa-work");
        assertEquals(
                RuntimeExecutionResult.Status.FAILED,
                fixture.dispatch(work).result(work.id).status());
        assertFalse(fixture.jdbc.operations.contains("core.usp_apply_reconcile_publish_fretes"));
    }

    @Test
    void unavailableQualityDoesNotCallApply() {
        final Fixture fixture = new Fixture();
        fixture.jdbc.failOperation = "recon.usp_evaluate_execution_data_quality";
        final Work work = fixture.work(DataExportTemplate.FRETES, "aa-work");
        assertEquals(
                RuntimeExecutionResult.Status.FAILED,
                fixture.dispatch(work).result(work.id).status());
        assertFalse(fixture.jdbc.operations.contains("core.usp_apply_reconcile_publish_fretes"));
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void lostCommitAcknowledgementRecoversExactPublicationWithoutReextracting(
            final DataExportTemplate template) {
        final Fixture fixture = new Fixture();
        fixture.jdbc.loseApplyResponse = true;
        final Work work = fixture.work(template, "aa-work");
        final var pending = fixture.dispatch(work).result(work.id);
        assertEquals(RuntimeExecutionResult.Status.RECOVERY_REQUIRED, pending.status());
        assertEquals(
                ExecutionState.PUBLISHED,
                fixture.jdbc.attempts.get(work.execution.toString()).state);
        final int fetched = work.fetches.get();
        final var recovered = pending.recovery().orElseThrow().recoverPromotion();
        assertEquals(RuntimeExecutionResult.Status.PUBLISHED, recovered.status());
        assertEquals(fetched, work.fetches.get());
        assertEquals(1, fixture.jdbc.attempts.get(work.execution.toString()).applies);
        assertThrows(
                IllegalStateException.class, pending.recovery().orElseThrow()::recoverPromotion);
        assertNotNull(pending.recovery().orElseThrow().failureCause().orElseThrow().getCause());
    }

    @Test
    void lostPrepareAcknowledgementResumesCandidateProtocol() {
        final Fixture fixture = new Fixture();
        fixture.jdbc.losePrepareResponse = true;
        final Work work = fixture.work(DataExportTemplate.FRETES, "aa-work");
        final var pending = fixture.dispatch(work).result(work.id);
        assertEquals(RuntimeExecutionResult.Status.RECOVERY_REQUIRED, pending.status());
        assertEquals(
                ExecutionState.PROMOTED,
                fixture.jdbc.attempts.get(work.execution.toString()).state);
        assertEquals(
                RuntimeExecutionResult.Status.PUBLISHED,
                pending.recovery().orElseThrow().recoverPromotion().status());
        assertEquals(2, work.fetches.get());
    }

    @Test
    void uncertainTransitionNeedsReadbackAndNeverRepeatsExtraction() {
        final Fixture fixture = new Fixture();
        fixture.jdbc.loseTransitionResponse = true;
        final Work work = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        final var pending = fixture.dispatch(work).result(work.id);
        assertEquals(RuntimeExecutionResult.Status.RECOVERY_REQUIRED, pending.status());
        assertThrows(
                IllegalStateException.class, pending.recovery().orElseThrow()::recoverPromotion);
        assertEquals(
                ExecutionState.EXTRACTED,
                fixture.jdbc.attempts.get(work.execution.toString()).state);
    }

    @Test
    void uncertainStartDoesNotFetchOrAssumePlanned() {
        final Fixture fixture = new Fixture();
        fixture.jdbc.loseStartResponse = true;
        final Work work = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        assertEquals(
                RuntimeExecutionResult.Status.RECOVERY_REQUIRED,
                fixture.dispatch(work).result(work.id).status());
        assertEquals(0, work.fetches.get());
    }

    @Test
    void missingOrWrongReceiptIsUncertainEvenWhenSqlMayHaveCommitted() {
        for (final boolean absent : List.of(true, false)) {
            final Fixture fixture = new Fixture();
            fixture.jdbc.omitPublication = absent;
            fixture.jdbc.wrongPublication = !absent;
            final Work work = fixture.work(DataExportTemplate.FRETES, "aa-work");
            assertEquals(
                    RuntimeExecutionResult.Status.RECOVERY_REQUIRED,
                    fixture.dispatch(work).result(work.id).status());
            assertEquals(
                    ExecutionState.PUBLISHED,
                    fixture.jdbc.attempts.get(work.execution.toString()).state);
        }
    }

    @Test
    void heartbeatIsBoundedAndLostLeaseStopsWork() {
        final Fixture fixture = new Fixture();
        final Work work = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        fixture.jdbc.afterBatch =
                () -> {
                    fixture.clock.now = fixture.clock.now.plusSeconds(21);
                    fixture.jdbc.loseLease = true;
                };
        assertEquals(
                RuntimeExecutionResult.Status.RECOVERY_REQUIRED,
                fixture.dispatch(work).result(work.id).status());
        assertEquals(2, fixture.jdbc.heartbeatCount);
        assertEquals(1, fixture.jdbc.batches);
        assertEquals(
                ExecutionState.EXTRACTING,
                fixture.jdbc.attempts.get(work.execution.toString()).state);
    }

    @Test
    void sourceContractDriftCannotReachCandidateSet() {
        final Fixture fixture = new Fixture();
        final Work work = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        work.drift = true;
        assertEquals(
                RuntimeExecutionResult.Status.FAILED,
                fixture.dispatch(work).result(work.id).status());
        assertEquals(0, fixture.jdbc.batches);
    }

    @Test
    void emptyTraversalCannotManufactureCompletenessOrPublish() {
        final Fixture fixture = new Fixture();
        final Work work = fixture.work(DataExportTemplate.FRETES, "aa-work");
        work.empty = true;
        assertEquals(
                RuntimeExecutionResult.Status.FAILED,
                fixture.dispatch(work).result(work.id).status());
        assertFalse(
                fixture.jdbc.operations.stream().anyMatch(op -> op.startsWith("core.usp_prepare")));
    }

    @Test
    void replayCarriesOriginAndNeverAdvancesIncrementalFrontier() {
        final Fixture fixture = new Fixture();
        final Work original = fixture.work(DataExportTemplate.COLETAS, "aa-original");
        fixture.dispatch(original);
        fixture.mode = ExecutionMode.REPLAY;
        fixture.replay = Optional.of(original.execution);
        final Work replay = fixture.work(DataExportTemplate.COLETAS, "aa-replay");
        final var result = fixture.dispatch(replay).result(replay.id);
        assertEquals(RuntimeExecutionResult.Status.PUBLISHED, result.status());
        assertTrue(result.publication().orElseThrow().incrementalFrontierAfter().isEmpty());
        assertEquals(
                original.execution.toString(),
                fixture.jdbc.attempts.get(replay.execution.toString()).start.get(16));
        assertEquals(2, replay.fetches.get());
    }

    @Test
    void rejectsMissingHandlerAndWrongDependencyWindowBeforeIo() {
        final Fixture fixture = new Fixture();
        final Work first = fixture.work(DataExportTemplate.COLETAS, "aa-first");
        final Work next = fixture.work(DataExportTemplate.FRETES, "bb-next", first.id);
        final var plan = fixture.plan(first, next);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeDispatcher(
                                        fixture.control,
                                        fixture.clock,
                                        new RuntimeDispatchBinding(first.id, first.handler))
                                .dispatch(plan, fixture.cancellation));
        next.request =
                new RuntimeExecutionRequest(
                        next.execution,
                        next.id,
                        ExecutionMode.INCREMENTAL,
                        RuntimeWindowStrategy.INTERVAL,
                        NOW.plusSeconds(1),
                        NOW.plusSeconds(3600),
                        "other-key");
        assertThrows(IllegalArgumentException.class, () -> fixture.dispatch(first, next));
        assertTrue(fixture.jdbc.operations.isEmpty());
    }

    @Test
    void sameOccurrenceCannotStartSourceAgainAfterPublication() {
        final Fixture fixture = new Fixture();
        final Work work = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        fixture.dispatch(work);
        final var retry = fixture.dispatch(work).result(work.id);
        assertEquals(RuntimeExecutionResult.Status.RECOVERY_REQUIRED, retry.status());
        assertEquals(2, work.fetches.get());
        assertEquals(
                ExecutionState.PUBLISHED,
                fixture.jdbc.attempts.get(work.execution.toString()).state);
    }

    @Test
    void newAttemptAfterFailureUsesNewOccurrenceAndBatchOne() {
        final Fixture fixture = new Fixture();
        final Work first = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        fixture.jdbc.failBatch = 2;
        fixture.dispatch(first);
        final Work retry = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        assertEquals(
                RuntimeExecutionResult.Status.PUBLISHED,
                fixture.dispatch(retry).result(retry.id).status());
        assertEquals(100, fixture.jdbc.attempts.get(first.execution.toString()).rows);
        assertEquals(251, fixture.jdbc.attempts.get(retry.execution.toString()).rows);
        assertEquals(2, fixture.jdbc.attempts.size());
    }

    @Test
    void rejectsInvalidReplayAndDuplicateOccurrenceBeforeIo() {
        final Fixture fixture = new Fixture();
        final Work first = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeExecutionRequest(
                                first.execution,
                                first.id,
                                ExecutionMode.REPLAY,
                                RuntimeWindowStrategy.INTERVAL,
                                NOW,
                                NOW.plusSeconds(60),
                                "replay-missing"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeExecutionRequest(
                                first.execution,
                                first.id,
                                ExecutionMode.REPLAY,
                                RuntimeWindowStrategy.INTERVAL,
                                NOW,
                                NOW.plusSeconds(60),
                                "replay-self",
                                Optional.of(first.execution)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeExecutionRequest(
                                first.execution,
                                first.id,
                                ExecutionMode.INCREMENTAL,
                                RuntimeWindowStrategy.INTERVAL,
                                NOW,
                                NOW.plusSeconds(60),
                                "replay-other-mode",
                                Optional.of(UUID.randomUUID())));
        final Work second = fixture.work(DataExportTemplate.FRETES, "bb-work");
        second.request =
                new RuntimeExecutionRequest(
                        first.execution,
                        second.id,
                        ExecutionMode.INCREMENTAL,
                        RuntimeWindowStrategy.INTERVAL,
                        NOW,
                        NOW.plusSeconds(3600),
                        "different-key");
        assertThrows(IllegalArgumentException.class, () -> fixture.dispatch(first, second));
        second.request =
                new RuntimeExecutionRequest(
                        second.execution,
                        second.id,
                        ExecutionMode.INCREMENTAL,
                        RuntimeWindowStrategy.INTERVAL,
                        NOW,
                        NOW.plusSeconds(3600),
                        first.request.idempotencyKey());
        assertThrows(IllegalArgumentException.class, () -> fixture.dispatch(first, second));
        assertTrue(fixture.jdbc.operations.isEmpty());
    }

    @Test
    void clockRegressionStopsBeforeMoreStaging() {
        final Fixture fixture = new Fixture();
        final Work work = fixture.work(DataExportTemplate.FRETES, "aa-work");
        fixture.jdbc.afterBatch = () -> fixture.clock.now = NOW.minusSeconds(1);
        assertEquals(
                RuntimeExecutionResult.Status.FAILED,
                fixture.dispatch(work).result(work.id).status());
        assertEquals(1, fixture.jdbc.batches);
        assertEquals(0, fixture.jdbc.openConnections);
    }

    static final class Fixture {
        final MutableClock clock = new MutableClock();
        final RuntimeSyntheticJdbc jdbc;
        final JdbcSqlServerControlPlane control;

        Fixture() {
            this(new RuntimeSyntheticJdbc(NOW));
        }

        Fixture(final RuntimeSyntheticJdbc jdbc) {
            this.jdbc = jdbc;
            control = new JdbcSqlServerControlPlane(jdbc.dataSource());
        }

        final CancellationSignal cancellation = new CancellationSignal();
        ExecutionMode mode = ExecutionMode.INCREMENTAL;
        Optional<UUID> replay = Optional.empty();

        br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryPort recoveryPort() {
            return new br.com.esl.etl.v2.plataforma.persistencia.controle
                    .JdbcSqlServerRuntimeRecovery(
                    jdbc.dataSource(), Duration.ofSeconds(5), Duration.ofSeconds(10));
        }

        Work work(
                final DataExportTemplate template,
                final String name,
                final RuntimeWorkloadId... dependencies) {
            return new Work(this, template, name, dependencies);
        }

        RuntimeExecutionPlan plan(final Work... works) {
            return RuntimeWorkloadRegistry.of(
                            java.util.Arrays.stream(works)
                                    .map(w -> w.definition)
                                    .toArray(RuntimeWorkloadDefinition[]::new))
                    .plan(
                            new RuntimePlanningRequest(
                                    CYCLE,
                                    "LOCAL_SHADOW",
                                    PLAN,
                                    NOW,
                                    java.util.Arrays.stream(works)
                                            .map(w -> w.request)
                                            .toArray(RuntimeExecutionRequest[]::new)));
        }

        RuntimeDispatchResult dispatch(final Work... works) {
            final var dispatcher =
                    new RuntimeDispatcher(
                            control,
                            clock,
                            recoveryPort(),
                            java.util.Arrays.stream(works)
                                    .map(w -> new RuntimeDispatchBinding(w.id, w.handler))
                                    .toArray(RuntimeDispatchBinding[]::new));
            return dispatcher.dispatch(plan(works), cancellation);
        }
    }

    static final class Work {
        final UUID execution;
        final ContractExecutionBinding binding;
        final RuntimeWorkloadId id;
        final RuntimeWorkloadDefinition definition;
        final AtomicInteger fetches = new AtomicInteger();
        RuntimeExecutionRequest request;
        RuntimeWorkloadHandler handler;
        boolean drift;
        boolean empty;

        Work(
                final Fixture fixture,
                final DataExportTemplate template,
                final String name,
                final RuntimeWorkloadId... dependencies) {
            this(fixture, template, name, UUID.randomUUID(), dependencies);
        }

        Work(
                final Fixture fixture,
                final DataExportTemplate template,
                final String name,
                final UUID execution,
                final RuntimeWorkloadId... dependencies) {
            this.execution = execution;
            id = new RuntimeWorkloadId(name);
            final var limits = ContractObservationLimits.runtimeDefaults();
            final var info =
                    new DataExportTemplateInfo(
                            template,
                            200,
                            Optional.empty(),
                            List.of(
                                    new DataExportMetadataField(
                                            "id", Optional.of("integer"), Optional.empty())),
                            List.of());
            final var authoring = DataExportContractAdapter.forSyntheticFixtures(limits);
            final var release =
                    SourceContractRelease.create(
                            ContractSourceKind.DATA_EXPORT,
                            DataExportContractAdapter.documentReference(template),
                            "synthetic-runtime-vertical-1",
                            DataExportContractAdapter.metadata(info),
                            authoring.response(
                                    JsonNodeFactory.instance.arrayNode().addAll(rows()),
                                    DataExportResponseForm.ROOT_ARRAY,
                                    "/id"));
            final var policy = ContractTestSupport.policy(release);
            final var runtimeFingerprint = new ImmutableFingerprint("runtime-1", "c".repeat(64));
            binding =
                    ContractExecutionBinding.create(execution, release, policy, runtimeFingerprint);
            final String entity = template == DataExportTemplate.COLETAS ? "coletas" : "fretes";
            definition =
                    new RuntimeWorkloadDefinition(
                            id,
                            "ESL",
                            "synthetic-source",
                            "synthetic-tenant",
                            entity,
                            binding.contractFingerprint(),
                            binding.configurationFingerprint(),
                            Duration.ofSeconds(60),
                            dependencies);
            request =
                    new RuntimeExecutionRequest(
                            execution,
                            id,
                            fixture.mode,
                            RuntimeWindowStrategy.INTERVAL,
                            NOW,
                            NOW.plusSeconds(3600),
                            execution.toString(),
                            fixture.replay);
            final var partition =
                    new ExecutionPartitionKey(
                            "LOCAL_SHADOW",
                            "synthetic-source",
                            "synthetic-tenant",
                            entity,
                            fixture.mode,
                            NOW,
                            NOW.plusSeconds(3600));
            final var start =
                    new ControlPlaneStart(
                            execution,
                            CYCLE,
                            partition,
                            "INTERVAL",
                            binding.contractFingerprint(),
                            binding.configurationFingerprint(),
                            execution.toString(),
                            fixture.replay,
                            Duration.ofSeconds(60),
                            NOW);
            final var guard = new ContractRunGuard(binding, release, policy, start, alert -> {});
            final var boundary = ContractResponsePathBoundary.forRuntime(release, policy);
            final var adapter = new DataExportContractAdapter(limits, boundary);
            final var observation =
                    new DataExportContractObservationConfiguration(
                            template,
                            DataExportResponseForm.ROOT_ARRAY,
                            "/id",
                            limits,
                            boundary,
                            runtimeFingerprint);
            final var bundle =
                    DataExportHttpGatewayBundle.contractBound(
                            page -> {
                                fetches.incrementAndGet();
                                final List<JsonNode> values =
                                        page.page() == 1 && !empty ? rows() : List.of();
                                if (drift && !values.isEmpty()) {
                                    ((com.fasterxml.jackson.databind.node.ObjectNode) values.get(0))
                                            .put("id", "bad");
                                }
                                return DataExportPageResponse.observed(
                                        values,
                                        adapter.response(
                                                JsonNodeFactory.instance.arrayNode().addAll(values),
                                                DataExportResponseForm.ROOT_ARRAY,
                                                "/id"),
                                        limits,
                                        boundary);
                            },
                            ignored -> info,
                            observation);
            final var input =
                    new DataExportRuntimeWorkload.Input(
                            partition,
                            guard,
                            new DataExportPageRequest(
                                    template,
                                    new BusinessDateRange(
                                            LocalDate.of(2036, 3, 19), LocalDate.of(2036, 3, 19)),
                                    Optional.empty(),
                                    1,
                                    100,
                                    template.defaultOrderBy()),
                            new DataExportExtractionLimits(4, 1000, 100),
                            bundle);
            final var quality =
                    new FailClosedDataQualityEngine(
                            new JdbcSqlServerObservabilityGateway(
                                    fixture.jdbc.dataSource(),
                                    fixture.clock,
                                    Duration.ofSeconds(5)));
            handler =
                    template == DataExportTemplate.COLETAS
                            ? LocalColetasFretesRuntime.coletas(
                                    input,
                                    new JdbcSqlServerColetaStagingGateway(
                                            fixture.jdbc.dataSource()),
                                    new JdbcSqlServerColetaPromotionGateway(
                                            fixture.jdbc.dataSource()),
                                    quality,
                                    POLICY)
                            : LocalColetasFretesRuntime.fretes(
                                    input,
                                    new JdbcSqlServerFreteStagingGateway(fixture.jdbc.dataSource()),
                                    new JdbcSqlServerFretePromotionGateway(
                                            fixture.jdbc.dataSource()),
                                    quality,
                                    POLICY);
        }

        private static List<JsonNode> rows() {
            final List<JsonNode> rows = new ArrayList<>();
            for (int index = 0; index < 251; index++) {
                rows.add(
                        JsonNodeFactory.instance
                                .objectNode()
                                .put("id", index % 3 + 1)
                                .put("sequence_code", index % 3 + 1)
                                .put("status", "finished")
                                .put("status_updated_at", NOW.toString())
                                .put("servico_em", NOW.toString()));
            }
            return rows;
        }
    }

    static final class MutableClock extends Clock {
        Instant now = NOW;

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
}
