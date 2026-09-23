package br.com.esl.etl.v2.plataforma.fonte.dataexport;

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
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchBinding;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatcher;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlan;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Fonte sintética; todos os adapters e todas as conexões são os reais do laboratório. */
final class RuntimePhysicalFixture {
    static final Instant NOW = Instant.parse("2036-03-19T18:42:10Z");
    static final ImmutableFingerprint PLAN = new ImmutableFingerprint("plan-1", "a".repeat(64));

    private RuntimePhysicalFixture() {}

    static final class Fixture {
        String barrier;
        Duration lease = Duration.ofSeconds(120);
        String status = "finished";
        Instant freshness = NOW;
        final Clock clock = Clock.systemUTC();
        final javax.sql.DataSource dataSource;
        final JdbcSqlServerControlPlane control;
        final UUID cycle;
        final String namespace;
        final DataQualityPolicyReference policy;
        final CancellationSignal cancellation = new CancellationSignal();
        ExecutionMode mode = ExecutionMode.BACKFILL;
        Optional<UUID> replay = Optional.empty();

        Fixture(
                final javax.sql.DataSource dataSource,
                final UUID cycle,
                final String namespace,
                final DataQualityPolicyReference policy) {
            this.dataSource = dataSource;
            this.control = new JdbcSqlServerControlPlane(dataSource);
            this.cycle = cycle;
            this.namespace = namespace;
            this.policy = policy;
        }

        br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryPort recoveryPort() {
            final var delegate =
                    new br.com.esl.etl.v2.plataforma.persistencia.controle
                            .JdbcSqlServerRuntimeRecovery(
                            dataSource, Duration.ofSeconds(10), Duration.ofSeconds(20));
            return barrier == null
                    ? delegate
                    : new RuntimePhysicalRecoveryBarrier(delegate, barrier);
        }

        RuntimeExecutionPlan plan(final Work work) {
            return RuntimeWorkloadRegistry.of(work.definition)
                    .plan(
                            new RuntimePlanningRequest(
                                    cycle, "LOCAL_SHADOW", PLAN, NOW, work.request));
        }

        RuntimeDispatcher dispatcher(final Work work) {
            return new RuntimeDispatcher(
                    control,
                    clock,
                    recoveryPort(),
                    new RuntimeDispatchBinding(work.id, work.handler));
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
        final Fixture fixture;

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
            this.fixture = fixture;
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
                            fixture.namespace,
                            fixture.namespace,
                            entity,
                            binding.contractFingerprint(),
                            binding.configurationFingerprint(),
                            fixture.lease,
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
                            fixture.namespace,
                            fixture.namespace,
                            entity,
                            fixture.mode,
                            NOW,
                            NOW.plusSeconds(3600));
            final var start =
                    new ControlPlaneStart(
                            execution,
                            fixture.cycle,
                            partition,
                            "INTERVAL",
                            binding.contractFingerprint(),
                            binding.configurationFingerprint(),
                            execution.toString(),
                            fixture.replay,
                            fixture.lease,
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
                                    fixture.dataSource, fixture.clock, Duration.ofSeconds(5)));
            handler =
                    template == DataExportTemplate.COLETAS
                            ? LocalColetasFretesRuntime.coletas(
                                    input,
                                    new JdbcSqlServerColetaStagingGateway(fixture.dataSource),
                                    new JdbcSqlServerColetaPromotionGateway(fixture.dataSource),
                                    quality,
                                    fixture.policy)
                            : LocalColetasFretesRuntime.fretes(
                                    input,
                                    new JdbcSqlServerFreteStagingGateway(fixture.dataSource),
                                    new JdbcSqlServerFretePromotionGateway(fixture.dataSource),
                                    quality,
                                    fixture.policy);
        }

        private List<JsonNode> rows() {
            final List<JsonNode> rows = new ArrayList<>();
            for (int index = 0; index < 3; index++) {
                rows.add(
                        JsonNodeFactory.instance
                                .objectNode()
                                .put("id", index % 3 + 1)
                                .put("sequence_code", index % 3 + 1)
                                .put("status", fixture.status)
                                .put("status_updated_at", fixture.freshness.toString())
                                .put("servico_em", NOW.toString()));
            }
            return rows;
        }
    }
}
