package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class DataExportObservedPageTest {
    static DataExportObservationRequest request(final DataExportTemplate template) {
        final var day = LocalDate.of(2026, 10, 1);
        return new DataExportObservationRequest(
                new DataExportPageRequest(
                        template,
                        new BusinessDateRange(day, day),
                        Optional.empty(),
                        1,
                        1,
                        template.defaultOrderBy()));
    }

    static JsonNode json(final String text) throws Exception {
        return DataExportStrictJsonParser.readTree(text);
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "CONTAS_A_PAGAR"},
            mode = EnumSource.Mode.EXCLUDE)
    void sevenRootCandidatesAdmitExpandedRowsWithoutCanonicalClaims(
            final DataExportTemplate template) throws Exception {
        final var request = request(template);
        final var row = json("{\"" + request.paginationRoot() + "\":\"fixture-root\"}");
        final var page = new DataExportObservedPage(request, List.of(row, row), 100, 200, 200);
        assertEquals(2, page.physicalRows());
        assertEquals(1, page.distinctRoots());
        assertFalse(page.canonicalIdentityProven());
        assertFalse(page.sourceCompletenessProven());
        assertFalse(page.toString().contains("fixture-root"));
        ((ObjectNode) row).put(request.paginationRoot(), "changed");
        assertEquals("fixture-root", page.records().get(0).path(request.paginationRoot()).asText());
        ((ObjectNode) page.records().get(0)).put(request.paginationRoot(), "changed");
        assertEquals("fixture-root", page.records().get(0).path(request.paginationRoot()).asText());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"id\":null}",
                "{\"id\":{}}",
                "{\"id\":[]}",
                "{\"id\":true}",
                "{\"id\":1.5}",
                "{\"id\":\" \"}"
            })
    void absenceOrNonIdentifierRootCannotBeHiddenByPhysicalRowCap(final String text)
            throws Exception {
        final var records = List.of(json(text));
        assertEquals(
                "ROOT_PER_UNVERIFIED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new DataExportObservedPage(
                                                request(DataExportTemplate.FRETES),
                                                records,
                                                100,
                                                200,
                                                200))
                        .getMessage());
    }

    @Test
    void perRejectsDistinctRootsAndPhysicalRowsHaveIndependentCap() throws Exception {
        final var records = List.of(json("{\"id\":1}"), json("{\"id\":2}"));
        assertEquals(
                "OBS_PER_EXCEEDED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new DataExportObservedPage(
                                                request(DataExportTemplate.FRETES),
                                                records,
                                                100,
                                                200,
                                                200))
                        .getMessage());
        assertEquals(
                "OBS_ROW_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new DataExportObservedPage(
                                                request(DataExportTemplate.FRETES),
                                                records,
                                                1,
                                                200,
                                                200))
                        .getMessage());
    }

    @Test
    void capHasNoInventedRootAndRequestDoesNotPermitWiderPageOrWindow() {
        assertEquals(
                "ROOT_PER_UNVERIFIED",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> request(DataExportTemplate.CONTAS_A_PAGAR).paginationRoot())
                        .getMessage());
        assertThrows(IllegalArgumentException.class, () -> request(DataExportTemplate.COLETAS));
        final var original = request(DataExportTemplate.FRETES).page();
        assertThrows(
                IllegalArgumentException.class,
                () -> new DataExportObservationRequest(original.withPage(2)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportObservationRequest(
                                new DataExportPageRequest(
                                        original.template(),
                                        original.businessDateWindow(),
                                        Optional.empty(),
                                        1,
                                        2,
                                        null)));
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void expansionMappingHasNoBindingAndPreservesMissingFields(final DataExportTemplate template)
            throws Exception {
        final var request = request(template);
        final var row = json("{\"" + request.paginationRoot() + "\":1}");
        final var page = new DataExportObservedPage(request, List.of(row), 100, 200, 200);
        final var mapped = new ExpansionObservedPageMapper().map(page);
        assertEquals(1, mapped.size());
        assertEquals(1, mapped.get(0).occurrence());
        assertNull(mapped.get(0).binding());
        if (mapped.get(0).observation() instanceof FaturaClienteObservation invoice) {
            assertEquals(Presence.ABSENT, invoice.comments().presence());
        }
        assertFalse(row.has("provenance"));
        assertFalse(row.has("capture_occurrence"));
    }

    @Test
    void emptyPageIsNotCompletenessProof() {
        final var page =
                new DataExportObservedPage(
                        request(DataExportTemplate.FRETES), List.of(), 100, 200, 200);
        assertEquals(0, page.physicalRows());
        assertEquals(0, page.distinctRoots());
        assertFalse(page.sourceCompletenessProven());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"},
            mode = EnumSource.Mode.EXCLUDE)
    void physicalOneRowIsNotAnEntityRootOrPerProof(final DataExportTemplate template)
            throws Exception {
        final var page =
                physical(template, "{\"data\":[{\"comments\":\"synthetic-private\"}]}", "comments");
        assertEquals(1, page.physicalRows());
        assertEquals(0, page.associatedItems());
        assertEquals(2, page.calls());
        assertEquals(200, page.infoStatus());
        assertEquals(200, page.dataStatus());
        assertEquals("TEST-OBS-PHYSICAL-001", page.mode());
        assertEquals(DataExportObservedPage.PhysicalStatus.PHYSICAL_ROW_OBSERVED, page.status());
        assertEquals(DataExportObservedPage.Verification.UNVERIFIED, page.sourceEntities());
        assertEquals(DataExportObservedPage.Verification.UNVERIFIED, page.remotePer());
        assertFalse(page.canonicalIdentityProven());
        assertFalse(page.paginationRootProven());
        assertFalse(page.sourceCompletenessProven());
        assertFalse(page.toString().contains("synthetic-private"));
        assertThrows(UnsupportedOperationException.class, () -> page.records().clear());
        ((ObjectNode) page.records().get(0)).put("comments", "changed");
        assertEquals("synthetic-private", page.records().get(0).path("comments").asText());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"},
            mode = EnumSource.Mode.EXCLUDE)
    void physicalEmptyIsNoDataObservedAndNeverCompleteness(final DataExportTemplate template)
            throws Exception {
        final var page = physical(template, "{\"data\":[]}", "comments");
        assertEquals(0, page.physicalRows());
        assertEquals(0, page.associatedItems());
        assertEquals(DataExportObservedPage.PhysicalStatus.NO_DATA_OBSERVED, page.status());
        assertEquals(DataExportObservedPage.Verification.UNVERIFIED, page.sourceEntities());
        assertFalse(page.sourceCompletenessProven());
    }

    @Test
    void physicalTwoRowsAreRejectedEvenWhenCandidateOrContentsAreIdentical() {
        for (final String rows :
                List.of(
                        "[{\"id\":1},{\"id\":1}]",
                        "[{\"id\":1},{\"id\":2}]",
                        "[{\"comments\":null},{\"comments\":null}]")) {
            assertEquals(
                    "PHYSICAL_ROW_BOUND",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            physical(
                                                    DataExportTemplate.FATURAS_POR_CLIENTE,
                                                    "{\"data\":" + rows + "}",
                                                    "id",
                                                    "comments"))
                            .getMessage());
        }
    }

    @Test
    void physicalEnvelopeDoesNotHideSiblingRowsOrPromoteUnitObjects() throws Exception {
        for (final String document :
                List.of(
                        "{\"data\":[{\"comments\":null}],\"extra\":[{\"comments\":null}]}",
                        "{\"data\":[],\"errors\":[]}",
                        "{\"comments\":null}",
                        "{\"data\":{\"comments\":null}}",
                        "{\"data\":null}",
                        "[]",
                        "null",
                        "{\"data\":[[]]}")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> physical(DataExportTemplate.CONTAS_A_PAGAR, document, "comments"));
        }
        assertEquals(
                1,
                physical(DataExportTemplate.MANIFESTOS, "[{\"comments\":null}]", "comments")
                        .physicalRows());
        assertEquals(0, physical(DataExportTemplate.MANIFESTOS, "[]", "comments").physicalRows());
        assertThrows(
                IllegalArgumentException.class,
                () -> physical(DataExportTemplate.FRETES, "{\"data\":[]}", "comments"));
    }

    @Test
    void physicalUnknownObjectsCollectionsAndUndeclaredFieldsCannotReachMapper() {
        for (final String row :
                List.of(
                        "{\"comments\":{\"data\":[1,2]}}",
                        "{\"comments\":[\"one\"]}",
                        "{\"comments\":[]}",
                        "{\"other\":\"scalar\"}",
                        "{}",
                        "null",
                        "{\"errors\":\"sensitive\"}")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            physical(
                                    DataExportTemplate.CONTAS_A_PAGAR,
                                    "{\"data\":[" + row + "]}",
                                    "comments",
                                    "errors"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"invoices_mapping", "fit_fte_invoices_order_number"})
    void physicalKnownInvoiceAssociationHasOneItemBound(final String name) throws Exception {
        final var page =
                physical(
                        DataExportTemplate.FATURAS_POR_CLIENTE,
                        "{\"data\":[{\"" + name + "\":[\"synthetic\"]}]}",
                        name);
        assertEquals(1, page.associatedItems());
        assertEquals(1, new ExpansionObservedPageMapper().mapPhysical(page).size());
        for (final String value :
                List.of(
                        "[\"a\",\"b\"]",
                        "[[\"a\"]]",
                        "[{\"id\":1}]",
                        "[1]",
                        "[null]",
                        "\"scalar\"",
                        "42",
                        "[\"" + "x".repeat(257) + "\"]")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            physical(
                                    DataExportTemplate.FATURAS_POR_CLIENTE,
                                    "{\"data\":[{\"" + name + "\":" + value + "}]}",
                                    name));
        }
        assertEquals(
                0,
                physical(
                                DataExportTemplate.FATURAS_POR_CLIENTE,
                                "{\"data\":[{\"" + name + "\":[]}]}",
                                name)
                        .associatedItems());
        assertEquals(
                0,
                physical(
                                DataExportTemplate.FATURAS_POR_CLIENTE,
                                "{\"data\":[{\"" + name + "\":null}]}",
                                name)
                        .associatedItems());
    }

    @Test
    void physicalAssociatedBudgetIsGlobalAcrossKnownCollections() throws Exception {
        assertEquals(
                "PHYSICAL_ASSOCIATED_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        physical(
                                                DataExportTemplate.FATURAS_POR_CLIENTE,
                                                "{\"data\":[{\"invoices_mapping\":[\"a\"],\"fit_fte_invoices_order_number\":[\"b\"]}]}",
                                                "invoices_mapping",
                                                "fit_fte_invoices_order_number"))
                        .getMessage());
        for (final String name : List.of("rcfdc", "rctac", "rctrc")) {
            assertEquals(
                    1,
                    physical(
                                    DataExportTemplate.SINISTROS,
                                    "{\"data\":[{\"" + name + "\":[\"a\"]}]}",
                                    name)
                            .associatedItems());
        }
        assertEquals(
                "PHYSICAL_ASSOCIATED_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        physical(
                                                DataExportTemplate.SINISTROS,
                                                "{\"data\":[{\"rcfdc\":[\"a\"],\"rctac\":[\"b\"]}]}",
                                                "rcfdc",
                                                "rctac"))
                        .getMessage());
        assertEquals(
                1,
                physical(
                                DataExportTemplate.INVENTARIO,
                                "{\"data\":[{\"cnr_c_s_fit_invoices_mapping\":[\"a\"]}]}",
                                "cnr_c_s_fit_invoices_mapping")
                        .associatedItems());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        physical(
                                DataExportTemplate.CONTAS_A_PAGAR,
                                "{\"data\":[{\"invoices_mapping\":[\"a\"]}]}",
                                "invoices_mapping"));
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void physicalExpansionKeepsBindingNullAndQuarantineBounded(final DataExportTemplate template)
            throws Exception {
        final String field =
                template == DataExportTemplate.INVENTARIO
                                || template == DataExportTemplate.SINISTROS
                        ? "sequence_code"
                        : template == DataExportTemplate.CONTAS_A_PAGAR
                                ? "ant_ils_sequence_code"
                                : "id";
        final var page = physical(template, "{\"data\":[{\"" + field + "\":true}]}", field);
        final var observations = new ExpansionObservedPageMapper().mapPhysical(page);
        assertEquals(1, observations.size());
        assertEquals(1, observations.get(0).occurrence());
        assertNull(observations.get(0).binding());
        assertFalse(observations.get(0).observation().valid());
        assertThrows(UnsupportedOperationException.class, observations::clear);
        assertEquals(
                0,
                new ExpansionObservedPageMapper()
                        .mapPhysical(physical(template, "{\"data\":[]}", field))
                        .size());
        assertEquals(DataExportObservedPage.Verification.UNVERIFIED, page.remotePer());
    }

    static DataExportObservedPage.PhysicalObservation physical(
            final DataExportTemplate template, final String document, final String... fieldNames)
            throws Exception {
        final var fields =
                java.util.Arrays.stream(fieldNames)
                        .map(
                                name ->
                                        new DataExportMetadataField(
                                                name, Optional.empty(), Optional.empty()))
                        .toList();
        return new DataExportObservedPage.PhysicalObservation(
                request(template),
                json(document),
                new DataExportTemplateInfoParser.ParsedTemplateInfo(fields, List.of()),
                200,
                200);
    }
}
