package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Entradas sintéticas locais, sem adapter de fornecedor, rede, banco ou configuração externa. */
class ContractColetasWindowIdentityBridgeTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsTheSameAssociationsWithoutRequiringOrderOrPhysicalMultiplicity() throws Exception {
        final JsonNode first = row("{\"id\":900001,\"sequence_code\":700001}");
        final JsonNode second = row("{\"id\":900002,\"sequence_code\":700002}");

        assertDoesNotThrow(
                () ->
                        ContractColetasWindowIdentityBridge.verify(
                                List.of(first, first, first, second, second),
                                List.of(second, first, first)));
    }

    @Test
    void rejectsDisjointIdsWithTheSameNaturalKeys() throws Exception {
        assertMismatch(
                twoRoots(),
                List.of(
                        row("{\"id\":910001,\"sequence_code\":700001}"),
                        row("{\"id\":910002,\"sequence_code\":700002}")));
    }

    @Test
    void rejectsPermutedAssociationsWithTheSameIdAndNaturalKeySets() throws Exception {
        assertMismatch(
                twoRoots(),
                List.of(
                        row("{\"id\":900002,\"sequence_code\":700001}"),
                        row("{\"id\":900001,\"sequence_code\":700002}")));
    }

    @Test
    void rejectsBothAMissingAndAnExtraRoot() throws Exception {
        final List<JsonNode> singleRoot = List.of(row("{\"id\":900001,\"sequence_code\":700001}"));

        assertMismatch(twoRoots(), singleRoot);
        assertMismatch(singleRoot, twoRoots());
    }

    @Test
    void doesNotCoerceATextualIdIntoItsNumericRepresentation() throws Exception {
        assertMismatch(
                List.of(row("{\"id\":900001,\"sequence_code\":700001}")),
                List.of(row("{\"id\":\"900001\",\"sequence_code\":700001}")));
    }

    @Test
    void rejectsNullMissingContainerOrBlankIdentitiesAndNaturalKeys() throws Exception {
        final List<JsonNode> valid = List.of(row("{\"id\":900001,\"sequence_code\":700001}"));
        for (final String input :
                List.of(
                        "null",
                        "[]",
                        "{}",
                        "{\"id\":null,\"sequence_code\":700001}",
                        "{\"id\":{},\"sequence_code\":700001}",
                        "{\"id\":[],\"sequence_code\":700001}",
                        "{\"id\":\" \",\"sequence_code\":700001}",
                        "{\"id\":900001}",
                        "{\"id\":900001,\"sequence_code\":null}",
                        "{\"id\":900001,\"sequence_code\":{}}",
                        "{\"id\":900001,\"sequence_code\":[]}",
                        "{\"id\":900001,\"sequence_code\":\" \"}")) {
            final List<JsonNode> invalid = List.of(row(input));
            assertMismatch(invalid, valid);
            assertMismatch(valid, invalid);
        }
        assertMismatch(null, valid);
        assertMismatch(valid, null);
    }

    @Test
    void rejectsConflictingKeysWithinAnExpandedEntity() throws Exception {
        final List<JsonNode> conflict =
                List.of(
                        row("{\"id\":900001,\"sequence_code\":700001}"),
                        row("{\"id\":900001,\"sequence_code\":700002}"));

        assertMismatch(conflict, conflict);
    }

    @Test
    void rejectsANaturalKeySharedByDifferentIds() throws Exception {
        final List<JsonNode> conflict =
                List.of(
                        row("{\"id\":900001,\"sequence_code\":700001}"),
                        row("{\"id\":900002,\"sequence_code\":700001}"));

        assertMismatch(conflict, conflict);
    }

    @Test
    void equalEmptyInputsDoNotInventACoverageDecision() {
        assertDoesNotThrow(() -> ContractColetasWindowIdentityBridge.verify(List.of(), List.of()));
    }

    private List<JsonNode> twoRoots() throws Exception {
        return List.of(
                row("{\"id\":900001,\"sequence_code\":700001}"),
                row("{\"id\":900002,\"sequence_code\":700002}"));
    }

    private static void assertMismatch(final List<JsonNode> full, final List<JsonNode> daily) {
        final IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> ContractColetasWindowIdentityBridge.verify(full, daily));
        assertEquals("COLETAS_FULL_WINDOW_DAILY_IDENTITY_MISMATCH", failure.getMessage());
    }

    private JsonNode row(final String value) throws Exception {
        return json.readTree(value);
    }
}
