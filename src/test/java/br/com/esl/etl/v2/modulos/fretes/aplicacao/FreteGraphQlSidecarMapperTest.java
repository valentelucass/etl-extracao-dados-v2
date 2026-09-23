package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.fretes.domain.FreteStageRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class FreteGraphQlSidecarMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final FreteGraphQlSidecarMapper mapper = new FreteGraphQlSidecarMapper();

    @Test
    void preservesExactlyTheApprovedSidecarEvidenceAndLeavesRelationUnresolved() throws Exception {
        final var envelope =
                mapper.map(
                        json.readTree(
                                """
                                {"freight":{"edges":[{"node":{"id":1,
                                  "accountingCreditId":2,"accountingCreditInstallmentId":3,
                                  "referenceNumber":"R","cte":{"key":"K"},"total":"10",
                                  "corporationSequenceNumber":4,"pickItemId":5}}],
                                  "pageInfo":{"hasNextPage":false,"endCursor":"C"}}}
                                """));

        assertEquals(1, envelope.edgeCount());
        assertTrue(envelope.canonicalJson().contains("UNRESOLVED_CANDIDATE_ONLY"));
        assertTrue(envelope.canonicalJson().contains("V2_046B_OWNS_CROSSWALK"));
        assertFalse(envelope.canonicalJson().contains("coletaId"));
        assertFalse(envelope.canonicalJson().contains("freshness"));
    }

    @Test
    void boundsOneSidecarPageAndRejectsMalformedEdges() throws Exception {
        final ObjectNode response = json.createObjectNode();
        final ArrayNode edges = response.putObject("freight").putArray("edges");
        for (int index = 0; index <= FreteStageRecord.MAXIMUM_SIDECAR_EDGES; index++) {
            edges.addObject().putObject("node").put("id", index + 1);
        }
        assertThrows(IllegalArgumentException.class, () -> mapper.map(response));
        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.map(json.readTree("{\"freight\":{\"edges\":[1]}}")));
    }

    @Test
    void absentEnvelopeIsIndependentAndEmpty() {
        final var absent = FreteGraphQlSidecarMapper.absent();
        assertEquals(0, absent.edgeCount());
        assertTrue(absent.canonicalJson().contains("edgesPresence\":\"ABSENT"));
        assertTrue(absent.canonicalJson().contains("/freight/pageInfo/hasNextPage"));
        assertTrue(absent.canonicalJson().contains("/freight/pageInfo/endCursor"));
    }
}
