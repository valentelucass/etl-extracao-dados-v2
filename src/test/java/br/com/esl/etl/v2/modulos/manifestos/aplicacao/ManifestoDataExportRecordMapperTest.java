package br.com.esl.etl.v2.modulos.manifestos.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoAttributePresence;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoFreshnessOrigin;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoMetric;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoStageDisposition;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoStageRecord;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoTextField;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestoDataExportRecordMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ManifestoDataExportRecordMapper mapper = new ManifestoDataExportRecordMapper();

    @Test
    void mapsRootChildrenMetricsCompetenceAndOnlyAppendOnlyRelationEvidence() {
        final ManifestoStageRecord record =
                mapper.map(
                        1,
                        node(
                                """
                                {"sequence_code":91,"status":"pending","mdfe_status":"authorized",
                                 "finished_at":null,"closed_at":null,
                                 "departured_at":"2036-01-05T10:00:00-03:00",
                                 "created_at":"2036-01-01T10:00:00Z",
                                 "mft_pfs_pck_sequence_code":12,"mft_mfs_number":7,
                                 "mft_mfs_key":"12345678901234567890123456789012345678901234",
                                 "km":0,"total_cost":"10.500","manifest_freights_total":2,
                                 "total_taxed_weight":3,"mft_vie_weight_capacity":4,
                                 "manifest_items_count":5,"finalized_manifest_items_count":6,
                                 "operational_comments":"ação e 🚚"}
                                """));

        assertEquals(ManifestoStageDisposition.VALID, record.disposition());
        assertEquals("INTEGER:91", record.sourceKey().storageValue());
        assertEquals(ManifestoFreshnessOrigin.DEPARTURED_AT, record.freshnessOrigin());
        assertEquals("2036-01-05T13:00:00Z", record.freshnessAtUtc().toString());
        assertEquals(ManifestoAttributePresence.VALUE, record.competence().presence());
        assertTrue(record.competence().canonicalJson().contains("departured_at"));
        assertEquals("0", record.metrics().get(ManifestoMetric.KM).canonicalNumber());
        assertEquals(
                "4",
                record.metrics().get(ManifestoMetric.VEHICLE_WEIGHT_CAPACITY).canonicalNumber());
        assertTrue(record.pickSourceKey().isPresent());
        assertEquals("INTEGER:12", record.pickSourceKey().orElseThrow().storageValue());
        assertEquals(
                "12345678901234567890123456789012345678901234", record.mdfe().orElseThrow().key());
        assertEquals(7, record.mdfe().orElseThrow().number().intValueExact());
        assertEquals(
                ManifestoAttributePresence.VALUE,
                record.rootFields().get("mdfe_status").presence());
        assertTrue(record.relationCandidatesJson().contains("mft_pfs_pck_sequence_code"));
        assertFalse(record.relationCandidatesJson().contains("coleta"));
        assertFalse(record.toString().contains("INTEGER:91"));
        assertFalse(record.toString().contains("ação"));
    }

    @Test
    void quarantinesIdentityTemporalAndChildAsymmetryFailClosed() {
        final List<ManifestoStageRecord> records =
                List.of(
                        mapper.map(1, node("{}")),
                        mapper.map(2, node("{\"sequence_code\":\"91\"}")),
                        mapper.map(3, node("{\"sequence_code\":0}")),
                        mapper.map(
                                4,
                                node(
                                        "{\"sequence_code\":91,\"finished_at\":\"2036-01-01 10:00:00\"}")),
                        mapper.map(
                                5,
                                node(
                                        "{\"sequence_code\":91,\"finished_at\":\"2036-01-01T10:00:00Z\",\"closed_at\":\"invalid\"}")),
                        mapper.map(
                                6,
                                node(
                                        "{\"sequence_code\":91,\"finished_at\":\"2036-01-01T10:00:00Z\",\"departured_at\":\"invalid\"}")),
                        mapper.map(
                                7,
                                node(
                                        "{\"sequence_code\":91,\"finished_at\":\"2036-01-01T10:00:00Z\",\"mft_mfs_number\":1}")),
                        mapper.map(
                                8,
                                node(
                                        "{\"sequence_code\":91,\"finished_at\":\"2036-01-01T10:00:00Z\","
                                                + "\"mft_mfs_key\":\"12345678901234567890123456789012345678901234\"}")),
                        mapper.map(
                                9,
                                node(
                                        "{\"sequence_code\":91,\"finished_at\":\"2036-01-01T10:00:00Z\","
                                                + "\"mft_pfs_pck_sequence_code\":-1}")),
                        mapper.map(
                                10,
                                node(
                                        "{\"sequence_code\":91,\"finished_at\":\"2036-01-01T10:00:00Z\",\"km\":false}")));

        assertEquals("MISSING_SOURCE_KEY", records.get(0).quarantineReasonCode());
        assertEquals("INVALID_SOURCE_KEY_TYPE", records.get(1).quarantineReasonCode());
        assertEquals("INVALID_SOURCE_KEY_VALUE", records.get(2).quarantineReasonCode());
        assertEquals("INVALID_FRESHNESS_TEMPORAL", records.get(3).quarantineReasonCode());
        assertEquals("INVALID_FRESHNESS_TEMPORAL", records.get(4).quarantineReasonCode());
        assertEquals("INVALID_FRESHNESS_TEMPORAL", records.get(5).quarantineReasonCode());
        assertEquals(
                "CHILD_NUMBER_SIGNAL_WITHOUT_VALID_KEY", records.get(6).quarantineReasonCode());
        assertEquals("MDFE_NUMBER_KEY_PAIR_ASYMMETRY", records.get(7).quarantineReasonCode());
        assertEquals("INVALID_CHILD_KEY_VALUE", records.get(8).quarantineReasonCode());
        assertEquals("INVALID_METRIC_VALUE", records.get(9).quarantineReasonCode());
        records.forEach(
                record -> {
                    assertEquals(ManifestoStageDisposition.QUARANTINE, record.disposition());
                    assertNull(record.payloadJson());
                    assertTrue(record.toString().contains("<redacted>"));
                });
    }

    @Test
    void preservesAbsenceAndNullButRejectsEveryTextOverflowWithoutUnicodeCutting() {
        final ObjectNode accepted = baseline();
        final ManifestoStageRecord absent = mapper.map(1, accepted);
        assertEquals(
                ManifestoAttributePresence.ABSENT,
                absent.rootFields().get("contract_type").presence());
        for (final ManifestoTextField field : ManifestoTextField.values()) {
            accepted.put(field.sourceField(), "x".repeat(field.maximumUtf16Units()));
        }
        accepted.put("mft_mfs_key", "12345678901234567890123456789012345678901234");
        accepted.put("mft_mfs_number", 1);
        accepted.put("mft_vie_license_plate", "🚚🚚🚚🚚🚚");
        accepted.put("operational_comments", "e\u0301 comentário");
        final ManifestoStageRecord valid = mapper.map(1, accepted);
        assertEquals(ManifestoStageDisposition.VALID, valid.disposition());
        assertEquals(
                ManifestoAttributePresence.VALUE,
                valid.rootFields().get("contract_type").presence());
        assertNotNull(valid.fieldPresenceJson());

        for (final ManifestoTextField field : ManifestoTextField.values()) {
            if (field == ManifestoTextField.MDFE_KEY) {
                continue;
            }
            final ObjectNode overflowing = baseline();
            overflowing.put(field.sourceField(), "x".repeat(field.maximumUtf16Units() + 1));
            final ManifestoStageRecord rejected = mapper.map(1, overflowing);
            assertEquals(
                    "TEXT_LIMIT_EXCEEDED", rejected.quarantineReasonCode(), field.sourceField());
        }
    }

    private static ObjectNode baseline() {
        final ObjectNode node = JSON.createObjectNode();
        node.put("sequence_code", 91);
        node.put("finished_at", "2036-01-01T10:00:00Z");
        return node;
    }

    private static JsonNode node(final String document) {
        try {
            return JSON.readTree(document);
        } catch (final JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }
}
