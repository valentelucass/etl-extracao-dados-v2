package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportMetadataField;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageFetch;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseForm;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfo;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContractRemoteEvidenceRunnerChecksTest {

    @Test
    void requiresBothTheBusinessAndUpdatedAtFiltersToBeDeclared() {
        final DataExportTemplateInfo complete =
                infoWithFilters("picks.request_date", "scopes.by_updated_at");
        final DataExportTemplateInfo missingUpdatedAt = infoWithFilters("picks.request_date");

        assertTrue(
                ContractRemoteEvidenceRunner.requiredFiltersDeclared(
                        DataExportTemplate.COLETAS, complete));
        assertFalse(
                ContractRemoteEvidenceRunner.requiredFiltersDeclared(
                        DataExportTemplate.COLETAS, missingUpdatedAt));
    }

    @Test
    void acceptsTheUnqualifiedMetadataNamesObservedInTheRemoteInfoDocument() {
        final DataExportTemplateInfo complete = infoWithFilters("request_date", "by_updated_at");

        assertTrue(
                ContractRemoteEvidenceRunner.requiredFiltersDeclared(
                        DataExportTemplate.COLETAS, complete));
    }

    @Test
    void distinguishesAnObservedLateChangeFromAnEmptyScopeResult() {
        assertTrue(ContractRemoteEvidenceRunner.lateChangeScopeObserved(pageFetch(1, true)));
        assertFalse(ContractRemoteEvidenceRunner.lateChangeScopeObserved(pageFetch(0, true)));
    }

    @Test
    void profilesThePageLimitByDistinctDataExportEntitiesInsteadOfPhysicalRows() {
        final DataExportPageFetch expandedPage =
                new DataExportPageFetch(
                        new DataExportPageResponse(
                                List.of(
                                        JsonNodeFactory.instance
                                                .objectNode()
                                                .put("id", "synthetic-1"),
                                        JsonNodeFactory.instance
                                                .objectNode()
                                                .put("id", "synthetic-1"))),
                        DataExportTransport.GET_WITH_QUERY,
                        200,
                        Optional.empty(),
                        DataExportResponseForm.ROOT_ARRAY,
                        true);

        final ContractPayloadEvidence evidence =
                ContractPayloadEvidence.from(
                        ContractPayloadObservation.POPULATED_FIRST_APPROVED_PAGE_SIZE,
                        DataExportTemplate.COLETAS,
                        1,
                        expandedPage);

        assertEquals(2, evidence.recordCount());
        assertTrue(evidence.paginationEntityCountVerifiable());
        assertEquals(1, evidence.paginationEntityCount());
        assertFalse(evidence.recordCountWithinRequestedPageSize());
        assertTrue(evidence.paginationEntityCountWithinRequestedPageSize());
    }

    private static DataExportTemplateInfo infoWithFilters(final String... filterNames) {
        return new DataExportTemplateInfo(
                DataExportTemplate.COLETAS,
                200,
                Optional.empty(),
                true,
                List.of(),
                java.util.Arrays.stream(filterNames)
                        .map(
                                name ->
                                        new DataExportMetadataField(
                                                name, Optional.empty(), Optional.empty()))
                        .toList());
    }

    private static DataExportPageFetch pageFetch(
            final int recordCount, final boolean jsonContentTypeDeclared) {
        final List<com.fasterxml.jackson.databind.JsonNode> records =
                recordCount == 0
                        ? List.of()
                        : List.of(JsonNodeFactory.instance.objectNode().put("id", "synthetic"));
        return new DataExportPageFetch(
                new DataExportPageResponse(records),
                DataExportTransport.GET_WITH_BODY,
                200,
                Optional.empty(),
                DataExportResponseForm.ROOT_ARRAY,
                jsonContentTypeDeclared);
    }
}
