package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoPromotionGateway;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaPromotionGateway;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoPromotionGateway;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractAdapter;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractObservationConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseForm;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRuntimeWorkload;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfo;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfoParser;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchBinding;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatcher;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePlanningRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadDefinition;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadId;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityRunSummary;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityState;
import br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LocalFiveVerticalRuntimeTest {
    private static final Instant NOW = Instant.parse("2032-02-29T03:00:00Z");

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    void composesTypedTraversalAndBindsPromotionThroughTheRealDispatcher(
            final DataExportTemplate template) throws Exception {
        final var execution = UUID.randomUUID();
        final var cycle = UUID.randomUUID();
        final var entity = template.name().toLowerCase(Locale.ROOT);
        final var id = new RuntimeWorkloadId(entity);
        final var release = RuntimeFiveVerticalContract.release(template);
        final var policy = ContractTestSupport.policy(release);
        final var runtime = new ImmutableFingerprint("synthetic-v1", "a".repeat(64));
        final var binding = ContractExecutionBinding.create(execution, release, policy, runtime);
        final var partition =
                new ExecutionPartitionKey(
                        "LOCAL_SHADOW",
                        "LOCAL_V2",
                        "LOCAL_V2",
                        entity,
                        ExecutionMode.BACKFILL,
                        NOW,
                        NOW.plusSeconds(86400));
        final var start =
                new ControlPlaneStart(
                        execution,
                        cycle,
                        partition,
                        "INTERVAL",
                        binding.contractFingerprint(),
                        binding.configurationFingerprint(),
                        execution.toString(),
                        Optional.empty(),
                        Duration.ofSeconds(30),
                        NOW);
        final var guard = new ContractRunGuard(binding, release, policy, start, ignored -> {});
        final var limits = ContractObservationLimits.runtimeDefaults();
        final var boundary = ContractResponsePathBoundary.forRuntime(release, policy);
        final var adapter = new DataExportContractAdapter(limits, boundary);
        final var observation =
                new DataExportContractObservationConfiguration(
                        template,
                        DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                        release.response().keyPath(),
                        limits,
                        boundary,
                        runtime);
        final var parsed =
                new DataExportTemplateInfoParser()
                        .parse(
                                new ObjectMapper()
                                        .readTree(
                                                Path.of(
                                                                "src/test/resources/runtime-laboratory-bloco55/"
                                                                        + template.templateId()
                                                                        + "-info.json")
                                                        .toFile()));
        final var info =
                new DataExportTemplateInfo(
                        template, 200, Optional.empty(), true, parsed.fields(), parsed.filters());
        final var events = new ArrayList<String>();
        final var gateways =
                RuntimeBootstrapTestFixture.boundGateways(
                        page -> {
                            final List<JsonNode> rows =
                                    page.page() == 1
                                            ? List.of(
                                                    RuntimeFiveVerticalPipelineTest.row(
                                                            template, 0),
                                                    RuntimeFiveVerticalPipelineTest.row(
                                                            template, 1))
                                            : List.of();
                            final var envelope = JsonNodeFactory.instance.objectNode();
                            envelope.putArray("data").addAll(rows);
                            return RuntimeBootstrapTestFixture.observedPage(
                                    rows,
                                    adapter.response(
                                            envelope,
                                            DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                                            release.response().keyPath()),
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
                                        LocalDate.of(2032, 2, 29), LocalDate.of(2032, 2, 29)),
                                Optional.empty(),
                                1,
                                2,
                                template.defaultOrderBy()),
                        new DataExportExtractionLimits(4, 16, 16),
                        gateways);
        final var qualityPolicy = new DataQualityPolicyReference("synthetic-dq-v1", "f".repeat(64));
        final var quality =
                new FailClosedDataQualityEngine(
                        request -> {
                            events.add("DQ");
                            return new DataQualityRunSummary(
                                    execution,
                                    qualityPolicy,
                                    "e".repeat(64),
                                    4,
                                    4,
                                    4,
                                    0,
                                    2,
                                    0,
                                    DataQualityState.PASSED,
                                    NOW);
                        });
        final var promotion = new Promotion(events);
        final var workload =
                switch (template) {
                    case MANIFESTOS ->
                            LocalFiveVerticalRuntime.manifestos(
                                    input,
                                    batch -> {
                                        assertEquals(2, batch.size());
                                        events.add("STAGE");
                                    },
                                    promotion,
                                    quality,
                                    qualityPolicy);
                    case COTACOES ->
                            LocalFiveVerticalRuntime.cotacoes(
                                    input,
                                    batch -> {
                                        assertEquals(2, batch.size());
                                        events.add("STAGE");
                                    },
                                    promotion,
                                    73,
                                    quality,
                                    qualityPolicy);
                    case LOCALIZACAO_CARGAS ->
                            LocalFiveVerticalRuntime.localizacao(
                                    input,
                                    batch -> {
                                        assertEquals(2, batch.size());
                                        events.add("STAGE");
                                    },
                                    promotion,
                                    quality,
                                    qualityPolicy);
                    default -> throw new AssertionError();
                };
        final var definition =
                new RuntimeWorkloadDefinition(
                        id,
                        "DATA_EXPORT",
                        "LOCAL_V2",
                        "LOCAL_V2",
                        entity,
                        binding.contractFingerprint(),
                        binding.configurationFingerprint(),
                        Duration.ofSeconds(30));
        final var plan =
                RuntimeWorkloadRegistry.of(definition)
                        .plan(
                                new RuntimePlanningRequest(
                                        cycle,
                                        "LOCAL_SHADOW",
                                        runtime,
                                        NOW,
                                        new RuntimeExecutionRequest(
                                                execution,
                                                id,
                                                ExecutionMode.BACKFILL,
                                                RuntimeWindowStrategy.INTERVAL,
                                                NOW,
                                                NOW.plusSeconds(86400),
                                                execution.toString())));
        final var control =
                (ControlPlane)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {ControlPlane.class},
                                (p, method, args) -> null);
        final var result =
                new RuntimeDispatcher(
                                control,
                                Clock.fixed(NOW, ZoneOffset.UTC),
                                new RuntimeDispatchBinding(id, workload))
                        .dispatch(plan, CancellationToken.none());
        assertEquals(RuntimeExitCategory.SUCCESS, result.outcome().exitCategory());
        assertEquals(1, result.outcome().publishedEntities());
        assertEquals(List.of("STAGE", "PREPARE", "DQ", "APPLY"), events);
        assertThrows(
                NullPointerException.class,
                () ->
                        LocalFiveVerticalRuntime.manifestos(
                                input, null, promotion, quality, qualityPolicy));
        if (template == DataExportTemplate.COTACOES) {
            assertEquals(73, promotion.reference);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            LocalFiveVerticalRuntime.cotacoes(
                                    input, batch -> {}, promotion, 0, quality, qualityPolicy));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            LocalFiveVerticalRuntime.manifestos(
                                    input, batch -> {}, promotion, quality, qualityPolicy));
        }
    }

    private static final class Promotion
            implements ManifestoPromotionGateway,
                    LocalizacaoCargaPromotionGateway,
                    CotacaoPromotionGateway {
        private final List<String> events;
        private long reference;

        Promotion(final List<String> events) {
            this.events = events;
        }

        @Override
        public void prepareCandidateSet(final ContractPromotionPermit permit) {
            events.add("PREPARE");
        }

        @Override
        public void prepareCandidateSet(
                final ContractPromotionPermit permit, final CancellationToken cancellation) {
            cancellation.throwIfCancellationRequested();
            prepareCandidateSet(permit);
        }

        @Override
        public StagingPublicationResult applyReconcileAndPublish(
                final ContractPromotionPermit permit,
                final DataQualityPromotionPermit quality,
                final long referenceReleaseId) {
            reference = referenceReleaseId;
            return applyReconcileAndPublish(permit, quality);
        }

        @Override
        public StagingPublicationResult applyReconcileAndPublish(
                final ContractPromotionPermit permit,
                final DataQualityPromotionPermit quality,
                final CancellationToken cancellation) {
            cancellation.throwIfCancellationRequested();
            return applyReconcileAndPublish(permit, quality);
        }

        @Override
        public StagingPublicationResult applyReconcileAndPublish(
                final ContractPromotionPermit permit, final DataQualityPromotionPermit quality) {
            assertEquals(permit.executionId(), quality.executionId());
            events.add("APPLY");
            return new StagingPublicationResult(
                    permit.executionId(),
                    2,
                    2,
                    0,
                    0,
                    0,
                    0,
                    NOW,
                    NOW,
                    Optional.empty(),
                    Optional.empty());
        }
    }
}
