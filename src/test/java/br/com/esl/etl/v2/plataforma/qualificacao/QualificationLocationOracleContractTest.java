package br.com.esl.etl.v2.plataforma.qualificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QualificationLocationOracleContractTest {
    @Test
    void pinsAllSyntheticLocationRootsForEachDeclaredVariant() throws IOException {
        final var baseline = new QualificationLocationOracle();
        final var representative =
                new QualificationLocationOracle(AnalyticScenarioVariant.VALUES_AND_NULLS);
        assertEquals(
                "0e9f2eb4c1d31bcd5387793996d3ca3339b1927d582888e98fcbdfc33cc12b2b",
                QualificationJson.sha256(
                        Path.of(
                                "src/main/resources/qualification-laboratory/location-hashes.synthetic.json")));
        assertEquals(
                "13ed719ae9573ea9541b605d1578024ce85d192a2ff2f9465be0de646188e2fe",
                QualificationJson.sha256(
                        Path.of(
                                "src/main/resources/qualification-laboratory/location-representative-hashes.synthetic.json")));
        assertEquals(
                "6b65d04b8b0884cc4e80c34cc2493d6d0c64614fa9ac2b71cc75c516ea1661e4",
                baseline.hash(1));
        assertEquals(
                "afff1aca705db76c166f76790453bf6bcc82264bf3bd23d737d9b9a5f96d195d",
                baseline.hash(32));
        assertEquals(
                "9af28acece7a9707674653d65352dfe37e6f2934ffc0813f05f651f2a1236386",
                representative.hash(1));
        assertEquals(
                "989c2f2cd09e5cf007a95880ec02e961a4c309a0998be70124659ca9992ff0d2",
                representative.hash(32));
        int differences = 0;
        for (int root = 1; root <= 32; root++) {
            final String first = baseline.hash(root);
            final String second = representative.hash(root);
            assertEquals(64, first.length());
            assertEquals(64, second.length());
            if (!first.equals(second)) {
                differences++;
            }
        }
        assertNotEquals(0, differences);
        for (final int outside : new int[] {0, 33}) {
            assertThrows(IllegalArgumentException.class, () -> baseline.hash(outside));
            assertThrows(IllegalArgumentException.class, () -> representative.hash(outside));
        }
    }
}
