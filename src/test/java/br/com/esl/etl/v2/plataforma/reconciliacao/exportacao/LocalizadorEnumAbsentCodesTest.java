package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalizadorEnumAbsentCodesTest {
    private static final String CODE = "authored_absent";
    @TempDir Path directory;

    @Test
    void legacyLabelsStayUnchangedAndAbsentCodeRequiresAnExplicitDeclaration() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.catalog.putObject("enumAbsentCodes");
        assertTrue(fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
        fixture.records.get(0).put("fit_fln_status", CODE);
        fixture.nativeRows.get(0).remove(10);
        assertThrows(IllegalArgumentException.class, fixture::compare);
    }

    @Test
    void explicitAbsenceKeepsRawCodeFullRowsAndDuplicateMultiplicity() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        fixture.records.get(0).put("fit_fln_status", CODE);
        fixture.nativeRows.get(0).remove(10);
        final byte[] original = fixture.sourceBytes();
        codes(fixture.catalog, "fit_fln_status").add(CODE);
        fixture.records.add(fixture.records.get(0).deepCopy());
        fixture.nativeRows.add(new LinkedHashMap<>(fixture.nativeRows.get(0)));
        final var result = fixture.compare();
        assertTrue(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(2, result.path("sourceRows").intValue());
        assertEquals(26, result.path("comparison").path("columnsEqual").size());
        assertEquals(4, result.path("comparison").path("totalsEqual").size());
        final var raw = raw(fixture.records.get(0).required("fit_fln_status"));
        assertEquals(
                new ExactExportTable.Atom("TEXT", CODE), LocalizadorContentAdapter.original(raw));
        assertEquals(
                new ExactExportTable.Atom("ABSENT", ""),
                LocalizadorContentAdapter.sourceValue(fixture.field(10), raw, fixture.catalog));
        fixture.records.remove(1);
        assertArrayEquals(original, fixture.sourceBytes());
        fixture.records.add(fixture.records.get(0).deepCopy());
        fixture.nativeRows.remove(1);
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void nullEmptyWrongTypeAndNativeBlankDoNotBecomeAnAbsentEnumCode() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        codes(fixture.catalog, "fit_fln_status").add(CODE);
        LocalizadorContentAdapter.verifyCatalog(fixture.catalog);
        final var factory = ExportValidationSupport.JSON.getNodeFactory();
        assertEquals(
                new ExactExportTable.Atom("NULL", ""),
                LocalizadorContentAdapter.sourceValue(
                        fixture.field(10), raw(factory.nullNode()), fixture.catalog));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LocalizadorContentAdapter.sourceValue(
                                fixture.field(10), raw(factory.textNode("")), fixture.catalog));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LocalizadorContentAdapter.sourceValue(
                                fixture.field(10),
                                raw(factory.booleanNode(false)),
                                fixture.catalog));
        final var absent = new ExactExportTable.Atom("ABSENT", "");
        assertEquals(
                absent,
                LocalizadorContentAdapter.nativeValue(fixture.field(10), absent, fixture.catalog));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LocalizadorContentAdapter.nativeValue(
                                fixture.field(10),
                                new ExactExportTable.Atom("BLANK", ""),
                                fixture.catalog));
    }

    @Test
    void absentCodesMustBeUniqueAndDisjointFromExistingLabelKeys() {
        final var duplicate = LocalizadorContentFixture.authoredCatalog();
        codes(duplicate, "fit_fln_status").add(CODE).add(CODE);
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizadorContentAdapter.verifyCatalog(duplicate));
        final var overlap = LocalizadorContentFixture.authoredCatalog();
        codes(overlap, "fit_fln_status").add("authored_pending");
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizadorContentAdapter.verifyCatalog(overlap));
    }

    @Test
    void absentObjectAndOnlyThreeEnumFieldsAreStrictlyScopedAtCatalogTopLevel() {
        for (final String field : List.of("fit_crn_psn_nickname", "invented", "TYPE")) {
            final var catalog = LocalizadorContentFixture.authoredCatalog();
            codes(catalog, field).add(CODE);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LocalizadorContentAdapter.verifyCatalog(catalog));
        }
        final var misplaced = LocalizadorContentFixture.authoredCatalog();
        ((ObjectNode) misplaced.required("columns").get(9)).putArray("enumAbsentCodes").add(CODE);
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizadorContentAdapter.verifyCatalog(misplaced));
        for (final JsonNode wrong :
                List.of(
                        ExportValidationSupport.JSON.getNodeFactory().nullNode(),
                        ExportValidationSupport.JSON.getNodeFactory().textNode("ABSENT"),
                        ExportValidationSupport.JSON.createArrayNode())) {
            final var catalog = LocalizadorContentFixture.authoredCatalog();
            catalog.set("enumAbsentCodes", wrong);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LocalizadorContentAdapter.verifyCatalog(catalog));
        }
    }

    @Test
    void codeCountsAndUtf16LengthsKeepTheirExact128Bounds() {
        final var boundary = LocalizadorContentFixture.authoredCatalog();
        final var values = codes(boundary, "fit_fln_status");
        for (int number = 0; number < 128; number++) {
            values.add("authored_" + number);
        }
        LocalizadorContentAdapter.verifyCatalog(boundary);
        values.add("authored_128");
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizadorContentAdapter.verifyCatalog(boundary));
        final var unicode = LocalizadorContentFixture.authoredCatalog();
        codes(unicode, "fit_fln_status").add("\ud83d\ude00".repeat(64));
        LocalizadorContentAdapter.verifyCatalog(unicode);
        codes(unicode, "fit_fln_status").add("\ud83d\ude00".repeat(65));
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizadorContentAdapter.verifyCatalog(unicode));
        for (final String invalid : List.of("", "a".repeat(129))) {
            final var catalog = LocalizadorContentFixture.authoredCatalog();
            codes(catalog, "fit_fln_status").add(invalid);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LocalizadorContentAdapter.verifyCatalog(catalog));
        }
        final var wrongType = LocalizadorContentFixture.authoredCatalog();
        codes(wrongType, "fit_fln_status").add(1);
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizadorContentAdapter.verifyCatalog(wrongType));
    }

    @Test
    void exactCodeDoesNotTrimFoldCaseOrAuthorizeOtherEnumsOrNativeText() throws Exception {
        final var fixture = new LocalizadorContentFixture(directory, 1);
        codes(fixture.catalog, "fit_fln_status").add(CODE);
        LocalizadorContentAdapter.verifyCatalog(fixture.catalog);
        for (final String unobserved : List.of("AUTHORED_ABSENT", " " + CODE, CODE + " ")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            LocalizadorContentAdapter.sourceValue(
                                    fixture.field(10),
                                    raw(
                                            ExportValidationSupport.JSON
                                                    .getNodeFactory()
                                                    .textNode(unobserved)),
                                    fixture.catalog));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LocalizadorContentAdapter.sourceValue(
                                fixture.field(4),
                                raw(ExportValidationSupport.JSON.getNodeFactory().textNode(CODE)),
                                fixture.catalog));
        fixture.records.get(0).put("fit_fln_status", CODE);
        assertFalse(
                fixture.compare().path("contentEqualUnderCandidatePresentation").booleanValue());
    }

    @Test
    void eachOfTheThreeEnumsCanDeclareAbsenceWithoutChangingStringLabelsOfTheOthers()
            throws Exception {
        for (final int column : List.of(4, 10, 21)) {
            final var fixture = new LocalizadorContentFixture(directory, 1);
            final String field = fixture.field(column).required("sourceField").textValue();
            codes(fixture.catalog, field).add(CODE);
            fixture.records.get(0).put(field, CODE);
            fixture.nativeRows.get(0).remove(column);
            assertTrue(
                    fixture.compare()
                            .path("contentEqualUnderCandidatePresentation")
                            .booleanValue());
        }
    }

    private static ArrayNode codes(final ObjectNode catalog, final String field) {
        final ObjectNode root =
                catalog.has("enumAbsentCodes")
                        ? (ObjectNode) catalog.get("enumAbsentCodes")
                        : catalog.putObject("enumAbsentCodes");
        return root.putArray(field);
    }

    private static LocalizadorContentAdapter.Raw raw(final JsonNode value) {
        return new LocalizadorContentAdapter.Raw(value, null);
    }
}
