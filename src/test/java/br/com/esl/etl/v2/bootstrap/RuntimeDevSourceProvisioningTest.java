package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Negative source-boundary controls only; no fixture proves source or Root admission. */
class RuntimeDevSourceProvisioningTest {
    private static final Path CURRENT_UNIT =
            Path.of(
                    "C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/"
                            + "execucao-esl-shadow-20261008-164952/hermesf4eda62e83d1");

    @Test
    void actualFontesHoldIsReadBeforeTokenFileOrRealFactoryConstruction() throws Exception {
        final var bytes = new ByteArrayOutputStream();
        try (var log = new PrintStream(bytes, true, StandardCharsets.UTF_8);
                var facts =
                        new RuntimeDevSourceEvidenceInspection(
                                CURRENT_UNIT, DataExportTemplate.COLETAS, log);
                var provisioning = new RuntimeDevSourceProvisioning(facts, true)) {
            final var tokenFailure =
                    assertThrows(
                            IllegalStateException.class,
                            () -> provisioning.require(SecretKey.DATA_EXPORT_TOKEN));
            assertEquals("DEV_SOURCE_ADMISSION_BEFORE_PROVISIONING", tokenFailure.getMessage());
            assertEquals(
                    RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA,
                    ((RuntimeDevEngineBankBinding.Refusal) tokenFailure.getCause()).gate());
            assertEquals(
                    "STOP429_SAME_CURRENT_OPERATIONAL_SCOPE_NOT_PROVED", facts.gateDetailCode());
            assertThrows(
                    IllegalStateException.class,
                    () -> provisioning.gateways(attempt -> {}).create(null, null, null, null));
            assertTrue(
                    bytes.toString(StandardCharsets.UTF_8)
                            .contains("STOP429_SAME_CURRENT_OPERATIONAL_SCOPE_NOT_PROVED"));
            assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("API_DATAEXPORT_TOKEN="));
        }
    }

    @Test
    void curatedProvisioningIsDisabledByDefaultAndOtherSecretKeysRefuse() throws Exception {
        try (var facts =
                        new RuntimeDevSourceEvidenceInspection(
                                CURRENT_UNIT, DataExportTemplate.COLETAS, System.err);
                var provisioning = new RuntimeDevSourceProvisioning(facts, false)) {
            assertFalse(provisioning.enabled());
            assertThrows(
                    IllegalStateException.class,
                    () -> provisioning.require(SecretKey.DATA_EXPORT_TOKEN));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> provisioning.require(SecretKey.SHADOW_SAMPLE_SA_PASSWORD));
        }
    }

    @Test
    void fileSelectionUsesLastNonemptyDeclaredLegacyKeysAndNoFallback() throws Exception {
        final var selected =
                RuntimeDevSourceProvisioning.select(
                        ("\uFEFFAPI_BASE_URL=https://first.invalid\n"
                                        + "API_DATAEXPORT_TOKEN=fixture-first\n"
                                        + "export API_BASE_URL='https://last.invalid'\n"
                                        + "API_DATAEXPORT_TOKEN=fixture-last\n"
                                        + "API_DATAEXPORT_TOKEN=\nV2_DATAEXPORT_TOKEN=ignored\n")
                                .getBytes(StandardCharsets.UTF_8));
        assertEquals("https://last.invalid", selected.get("API_BASE_URL"));
        assertEquals("fixture-last", selected.get("API_DATAEXPORT_TOKEN"));
        assertEquals(2, selected.size());
        final var priority =
                RuntimeDevSourceProvisioning.select(
                        ("API_BASEURL=https://priority.invalid\n"
                                        + "API_BASEURL=\n"
                                        + "API_BASE_URL=https://lower-priority.invalid\n"
                                        + "API_DATAEXPORT_TOKEN=fixture\n")
                                .getBytes(StandardCharsets.UTF_8));
        assertEquals("https://priority.invalid", priority.get("API_BASE_URL"));
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevSourceProvisioning.select(
                                "API_BASE_URL=\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                IOException.class, () -> RuntimeDevSourceProvisioning.select(new byte[65_537]));
        assertThrows(
                IOException.class,
                () -> RuntimeDevSourceProvisioning.select(new byte[] {(byte) 0xff}));
    }

    @Test
    void heldDiagnosisCannotBeConvertedToPermissionByChangingAFlag() throws Exception {
        final var facts =
                new ObjectMapper()
                        .readTree(
                                java.nio.file.Files.readAllBytes(
                                        CURRENT_UNIT.resolve(
                                                "fontes01/minimal-causal-rule02.json")));
        RuntimeDevSourceEvidenceInspection.inspectHeldFacts(facts);
        ((com.fasterxml.jackson.databind.node.ObjectNode) facts.path("currentDisposition"))
                .put("sourceReleased", true);
        assertThrows(
                IOException.class,
                () -> RuntimeDevSourceEvidenceInspection.inspectHeldFacts(facts));
    }

    @Test
    void closedSourceInspectionNeverBecomesPhysicalAdmission() throws Exception {
        final var facts =
                new RuntimeDevSourceEvidenceInspection(
                        CURRENT_UNIT, DataExportTemplate.COLETAS, System.err);
        assertThrows(SQLException.class, facts::open);
        facts.close();
        assertThrows(RuntimeDevEngineBankBinding.Refusal.class, facts::verify);
    }

    @Test
    void currentInspectionDoesNotImportThe6908HoldIntoAnotherTemplate() throws Exception {
        try (var facts =
                new RuntimeDevSourceEvidenceInspection(
                        CURRENT_UNIT, DataExportTemplate.FRETES, System.err)) {
            final var refused =
                    assertThrows(RuntimeDevEngineBankBinding.Refusal.class, facts::verify);
            assertEquals(RuntimeDevEngineBankBinding.MissingGate.ADMISSION, refused.gate());
            assertEquals(null, facts.gateDetailCode());
        }
    }

    @Test
    void documentedIntervalAndNoRetryAreCheckedOnNonsecretSettings() {
        final var settings =
                new DataExportClientSettings(
                        URI.create("https://fixture.invalid"),
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(30),
                        DataExportTransport.GET_WITH_QUERY,
                        new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                        10485760);
        final Function<Long, DataExportSourceConfiguration> source =
                milliseconds ->
                        new DataExportSourceConfiguration(
                                "RootPartition",
                                "RootPartition",
                                settings,
                                new EslResiliencePolicy(
                                        Duration.ofMillis(milliseconds),
                                        1,
                                        2,
                                        2,
                                        Duration.ofSeconds(30),
                                        Duration.ofSeconds(60),
                                        Duration.ofSeconds(60),
                                        Duration.ZERO,
                                        0,
                                        1,
                                        Duration.ofSeconds(1)));
        assertThrows(
                IllegalStateException.class,
                () -> RuntimeDevSourceProvisioning.verifyMetadata(source.apply(1999L)));
        RuntimeDevSourceProvisioning.verifyMetadata(source.apply(2000L));
    }
}
