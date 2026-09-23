package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class GraphQlRequestJsonSerializerTest {
    @Test
    void serializationFailureDoesNotRetainRequestOrSensitiveCause() {
        final String sensitive = "synthetic-sensitive-serializer-detail";
        final var mapper = new ObjectMapper();
        final var module = new SimpleModule();
        module.addSerializer(
                ObjectNode.class,
                new StdSerializer<ObjectNode>(ObjectNode.class) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void serialize(
                            final ObjectNode value,
                            final JsonGenerator generator,
                            final SerializerProvider provider)
                            throws IOException {
                        throw new IOException(sensitive);
                    }
                });
        mapper.registerModule(module);
        final var serializer = new GraphQlRequestJsonSerializer(mapper);

        final var failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                serializer.serialize(
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT)));

        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(failure.toString().contains(sensitive));
    }
}
