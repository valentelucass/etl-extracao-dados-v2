package br.com.esl.etl.v2.plataforma.qualificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualificationWireOracleContractTest {
    @Test
    void reconstructsSyntheticSourceKeysAndCorrectionsFromPackagedSeeds() throws IOException {
        final var oracle = new QualificationWireOracle();
        assertEquals(
                12, oracle.source("CAP", 12, 1, 1, false).path("ant_ils_sequence_code").asInt());
        assertEquals(122, oracle.source("FAT", 12, 2, 1, false).path("id").asInt());
        assertEquals(
                122,
                oracle.source("INV", 12, 2, 1, false)
                        .path("cnr_c_s_fit_corporation_sequence_number")
                        .asInt());
        assertEquals(
                122,
                oracle.source("SIN", 12, 2, 1, false)
                        .path("icm_fis_fit_corporation_sequence_number")
                        .asInt());
        assertEquals(300012, oracle.source("FRE", 12, 1, 1, false).path("id").asInt());
        assertEquals(
                600012,
                oracle.source("LOC", 12, 1, 1, false).path("corporation_sequence_number").asInt());
        assertEquals(200012, oracle.source("COL", 12, 1, 1, false).path("id").asInt());
        assertEquals(10011, oracle.source("COT", 12, 1, 1, false).path("sequence_code").asInt());
        final var original = oracle.source("MAN", 12, 1, 1, false);
        final var corrected = oracle.source("MAN", 12, 1, 2, true);
        assertFalse(original.path("finished_at").equals(corrected.path("finished_at")));
        assertEquals("SYNTHETIC BRANCH B", corrected.path("mft_crn_psn_nickname").asText());
        assertEquals("INTEGER:12", oracle.manifestKey(12, 1, false));
        assertEquals(3, oracle.sourceRows("COL", 12, 1, false));
        assertEquals(12, oracle.expansionRootOrdinal("FRE", "INTEGER", "root-12"));
        assertEquals(
                2,
                oracle.expansionComponentOrdinal(
                        "FRE", "INTEGER", "root-12", "PART", "part-1", "COMP", "component-2"));
    }

    @Test
    void rejectsOutOfScopeWireAndAcceptsOnlyObjectFields() throws IOException {
        final var oracle = new QualificationWireOracle(AnalyticScenarioVariant.VALUES_AND_NULLS);
        for (final String entity :
                List.of("CAP", "FAT", "INV", "SIN", "FRE", "LOC", "COL", "COT", "MAN")) {
            assertThrows(
                    IllegalArgumentException.class, () -> oracle.source(entity, 0, 1, 1, false));
            assertThrows(
                    IllegalArgumentException.class, () -> oracle.source(entity, 1, 3, 1, false));
            assertThrows(
                    IllegalArgumentException.class, () -> oracle.source(entity, 1, 1, 1001, false));
        }
        assertThrows(
                IllegalArgumentException.class, () -> oracle.source("UNKNOWN", 1, 1, 1, false));
        assertFalse(
                oracle.compare(
                        "FRE",
                        oracle.source("FRE", 1, 1, 1, false),
                        JsonNodeFactory.instance.arrayNode(),
                        JsonNodeFactory.instance.objectNode()));
        assertEquals(
                2,
                QualificationWireOracle.names(
                                JsonNodeFactory.instance.objectNode().put("a", 1).put("b", 2))
                        .size());
        assertThrows(IllegalArgumentException.class, () -> QualificationWireOracle.names(null));
        assertThrows(IllegalArgumentException.class, () -> QualificationWireOracle.lateral(0));
        assertTrue(QualificationWireOracle.lateral(1).size() > 0);
    }

    @Test
    void detectsChangedFreightWireAndReportsOnlyManifestFieldCoordinate() throws IOException {
        final var oracle = new QualificationWireOracle();
        final var freight = oracle.source("FRE", 2, 1, 1, false);
        assertTrue(
                oracle.compare(
                        "FRE", freight, freight.deepCopy(), JsonNodeFactory.instance.objectNode()));
        final var changed = freight.deepCopy();
        changed.put("id", freight.path("id").asInt() + 1);
        assertFalse(oracle.compare("FRE", freight, changed, JsonNodeFactory.instance.objectNode()));

        final var manifest = oracle.source("MAN", 2, 1, 1, false);
        assertEquals(
                "MEMBER_SET",
                oracle.manifestDifference(
                        manifest,
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.objectNode()));
    }
}
