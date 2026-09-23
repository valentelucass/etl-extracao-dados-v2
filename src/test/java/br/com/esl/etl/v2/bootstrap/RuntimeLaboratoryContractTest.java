package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractValidator;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractAdapter;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseForm;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeLaboratoryContractTest {
    @Test
    void versionedColetaFixtureSuppliesOrderedBusinessFreshnessForTemporalUpdates()
            throws Exception {
        final var mapper = new ObjectMapper();
        final var old =
                mapper.readTree(
                        Path.of("src/test/resources/runtime-laboratory/6908-data.json").toFile());
        final String corrected =
                java.nio.file.Files.readString(
                        Path.of("src/test/resources/runtime-laboratory-v2/6908-data.json"));
        final var release = RuntimeLaboratoryContract.release(DataExportTemplate.COLETAS);
        final var policy =
                ContractCompatibilityPolicy.create(
                        "bloco54-compatible-v1", release.contractFingerprint(), List.of());
        final var adapter =
                new DataExportContractAdapter(
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(release, policy));
        for (final String date : List.of("2024-02-28", "2024-02-29", "2024-03-01")) {
            final var page = mapper.readTree(corrected.replace("2024-01-01", date));
            new ContractValidator(release, policy)
                    .validateResponse(
                            adapter.response(
                                    page, DataExportResponseForm.ENVELOPE_DATA_ARRAY, "/id"));
            for (int i = 0; i < 2; i++) {
                final var previous =
                        new ColetaDataExportRecordMapper().map(i + 1, old.get("data").get(i));
                final var row =
                        new ColetaDataExportRecordMapper().map(i + 1, page.get("data").get(i));
                assertNull(previous.freshnessAtUtc());
                assertNull(row.quarantineReasonCode());
                assertEquals(java.time.Instant.parse(date + "T03:00:00Z"), row.freshnessAtUtc());
            }
        }
    }

    @Test
    void refusesEmptyDuplicateOversizedMissingAndEmptyDuePlansBeforeClients(
            @org.junit.jupiter.api.io.TempDir final Path directory) throws Exception {
        final var file = directory.resolve("temporal.json");
        for (final String content :
                List.of(
                        "",
                        "[]",
                        "{}{}",
                        "{\"version\":\"a\",\"version\":\"b\"}",
                        " ".repeat(16385))) {
            java.nio.file.Files.writeString(file, content);
            assertThrows(IllegalArgumentException.class, () -> RuntimeTemporalOperation.read(file));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeTemporalOperation.read(directory.resolve("missing.json")));
        final var root =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        new ObjectMapper()
                                .readTree(
                                        Path.of("config/laboratory/bloco54-temporal-coletas.json")
                                                .toFile());
        root.put("observedAt", "2024-02-28T03:00:00Z");
        final var operation = new RuntimeTemporalOperation(root);
        assertEquals(0, operation.result.windows().size());
        assertThrows(IllegalArgumentException.class, () -> operation.persist(null));
        root.put("blackouts", "invalid");
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTemporalOperation(root));
        root.put("blackouts", "2024-02-01/2024-02-02,".repeat(65));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTemporalOperation(root));
    }

    @Test
    void freezesTemporalWindowsWithStableOccurrencesAndFreshInvocationReceipts() throws Exception {
        final var mapper = new ObjectMapper();
        final var document =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.readTree(
                                Path.of("config/laboratory/bloco54-temporal-coletas.json")
                                        .toFile());
        final var configuration =
                new br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory()
                        .load(
                                Path.of("config/application.example.properties"),
                                new java.util.Properties(),
                                java.util.Map.of(),
                                java.time.Clock.systemUTC());
        final var first = new RuntimeTemporalOperation(document);
        final var window = first.result.windows().get(0);
        final var frozen = first.freeze(configuration, window);
        final var material =
                mapper.readTree(
                        br.com.esl.etl.v2.plataforma.autorizacao.RuntimeScopeTestInspection
                                .material(frozen.scope()));
        assertEquals(22, material.size());
        assertEquals("LOCAL_V2", material.get("source").textValue());
        assertEquals("RUN", material.get("action").textValue());
        assertEquals(window.partitionStart().toString(), material.get("start").textValue());
        assertEquals(frozen.cycle().toString(), material.get("cycle").textValue());
        assertEquals(first.policy.fingerprint().sha256(), material.get("contractHash").textValue());
        document.put("invocation", java.util.UUID.randomUUID().toString());
        final var next = new RuntimeTemporalOperation(document).freeze(configuration, window);
        assertEquals(frozen.scope().executionId(), next.scope().executionId());
        assertEquals(frozen.cycle(), next.cycle());
        assertNotEquals(frozen.scope().invocationId(), next.scope().invocationId());
        document.put("workload", "fretes").put("dependsOn", "coletas");
        final var freight = new RuntimeTemporalOperation(document).freeze(configuration, window);
        assertNotEquals(frozen.namespace(), freight.namespace());
        assertNotEquals(frozen.scope().executionId(), freight.scope().executionId());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        first.freeze(
                                configuration,
                                new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner
                                        .Window(
                                        window.partitionStart().minusSeconds(86400),
                                        window.partitionStart(),
                                        window.extractionStart().minusSeconds(86400),
                                        window.dueAt(),
                                        window.deadlineAt())));
    }

    @Test
    void laboratoryActivationRejectsExpiredRemoteAndExcessiveProfilesWithoutOpeningClients() {
        final var values = new java.util.HashMap<String, String>();
        values.put("V2_DATAEXPORT_ENABLED", "true");
        values.put("V2_DATAEXPORT_BASE_URL", "http://127.0.0.1:19540");
        values.put("V2_DATAEXPORT_SOURCE_INSTANCE", "LOCAL_V2");
        values.put("V2_DATAEXPORT_TENANT_SCOPE", "LOCAL_V2");
        values.put("V2_DATAEXPORT_TIMEZONE", "America/Sao_Paulo");
        values.put("V2_DATAEXPORT_TRANSPORT", "GET_WITH_QUERY");
        values.put("V2_DATAEXPORT_TIMEOUT_SECONDS", "30");
        values.put("V2_DATAEXPORT_RETRY_INITIAL_DELAY_MS", "0");
        values.put("V2_DATAEXPORT_RETRY_MAX_DELAY_MS", "0");
        values.put("V2_DATAEXPORT_RESILIENCE_MINIMUM_REQUEST_INTERVAL_MS", "0");
        values.put("V2_DATAEXPORT_RESILIENCE_MAX_IN_FLIGHT", "1");
        values.put("V2_DATAEXPORT_RESILIENCE_MAX_RETRY_AFTER_MS", "0");
        values.put("V2_DATAEXPORT_RESILIENCE_CIRCUIT_FAILURE_THRESHOLD", "3");
        values.put("V2_DATAEXPORT_RESILIENCE_CIRCUIT_COOLDOWN_SECONDS", "1");
        values.put("V2_DATAEXPORT_MAX_RESPONSE_BYTES", "1048576");
        values.put("V2_DATAEXPORT_RETRY_MAX_ATTEMPTS", "1");
        values.put("V2_DATAEXPORT_RESILIENCE_MAX_REQUESTS_PER_CYCLE", "5");
        values.put("V2_DATAEXPORT_RESILIENCE_MAX_REQUESTS_PER_WORKLOAD", "5");
        values.put("V2_DATAEXPORT_RESILIENCE_MAX_REPARTITIONS", "0");
        values.put("V2_DATAEXPORT_RESILIENCE_STEP_TIMEOUT_SECONDS", "30");
        values.put("V2_DATAEXPORT_RESILIENCE_CYCLE_TIMEOUT_SECONDS", "60");
        final var clock =
                java.time.Clock.fixed(
                        java.time.Instant.parse("2026-09-08T00:00:00Z"), java.time.ZoneOffset.UTC);
        final var factory =
                new br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory();
        final var configuration =
                factory.load(
                        Path.of("config/application.example.properties"),
                        new java.util.Properties(),
                        values,
                        clock);
        RuntimeLaboratoryContract.validate(configuration, clock.instant().plusSeconds(60));
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeLaboratoryContract.validate(configuration, clock.instant()));
        for (final var variant :
                java.util.Map.of(
                                "V2_DATAEXPORT_BASE_URL", "https://source.example.test",
                                "V2_DATAEXPORT_SOURCE_INSTANCE", "another-source",
                                "V2_DATAEXPORT_TENANT_SCOPE", "another-tenant",
                                "V2_DATAEXPORT_MAX_RESPONSE_BYTES", "1048577",
                                "V2_DATAEXPORT_RETRY_MAX_ATTEMPTS", "2",
                                "V2_DATAEXPORT_RESILIENCE_MAX_REQUESTS_PER_CYCLE", "6",
                                "V2_DATAEXPORT_RESILIENCE_MAX_REPARTITIONS", "1",
                                "V2_DATAEXPORT_RESILIENCE_CYCLE_TIMEOUT_SECONDS", "61")
                        .entrySet()) {
            final var changed = new java.util.HashMap<>(values);
            changed.put(variant.getKey(), variant.getValue());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            RuntimeLaboratoryContract.validate(
                                    factory.load(
                                            Path.of("config/application.example.properties"),
                                            new java.util.Properties(),
                                            changed,
                                            clock),
                                    clock.instant().plusSeconds(60)));
        }
    }

    @Test
    void attemptInstrumentationCountsFailuresAsAttemptsAndRefusesExcessBeforeSending() {
        final var attempts = new RuntimeHttpAttempts(2);
        attempts.beforeAttempt(
                new br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpAttempt(
                        6908, "info"));
        attempts.beforeAttempt(
                new br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpAttempt(
                        6908, "data-GET_WITH_QUERY"));
        assertEquals("RUNTIME_HTTP_ATTEMPTS metadata=1 data=1 total=2", attempts.summary());
        assertThrows(
                IllegalStateException.class,
                () ->
                        attempts.beforeAttempt(
                                new br.com.esl.etl.v2.plataforma.fonte.dataexport
                                        .DataExportHttpAttempt(6908, "info")));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeHttpAttempts(0));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeHttpAttempts(100001));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeHttpAttempts(2)
                                .beforeAttempt(
                                        new br.com.esl.etl.v2.plataforma.fonte.dataexport
                                                .DataExportHttpAttempt(6908, "unknown")));
    }

    @Test
    void bothExactLaboratoryShapesCrossRealContractAndRealMappers() throws Exception {
        for (final var template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            final var release = RuntimeLaboratoryContract.release(template);
            assertNotEquals(
                    DataExportFirstWaveContractCatalog.release(template).contractFingerprint(),
                    release.contractFingerprint());
            final var policy =
                    ContractCompatibilityPolicy.create(
                            "bloco54-compatible-v1", release.contractFingerprint(), List.of());
            final var adapter =
                    new DataExportContractAdapter(
                            ContractObservationLimits.runtimeDefaults(),
                            ContractResponsePathBoundary.forRuntime(release, policy));
            final String number = template == DataExportTemplate.COLETAS ? "6908" : "6389";
            final var root =
                    new ObjectMapper()
                            .readTree(
                                    Path.of(
                                                    "src/test/resources/runtime-laboratory/"
                                                            + number
                                                            + "-data.json")
                                            .toFile());
            new ContractValidator(release, policy)
                    .validateResponse(
                            adapter.response(
                                    root, DataExportResponseForm.ENVELOPE_DATA_ARRAY, "/id"));
            int ordinal = 0;
            for (final var record : root.get("data")) {
                ordinal++;
                if (template == DataExportTemplate.COLETAS) {
                    assertNull(
                            new ColetaDataExportRecordMapper()
                                    .map(ordinal, record)
                                    .quarantineReasonCode());
                } else {
                    assertFalse(
                            new FreteDataExportRecordMapper().map(ordinal, record).quarantined());
                }
            }
            assertEquals(2, ordinal);
            ((com.fasterxml.jackson.databind.node.ObjectNode) root.get("data").get(0))
                    .put("undeclared_shape", "rejected");
            assertThrows(
                    RuntimeException.class,
                    () ->
                            adapter.response(
                                    root, DataExportResponseForm.ENVELOPE_DATA_ARRAY, "/id"));
        }
    }

    @Test
    void temporalDocumentBoundsBlackoutAndLeapDayAreConsumedOffline() throws Exception {
        final var file = Path.of("config/laboratory/bloco54-temporal-coletas.json");
        final var operation = RuntimeTemporalOperation.read(file);
        assertEquals(3, operation.result.windows().size());
        assertEquals(
                "2024-02-29T03:00:00Z",
                operation.result.windows().get(1).partitionStart().toString());
        final var root =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        new ObjectMapper().readTree(file.toFile());
        root.put("blackouts", "2024-02-29/2024-03-01");
        assertEquals(1, new RuntimeTemporalOperation(root).result.windows().size());
        root.put("maximumBacklog", "5");
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTemporalOperation(root));
        root.put("maximumBacklog", "4").put("extra", "denied");
        assertThrows(IllegalArgumentException.class, () -> new RuntimeTemporalOperation(root));
    }
}
