package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalizadorObservedNullsP2Test {
    private static final List<String> NULL8 =
            List.of(
                    "fit_dyn_drt_nickname",
                    "fit_dyn_name",
                    "fit_fln_cln_nickname",
                    "fit_fln_status",
                    "fit_fte_lce_occurrence_at",
                    "fit_fte_lce_ore_description",
                    "fit_o_n_drt_nickname",
                    "fit_o_n_name");

    @TempDir Path directory;

    @Test
    void newResourceChangesOnlyEightNullableFlagsAndPreservesAllOtherRulesAndMetadata()
            throws Exception {
        final var archived = resource(LocalizadorObservedWire26.RESOURCE);
        final var current = resource(LocalizadorObservedWire26.OBSERVED_NULL8_P2_RESOURCE);
        assertEquals(archived.required("metadata"), current.required("metadata"));
        final var oldShape = LocalizadorObservedWire26.shape();
        final var newShape = LocalizadorObservedWire26.observed20261108NullsP2();
        assertEquals(oldShape.fields(), newShape.fields());
        assertEquals(oldShape.filters(), newShape.filters());
        assertEquals(oldShape.paths().keySet(), newShape.paths().keySet());
        assertEquals(27, newShape.paths().size());
        int changed = 0;
        for (final var item : oldShape.paths().entrySet()) {
            final var actual = (ObjectNode) newShape.paths().get(item.getKey()).deepCopy();
            final boolean newlyNullable = NULL8.contains(item.getKey().substring(1));
            assertEquals(
                    newlyNullable || item.getValue().path("nullable").booleanValue(),
                    actual.path("nullable").booleanValue());
            changed += newlyNullable ? 1 : 0;
            actual.set("nullable", item.getValue().required("nullable"));
            assertEquals(item.getValue(), actual);
        }
        assertEquals(8, changed);
        assertEquals(
                "2026-10-24.p1b-localizacao-cargas-observed.v1",
                archived.path("contractVersion").textValue());
        assertFalse(current.has("fingerprints"));
        assertEquals(
                archived.required("fingerprints"),
                current.required("parentContract").required("historicalFingerprints"));
    }

    @Test
    void eachObservedNullPassesOnlyTheNewWireFactoryAndTheAlreadyExistingStringMapper()
            throws Exception {
        final var oldShape = LocalizadorObservedWire26.shape();
        final var newShape = LocalizadorObservedWire26.observed20261108NullsP2();
        final var mapper = LocalizacaoCargaDataExportRecordMapper.observed20261024();
        for (final String field : NULL8) {
            final var row = LocalizadorCaptureFixture.row(1).putNull(field);
            assertFalse(LocalizadorObservedWire26.qualified(row, oldShape, new TreeMap<>()));
            assertTrue(LocalizadorObservedWire26.qualified(row, newShape, new TreeMap<>()));
            final var mapped = mapper.map(1, row.toString());
            assertFalse(mapped.quarantined());
            final var presence = ExportValidationSupport.JSON.readTree(mapped.fieldPresenceJson());
            final var evidence = presence.required("fields").required(field);
            assertEquals("NULL", evidence.path("presence").textValue());
            assertEquals("EXPLICIT_NULL", evidence.path("parseState").textValue());
            assertTrue(
                    ExportValidationSupport.JSON
                            .readTree(mapped.payloadJson())
                            .required(field)
                            .isNull());
        }
    }

    @Test
    void newlyNullableFieldsDoNotAdmitUnobservedBooleanNumberArrayOrObjectTypes() throws Exception {
        final var shape = LocalizadorObservedWire26.observed20261108NullsP2();
        for (final String field : NULL8) {
            for (final String token : List.of("true", "42", "[]", "{}")) {
                final var row = LocalizadorCaptureFixture.row(1);
                row.set(field, ExportValidationSupport.JSON.readTree(token));
                final var reasons = new TreeMap<String, Long>();
                assertFalse(LocalizadorObservedWire26.qualified(row, shape, reasons));
                assertEquals(1, reasons.size());
                assertTrue(reasons.firstKey().startsWith("/" + field + ":"));
            }
        }
    }

    @Test
    void everyOtherHistoricallyNonNullableFieldStillRefusesNull() throws Exception {
        final var oldShape = LocalizadorObservedWire26.shape();
        final var newShape = LocalizadorObservedWire26.observed20261108NullsP2();
        int checked = 0;
        for (final String field : oldShape.fields()) {
            if (!NULL8.contains(field)
                    && !oldShape.paths().get("/" + field).path("nullable").booleanValue()) {
                assertFalse(
                        LocalizadorObservedWire26.qualified(
                                LocalizadorCaptureFixture.row(1).putNull(field),
                                newShape,
                                new TreeMap<>()));
                checked++;
            }
        }
        assertEquals(16, checked);
    }

    @Test
    void eightFieldsRemainRequiredAndAbsentIsNeverRewrittenToNull() throws Exception {
        final var shape = LocalizadorObservedWire26.observed20261108NullsP2();
        for (final String field : NULL8) {
            final var row = LocalizadorCaptureFixture.row(1);
            row.remove(field);
            final var reasons = new TreeMap<String, Long>();
            assertFalse(LocalizadorObservedWire26.qualified(row, shape, reasons));
            assertEquals(Set.of("/" + field + ":ABSENT"), reasons.keySet());
        }
    }

    @Test
    void completeCaptureAndCurrentQualifierUseTheNewPinAndPreserveBothLiteralOccurrences()
            throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var row = allNulls();
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array(row, row)));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var captured = fixture.capture(2, 100, 10);
            assertEquals("LOCALIZADOR_SOURCE_COMPLETE", captured.path("status").textValue());
            assertTrue(captured.path("rowTypesQualified").booleanValue());
            assertTrue(captured.path("mapperQualified").booleanValue());
            assertEquals(0, captured.path("rowTypeRejectedOccurrences").longValue());
            assertEquals(0, captured.path("mapperRejectedOccurrences").longValue());
            assertEquals(2, captured.path("rows").longValue());
            assertEquals(
                    LocalizadorObservedWire26.OBSERVED_NULL8_P2_SHA256,
                    captured.path("rowTypesContractSha256").textValue());
            final var source = ExportValidationSupport.Pin.from(captured.required("dataPin"));
            final byte[] before = Files.readAllBytes(source.path());
            final var qualified =
                    LocalizadorRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), source, budget());
            assertTrue(qualified.path("success").booleanValue());
            assertEquals(2, qualified.path("currentMapperInvocations").longValue());
            assertEquals(
                    LocalizadorObservedWire26.OBSERVED_NULL8_P2_SHA256,
                    qualified.path("sourceWireContractSha256").textValue());
            assertEquals(
                    2,
                    LocalizadorRawMapperQualifier.verifyOriginalResponses(
                            captured.required("responses"), source, new ArrayList<>(), budget()));
            assertArrayEquals(before, Files.readAllBytes(source.path()));
            for (final String line : Files.readAllLines(source.path())) {
                for (final String field : NULL8) {
                    assertTrue(line.contains("\"" + field + "\":null"));
                }
            }
        }
    }

    @Test
    void unobservedNonNullWireTypeStillFailsEvenWhenTheExistingMapperPreservesIt()
            throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var row = allNulls().put("fit_dyn_name", 42);
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array(row)));
            fixture.port.pages.add(
                    LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
            final var captured = fixture.capture(2, 100, 10);
            assertEquals("LOCALIZADOR_SOURCE_COMPLETE", captured.path("status").textValue());
            assertFalse(captured.path("rowTypesQualified").booleanValue());
            assertTrue(captured.path("mapperQualified").booleanValue());
            final var source = ExportValidationSupport.Pin.from(captured.required("dataPin"));
            final var qualified =
                    LocalizadorRawMapperQualifier.qualify(
                            fixture.pin(fixture.request), source, budget());
            assertFalse(qualified.path("success").booleanValue());
            assertFalse(qualified.path("rowTypesQualified").booleanValue());
            assertTrue(qualified.path("mapperQualified").booleanValue());
            assertEquals(1, qualified.path("rowTypeRejectedRows").longValue());
            assertTrue(Files.readString(source.path()).contains("\"fit_dyn_name\":42"));
        }
    }

    @Test
    void defaultSeventeenAndObservedTwentySixMapperFactoriesKeepTheirHistoricalMatrices()
            throws Exception {
        final var row = allNulls();
        final var observed =
                LocalizacaoCargaDataExportRecordMapper.observed20261024().map(1, row.toString());
        assertFalse(observed.quarantined());
        assertEquals(
                "LOCALIZACAO_8656_26_PATHS_OBSERVED_20261024",
                ExportValidationSupport.JSON
                        .readTree(observed.fieldPresenceJson())
                        .required("policy")
                        .path("matrix")
                        .textValue());
        final var defaultMapper = new LocalizacaoCargaDataExportRecordMapper();
        assertEquals(
                "UNAPPROVED_SOURCE_PATH",
                defaultMapper.map(1, row.toString()).quarantineReasonCode());
        for (final String field : List.copyOf(LocalizadorObservedWire26.shape().fields())) {
            if (!LocalizacaoCargaDataExportRecordMapper.ACCEPTED_FIELDS.contains(field)) {
                row.remove(field);
            }
        }
        final var base = defaultMapper.map(1, row.toString());
        assertFalse(base.quarantined());
        assertEquals(
                "LOCALIZACAO_8656_17_PATHS_V1",
                ExportValidationSupport.JSON
                        .readTree(base.fieldPresenceJson())
                        .required("policy")
                        .path("matrix")
                        .textValue());
    }

    private static ObjectNode allNulls() throws Exception {
        final var row = LocalizadorCaptureFixture.row(1);
        NULL8.forEach(row::putNull);
        return row;
    }

    private static JsonNode resource(final String name) throws Exception {
        try (var stream = LocalizadorObservedNullsP2Test.class.getResourceAsStream("/" + name)) {
            assertNotNull(stream);
            return LocalizadorObservedWire26.strict(stream.readNBytes(65537));
        }
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(80));
    }
}
