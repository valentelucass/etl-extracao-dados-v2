package br.com.esl.etl.v2.modulos.usuarios.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioNamePresence;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageDisposition;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageRecord;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsuarioGraphQlNodeMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final UsuarioGraphQlNodeMapper mapper = new UsuarioGraphQlNodeMapper();

    @Test
    void keepsIntegerAndStringKeysDistinctAndPreservesNamePresence() {
        final UsuarioStageRecord integer = mapper.map(1, node("{\"id\":1,\"name\":\"Álvaro\"}"));
        final UsuarioStageRecord string = mapper.map(2, node("{\"id\":\"1\",\"name\":null}"));
        final UsuarioStageRecord absent = mapper.map(3, node("{\"id\":\"synthetic-user\"}"));

        assertEquals(UsuarioStageDisposition.VALID, integer.disposition());
        assertEquals(ScopedSourceIdentity.WireType.INTEGER, integer.sourceKey().wireType());
        assertEquals("INTEGER:1", integer.sourceKey().storageValue());
        assertEquals(UsuarioNamePresence.VALUE, integer.namePresence());
        assertEquals("Álvaro", integer.name());
        assertEquals(ScopedSourceIdentity.WireType.STRING, string.sourceKey().wireType());
        assertEquals("STRING:1", string.sourceKey().storageValue());
        assertEquals(UsuarioNamePresence.NULL, string.namePresence());
        assertNull(string.name());
        assertEquals(UsuarioNamePresence.ABSENT, absent.namePresence());
        assertNull(absent.name());
    }

    @Test
    void quarantinesEveryInvalidSourceKeyWithASanitizedReason() {
        final List<String> documents =
                List.of(
                        "{}",
                        "{\"id\":null}",
                        "{\"id\":true}",
                        "{\"id\":\"   \"}",
                        "{\"id\":\"x" + "x".repeat(256) + "\"}");
        final List<String> expectedReasons =
                List.of(
                        "MISSING_SOURCE_KEY",
                        "MISSING_SOURCE_KEY",
                        "INVALID_SOURCE_KEY_TYPE",
                        "INVALID_SOURCE_KEY_VALUE",
                        "SOURCE_KEY_TOO_LONG");

        for (int index = 0; index < documents.size(); index++) {
            final UsuarioStageRecord record = mapper.map(index + 1, node(documents.get(index)));
            assertEquals(UsuarioStageDisposition.QUARANTINE, record.disposition());
            assertEquals(expectedReasons.get(index), record.quarantineReasonCode());
            assertNull(record.sourceKey());
        }
    }

    @Test
    void quarantinesInvalidNamesWithoutCopyingTheirValueIntoTheReason() {
        final List<JsonNode> nodes =
                List.of(
                        node("{\"id\":1,\"name\":42}"),
                        node("{\"id\":2,\"name\":\"" + "x".repeat(256) + "\"}"),
                        textNameNode("line\nbreak"),
                        textNameNode("\uD800"));
        final List<String> reasons =
                List.of(
                        "INVALID_NAME_TYPE",
                        "INVALID_NAME_VALUE",
                        "INVALID_NAME_VALUE",
                        "INVALID_NAME_VALUE");

        for (int index = 0; index < nodes.size(); index++) {
            final UsuarioStageRecord record = mapper.map(index + 1, nodes.get(index));
            assertEquals(UsuarioStageDisposition.QUARANTINE, record.disposition());
            assertEquals(reasons.get(index), record.quarantineReasonCode());
        }
    }

    @Test
    void requiresANonNullNode() {
        assertThrows(NullPointerException.class, () -> mapper.map(1, null));
    }

    private static JsonNode node(final String document) {
        try {
            return JSON.readTree(document);
        } catch (final JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }

    private static JsonNode textNameNode(final String value) {
        return JSON.createObjectNode().put("id", 3).put("name", value);
    }
}
