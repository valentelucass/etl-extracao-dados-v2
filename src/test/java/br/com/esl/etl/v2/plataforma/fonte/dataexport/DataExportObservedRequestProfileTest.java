package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DataExportObservedRequestProfileTest {
    @Test
    void comparesAllSevenComponentsAndPreservesValueEqualityOfObservedProfiles() {
        final var dates = ObservedFlowTestSupport.DATES;
        final var order = DataExportTemplate.COLETAS.defaultOrderBy();
        final var request =
                new DataExportPageRequest(
                        DataExportTemplate.COLETAS, dates, Optional.empty(), 1, 50, order);
        assertEquals(request, request);
        assertNotEquals(null, request);
        assertNotEquals("not-a-request", request);
        assertNotEquals(request, request.withPage(2));
        assertNotEquals(
                request,
                new DataExportPageRequest(
                        DataExportTemplate.FRETES, dates, Optional.empty(), 1, 50, order));
        assertNotEquals(
                request,
                new DataExportPageRequest(
                        DataExportTemplate.COLETAS,
                        new BusinessDateRange(LocalDate.of(2036, 3, 18), LocalDate.of(2036, 3, 19)),
                        Optional.empty(),
                        1,
                        50,
                        order));
        assertNotEquals(
                request,
                new DataExportPageRequest(
                        DataExportTemplate.COLETAS,
                        dates,
                        Optional.of(
                                new SourceDateTimeRange(
                                        java.time.Instant.parse("2036-03-19T00:00:00Z"),
                                        java.time.Instant.parse("2036-03-19T01:00:00Z"))),
                        1,
                        50,
                        order));
        assertNotEquals(
                request,
                new DataExportPageRequest(
                        DataExportTemplate.COLETAS, dates, Optional.empty(), 1, 51, order));
        assertNotEquals(
                request,
                new DataExportPageRequest(
                        DataExportTemplate.COLETAS,
                        dates,
                        Optional.empty(),
                        1,
                        50,
                        List.of("id asc")));
        final var release =
                DataExportObservedEntityContractCatalog.release(
                        DataExportTemplate.LOCALIZACAO_CARGAS);
        final var first =
                DataExportPageRequest.observed(
                        DataExportTemplate.LOCALIZACAO_CARGAS,
                        release,
                        ObservedFlowTestSupport.BASE,
                        dates,
                        1,
                        50);
        final var second =
                DataExportPageRequest.observed(
                        DataExportTemplate.LOCALIZACAO_CARGAS,
                        release,
                        ObservedFlowTestSupport.BASE,
                        dates,
                        1,
                        50);
        final var changedBase =
                DataExportPageRequest.observed(
                        DataExportTemplate.LOCALIZACAO_CARGAS,
                        release,
                        new ImmutableFingerprint("synthetic-runtime-v1", "f".repeat(64)),
                        dates,
                        1,
                        50);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, changedBase);
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    void realCatalogSubjectVersionAndPinnedShaRejectAlgorithmSubjectAndOriginSwaps(
            final DataExportTemplate template) {
        final var fixture = new ObservedFlowTestSupport(template, 50, "[]");
        final var selection = fixture.guard.sourceSelection();
        assertEquals(
                fixture.release.contractVersion(), fixture.release.contractFingerprint().version());
        assertTrue(fixture.request.profile().approves(fixture.request, selection));
        final var algorithmLabelAsVersion =
                new br.com.esl.etl.v2.plataforma.contrato.ContractSourceSelection(
                        selection.sourceKind(),
                        selection.documentReference(),
                        selection.contractVersion(),
                        new ImmutableFingerprint(
                                "source-contract-release-v1",
                                selection.contractFingerprint().sha256()),
                        selection.runtimeConfigurationFingerprint());
        final var swappedSubject =
                new br.com.esl.etl.v2.plataforma.contrato.ContractSourceSelection(
                        selection.sourceKind(),
                        selection.documentReference(),
                        "source-contract-release-v1",
                        selection.contractFingerprint(),
                        selection.runtimeConfigurationFingerprint());
        final var changedSha =
                new br.com.esl.etl.v2.plataforma.contrato.ContractSourceSelection(
                        selection.sourceKind(),
                        selection.documentReference(),
                        selection.contractVersion(),
                        new ImmutableFingerprint(
                                selection.contractFingerprint().version(), "0".repeat(64)),
                        selection.runtimeConfigurationFingerprint());
        final var changedKind =
                new br.com.esl.etl.v2.plataforma.contrato.ContractSourceSelection(
                        br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind.GRAPHQL,
                        selection.documentReference(),
                        selection.contractVersion(),
                        selection.contractFingerprint(),
                        selection.runtimeConfigurationFingerprint());
        final var changedDocument =
                new br.com.esl.etl.v2.plataforma.contrato.ContractSourceSelection(
                        selection.sourceKind(),
                        selection.documentReference() + "-other",
                        selection.contractVersion(),
                        selection.contractFingerprint(),
                        selection.runtimeConfigurationFingerprint());
        for (final var invalid :
                List.of(
                        algorithmLabelAsVersion,
                        swappedSubject,
                        changedSha,
                        changedKind,
                        changedDocument)) {
            assertFalse(fixture.request.profile().approves(fixture.request, invalid));
        }
        assertEquals(0, fixture.client.requests.size());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    void selectsOnlyPinnedReleaseAndPreservesTheWholeRequestAcrossPages(
            final DataExportTemplate template) {
        final var release = DataExportObservedEntityContractCatalog.release(template);
        final var request =
                DataExportPageRequest.observed(
                        template,
                        release,
                        ObservedFlowTestSupport.BASE,
                        ObservedFlowTestSupport.DATES,
                        1,
                        50);
        assertTrue(request.orderBy().isEmpty());
        assertEquals(request.filters(), request.withPage(2).filters());
        assertSame(request.profile(), request.withPage(2).profile());
        assertEquals(50, request.withPage(2).pageSize());
        assertEquals(
                release,
                DataExportRequestProfile.observedRelease(template, release.contractVersion()));
        assertThrows(
                IllegalArgumentException.class,
                () -> DataExportRequestProfile.observedRelease(template, "unknown"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DataExportPageRequest.observed(
                                template,
                                release,
                                ObservedFlowTestSupport.BASE,
                                ObservedFlowTestSupport.DATES,
                                1,
                                101));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportPageRequest(
                                template,
                                ObservedFlowTestSupport.DATES,
                                Optional.empty(),
                                1,
                                50,
                                null,
                                request.profile()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportPageRequest(
                                template,
                                ObservedFlowTestSupport.DATES,
                                Optional.empty(),
                                1,
                                50,
                                template.defaultOrderBy(),
                                request.profile()));
        final var differentPer =
                DataExportPageRequest.observed(
                        template,
                        release,
                        ObservedFlowTestSupport.BASE,
                        ObservedFlowTestSupport.DATES,
                        1,
                        100);
        assertNotEquals(request, differentPer);
        assertNotEquals(
                request.profile().runtimeFingerprint(ObservedFlowTestSupport.BASE),
                differentPer.profile().runtimeFingerprint(ObservedFlowTestSupport.BASE));
    }

    @Test
    void legacyConstructorJsonEqualityAndHashRetainSixComponentBehavior() throws Exception {
        final var template = DataExportTemplate.FRETES;
        final var dates = ObservedFlowTestSupport.DATES;
        final var request =
                new DataExportPageRequest(template, dates, Optional.empty(), 1, 50, null);
        final var old = new LegacySix(template, dates, Optional.empty(), 1, 50, List.of("id asc"));
        assertEquals(old.hashCode(), request.hashCode());
        assertEquals(List.of("id asc"), request.orderBy());
        assertSame(DataExportRequestProfile.LEGACY, request.profile());
        assertEquals(
                request,
                new DataExportPageRequest(
                        template, dates, Optional.empty(), 1, 50, List.of("id asc")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportPageRequest(
                                template, dates, Optional.empty(), 1, 50, List.of()));
        final var mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        final var properties =
                mapper.getSerializationConfig()
                        .introspect(mapper.constructType(DataExportPageRequest.class))
                        .findProperties();
        assertEquals(6, properties.size());
        assertFalse(properties.stream().anyMatch(property -> property.getName().equals("profile")));
        final var body =
                new DataExportRequestJsonSerializer(mapper)
                        .serialize(request, java.time.ZoneId.of("America/Sao_Paulo"));
        assertEquals("id asc", body.path("order_by").asText());
        assertEquals("50", body.path("per").asText());
        assertFalse(body.has("profile"));
        assertEquals(7, DataExportPageRequest.class.getRecordComponents().length);
    }

    @Test
    void legacyConstructionDoesNotLoadObservedResourcesInAnIsolatedLoader() throws Exception {
        final URL[] urls =
                Arrays.stream(
                                System.getProperty(
                                                "surefire.test.class.path",
                                                System.getProperty("java.class.path"))
                                        .split(Pattern.quote(java.io.File.pathSeparator)))
                        .map(Path::of)
                        .map(
                                path -> {
                                    try {
                                        return path.toUri().toURL();
                                    } catch (final java.net.MalformedURLException invalid) {
                                        throw new AssertionError(invalid);
                                    }
                                })
                        .toArray(URL[]::new);
        try (var loader =
                new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
                    @Override
                    public java.io.InputStream getResourceAsStream(final String name) {
                        if (name.contains("observed-p1b")) {
                            throw new AssertionError("LEGACY_LOADED_OBSERVED_RESOURCE");
                        }
                        return super.getResourceAsStream(name);
                    }
                }) {
            final var requestType = loader.loadClass(DataExportPageRequest.class.getName());
            final var templateType = loader.loadClass(DataExportTemplate.class.getName());
            final var dateType = loader.loadClass(BusinessDateRange.class.getName());
            final var template = templateType.getField("COLETAS").get(null);
            final var dates =
                    dateType.getConstructor(LocalDate.class, LocalDate.class)
                            .newInstance(LocalDate.of(2036, 3, 19), LocalDate.of(2036, 3, 19));
            final var request =
                    requestType
                            .getConstructor(
                                    templateType,
                                    dateType,
                                    Optional.class,
                                    int.class,
                                    int.class,
                                    List.class)
                            .newInstance(template, dates, Optional.empty(), 1, 50, null);
            assertEquals(
                    List.of("sequence_code asc"), requestType.getMethod("orderBy").invoke(request));
        }
    }

    @Test
    void threeGatesRejectLegacyUnderObservedGuardBeforeFetching() {
        final var fixture =
                new ObservedFlowTestSupport(DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[]");
        final var legacy =
                new DataExportPageRequest(
                        fixture.request.template(),
                        fixture.request.businessDateWindow(),
                        Optional.empty(),
                        1,
                        50,
                        null);
        final var start =
                br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport.controlPlaneStart(
                        br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding.create(
                                java.util.UUID.randomUUID(),
                                fixture.release,
                                br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport.policy(
                                        fixture.release),
                                new ImmutableFingerprint("synthetic", "a".repeat(64))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportRuntimeWorkload.Input(
                                start.partition(),
                                fixture.guard,
                                legacy,
                                new DataExportExtractionLimits(2, 100, 100),
                                fixture.bundle));
        assertEquals(0, fixture.client.requests.size());
        assertThrows(
                ContractDriftException.class,
                () -> DataExportContractGate.enforce(fixture.bundle, legacy, fixture.guard));
        final var pipelineFixture =
                new ObservedFlowTestSupport(DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[]");
        final var pipeline =
                new DataExportStagingPipeline<Integer>(
                        legacy.template(),
                        new DataExportPageStreamer(
                                request -> {
                                    throw new AssertionError("FETCHED");
                                },
                                DataExportExtractionAudit.noop(),
                                java.time.Clock.systemUTC()),
                        (ordinal, node) -> ordinal,
                        (id, batch, rows, at, token) -> {
                            throw new AssertionError("STAGED");
                        });
        assertThrows(
                ContractDriftException.class,
                () ->
                        pipeline.execute(
                                pipelineFixture.guard,
                                legacy,
                                new DataExportExtractionLimits(2, 100, 100),
                                br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken.none()));
        assertEquals(0, pipelineFixture.client.requests.size());
    }

    private record LegacySix(
            DataExportTemplate template,
            BusinessDateRange businessDateWindow,
            Optional<SourceDateTimeRange> updatedAtWindow,
            int page,
            int pageSize,
            List<String> orderBy) {}

    @Test
    void guardRejectsNewPerOrFilterEvenWhenBothRequestsUseTheSamePinnedRelease() {
        final var fixture =
                new ObservedFlowTestSupport(DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[]");
        final var changedPer =
                DataExportPageRequest.observed(
                        fixture.request.template(),
                        fixture.release,
                        ObservedFlowTestSupport.BASE,
                        ObservedFlowTestSupport.DATES,
                        1,
                        100);
        final var changedDates =
                new BusinessDateRange(LocalDate.of(2036, 3, 18), LocalDate.of(2036, 3, 19));
        final var changedFilter =
                DataExportPageRequest.observed(
                        fixture.request.template(),
                        fixture.release,
                        ObservedFlowTestSupport.BASE,
                        changedDates,
                        1,
                        50);
        assertFalse(changedPer.profile().approves(changedPer, fixture.guard.sourceSelection()));
        assertFalse(
                changedFilter.profile().approves(changedFilter, fixture.guard.sourceSelection()));
        assertTrue(
                fixture.request
                        .profile()
                        .approves(fixture.request, fixture.guard.sourceSelection()));
        assertThrows(
                ContractDriftException.class,
                () -> DataExportContractGate.enforce(fixture.bundle, changedPer, fixture.guard));
        assertEquals(0, fixture.client.requests.size());
    }

    @Test
    void mismatchedMaterializedRuntimeInvalidatesTheGuardBeforeAnyDelegate() {
        final var fixture =
                new ObservedFlowTestSupport(DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[]");
        final var source = fixture.observation;
        final var wrong =
                new DataExportContractObservationConfiguration(
                        source.template(),
                        source.expectedResponseForm(),
                        source.approvedKeyPath(),
                        source.observationLimits(),
                        source.responsePathBoundary(),
                        new ImmutableFingerprint("synthetic-runtime-v1", "f".repeat(64)));
        final var bundle =
                DataExportHttpGatewayBundle.contractBound(
                        request -> {
                            throw new AssertionError("DATA_FETCHED");
                        },
                        template -> {
                            throw new AssertionError("INFO_FETCHED");
                        },
                        wrong);
        assertThrows(
                ContractDriftException.class,
                () -> DataExportContractGate.enforce(bundle, fixture.request, fixture.guard));
        assertThrows(ContractDriftException.class, fixture.guard::sourceSelection);
        assertEquals(0, fixture.client.requests.size());
    }
}
