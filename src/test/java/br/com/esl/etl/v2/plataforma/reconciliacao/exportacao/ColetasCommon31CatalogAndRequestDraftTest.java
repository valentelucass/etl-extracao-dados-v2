package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageDisposition;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfoParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Ordinary JUnit methods. INFO tests exercise its public parser, not a fake wire capture. */
class ColetasCommon31CatalogAndRequestDraftTest {
    @Test
    void declaresExactlyThe31ReviewedItemsPathsKindsAndNativePositions() throws Exception {
        JsonNode catalog = ColetasCommonAdapter.catalog();
        assertEquals("COLETAS_COMMON31_V1", catalog.path("schema").asText());
        assertEquals(6908, catalog.path("reportId").asInt());
        JsonNode fields = catalog.required("columns");
        assertEquals(31, fields.size());
        Set<String> itemIds = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (int index = 0; index < 31; index++) {
            var expected = ColetasCommon31SyntheticFixture.FIELDS.get(index);
            JsonNode actual = fields.get(index);
            assertEquals(expected.item(), actual.path("itemId").asText());
            assertEquals(expected.title(), actual.path("name").asText());
            assertEquals(expected.source(), actual.path("sourceField").asText());
            assertEquals(expected.kind(), actual.path("kind").asText());
            assertEquals(index + 1, actual.path("nativeColumn").asInt());
            assertTrue(itemIds.add(actual.path("itemId").asText()));
            assertTrue(paths.add(actual.path("sourceField").asText()));
        }
    }

    @Test
    void commonPathsAreExplicitFlatKeysAndNeverGuessedRelationshipPaths() throws Exception {
        JsonNode fields = ColetasCommonAdapter.catalog().required("columns");
        for (JsonNode field : fields) {
            String name = field.required("sourceField").asText();
            assertTrue(name.matches("[a-z][a-z0-9_]*"));
            assertFalse(name.contains("/"));
            assertFalse(name.contains("."));
        }
        assertEquals("pck_mik_mft_sequence_code", fields.get(28).path("sourceField").asText());
        assertEquals("pck_pus_ore_trigger", fields.get(22).path("sourceField").asText());
    }

    @Test
    void infoParserRetainsAnExtraApiFieldWithoutChangingTheCommon31Catalog() throws Exception {
        ObjectNode info = info();
        info.withArray("fields").add("synthetic_api_only_extension");
        var parsed = new DataExportTemplateInfoParser().parse(info);
        assertEquals(32, parsed.fields().size());
        assertTrue(
                parsed.fields().stream()
                        .anyMatch(
                                field ->
                                        "synthetic_api_only_extension"
                                                .equals(field.technicalName())));
        assertEquals(6, parsed.filters().size());
        assertEquals(31, ColetasCommonAdapter.catalog().required("columns").size());
    }

    @Test
    void infoParserRejectsDuplicateTechnicalFieldsInsteadOfSilentlyChoosingOne() {
        ObjectNode info = info();
        info.withArray("fields").add("id");
        assertThrows(
                IllegalStateException.class, () -> new DataExportTemplateInfoParser().parse(info));
    }

    @Test
    void infoParserRejectsAScalarFieldCollection() {
        ObjectNode info = info();
        info.put("fields", 31);
        assertThrows(
                IllegalStateException.class, () -> new DataExportTemplateInfoParser().parse(info));
    }

    @Test
    void requestContainsTheSixClosedFiltersAndUsesThe6908Profile() {
        ObjectNode request = ColetasCommon31SyntheticFixture.request();
        ColetasIndependentRequest.validate(request);
        assertEquals(
                Set.of(
                        "picks[finish_date]",
                        "picks[service_date]",
                        "picks[request_date]",
                        "picks[corporation_id]",
                        "picks[status]",
                        "scopes[by_updated_at]"),
                names(request.required("filters")));
        ObjectNode body = ColetasIndependentRequest.body(request, 1, 25);
        assertEquals(
                "2036-03-20 - 2036-03-26",
                body.path("search").path("picks").path("request_date").asText());
        assertEquals(Set.of("search", "page", "per"), names(body));
        assertEquals("1", body.path("page").asText());
        assertEquals("25", body.path("per").asText());
        assertFalse(body.toString().contains("GraphQL"));
    }

    @Test
    void anExportReportOrFretesProfileCannotBeSubstitutedForColetas6908() {
        ObjectNode request = ColetasCommon31SyntheticFixture.request();
        request.put("reportId", 6389);
        assertThrows(
                IllegalArgumentException.class, () -> ColetasIndependentRequest.validate(request));
        request.put("reportId", 6908).put("profile", "FRETES_6389_EXPORT_XLSX_V1");
        assertThrows(
                IllegalArgumentException.class, () -> ColetasIndependentRequest.validate(request));
    }

    @Test
    void anAbsentFilterCannotBeTreatedAsAnEmptyFilter() {
        ObjectNode request = ColetasCommon31SyntheticFixture.request();
        ((ObjectNode) request.required("filters")).remove("picks[status]");
        assertThrows(
                IllegalArgumentException.class, () -> ColetasIndependentRequest.validate(request));
    }

    @Test
    void nullAndNumericFilterValuesCannotAcquireTheMeaningOfEmptyStrings() {
        ObjectNode request = ColetasCommon31SyntheticFixture.request();
        ObjectNode filters = (ObjectNode) request.required("filters");
        filters.putNull("picks[status]");
        assertThrows(
                IllegalArgumentException.class, () -> ColetasIndependentRequest.validate(request));
        filters.put("picks[status]", 0);
        assertThrows(
                IllegalArgumentException.class, () -> ColetasIndependentRequest.validate(request));
    }

    @Test
    void anAdditionalOrNonEmptyAuxiliaryFilterRequiresASeparateQualifiedContract() {
        ObjectNode request = ColetasCommon31SyntheticFixture.request();
        ObjectNode filters = (ObjectNode) request.required("filters");
        filters.put("synthetic[extra]", "");
        assertThrows(
                IllegalArgumentException.class, () -> ColetasIndependentRequest.validate(request));
        filters.remove("synthetic[extra]");
        filters.put("picks[status]", "pending");
        assertThrows(
                IllegalArgumentException.class, () -> ColetasIndependentRequest.validate(request));
    }

    @Test
    void aDifferentPeriodCannotBeSmuggledThroughTheRequestDateFilter() {
        ObjectNode request = ColetasCommon31SyntheticFixture.request();
        ((ObjectNode) request.required("filters"))
                .put("picks[request_date]", "2036-03-21 - 2036-03-26");
        assertThrows(
                IllegalArgumentException.class, () -> ColetasIndependentRequest.validate(request));
    }

    @Test
    void onlyObservedDateFilterPresentationsAreCanonicalized() {
        ObjectNode left =
                (ObjectNode) ColetasCommon31SyntheticFixture.request().required("filters");
        ObjectNode right = left.deepCopy().put("picks[request_date]", "20/03/2036 - 26/03/2036");
        assertTrue(ColetasIndependentRequest.sameFilters(left, right));
        right.put("picks[request_date]", "2036-03-21 - 2036-03-26");
        assertFalse(ColetasIndependentRequest.sameFilters(left, right));
    }

    @Test
    void pageAndPerBoundsCannotCreateAnUnboundedCapture() {
        ObjectNode request = ColetasCommon31SyntheticFixture.request();
        assertThrows(
                IllegalArgumentException.class,
                () -> ColetasIndependentRequest.body(request, 0, 25));
        assertThrows(
                IllegalArgumentException.class,
                () -> ColetasIndependentRequest.body(request, 65, 25));
        assertThrows(
                IllegalArgumentException.class,
                () -> ColetasIndependentRequest.body(request, 1, 101));
    }

    @Test
    void theExistingMapperRetainsExtraFieldsFractionsAndRawAssociationCandidates()
            throws Exception {
        ObjectNode authored = ColetasCommon31SyntheticFixture.sourceRow();
        authored.putObject("synthetic_api_extension").put("literal", "SYNTHETIC EXTRA");
        authored.putObject("manifesto").put("sequence_code", 999);
        var mapped = new ColetaDataExportRecordMapper().map(1, authored);
        assertEquals(ColetaStageDisposition.VALID, mapped.disposition());
        assertEquals(authored, ExportValidationSupport.JSON.readTree(mapped.payloadJson()));
        assertEquals(
                42,
                ExportValidationSupport.JSON
                        .readTree(mapped.payloadJson())
                        .path("pck_mik_mft_sequence_code")
                        .asInt());
        assertTrue(
                ExportValidationSupport.JSON
                        .readTree(mapped.relationCandidatesJson())
                        .has("manifesto"));
        assertFalse(mapped.relationCandidatesJson().contains("canonical"));
    }

    private static Set<String> names(JsonNode value) {
        Set<String> result = new HashSet<>();
        value.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private static ObjectNode info() {
        ObjectNode info = ExportValidationSupport.JSON.createObjectNode();
        var fields = info.putArray("fields");
        ColetasCommon31SyntheticFixture.FIELDS.forEach(field -> fields.add(field.source()));
        info.putArray("filters")
                .add("picks.finish_date")
                .add("picks.service_date")
                .add("picks.request_date")
                .add("picks.corporation_id")
                .add("picks.status")
                .add("scopes.by_updated_at");
        return info;
    }
}
