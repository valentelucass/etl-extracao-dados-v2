package br.com.esl.etl.v2.contratos.caracterizacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StrictUtf8JsonLoaderTest {

    private final StrictUtf8JsonLoader loader = new StrictUtf8JsonLoader();

    @Test
    void rejectsOversizedInputBeforeCallingTheParser() {
        final AtomicInteger parserCalls = new AtomicInteger();
        final StrictUtf8JsonLoader counted =
                new StrictUtf8JsonLoader(
                        document -> {
                            parserCalls.incrementAndGet();
                            return new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readTree(document);
                        });

        assertThrows(
                IllegalArgumentException.class,
                () -> counted.load("{}".getBytes(StandardCharsets.UTF_8), 1));
        assertEquals(0, parserCalls.get());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        counted.load(
                                "{\"a\":{\"b\":{\"c\":1}}}".getBytes(StandardCharsets.UTF_8),
                                64,
                                new StrictUtf8JsonLoader.JsonStructureLimits(2, 16, 16)));
        assertEquals(0, parserCalls.get());
    }

    @Test
    void acceptsTheExactByteBoundaryAndStrictUtf8() {
        final byte[] document = "{\"synthetic\":true}".getBytes(StandardCharsets.UTF_8);
        assertEquals(true, loader.load(document, document.length).get("synthetic").booleanValue());
    }

    @Test
    void rejectsMalformedUtf8DuplicateFieldsTrailingDocumentsAndEmptyDocuments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(new byte[] {(byte) 0xC3, 0x28}, 8));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        loader.load(
                                "{\"synthetic\":1,\"synthetic\":2}"
                                        .getBytes(StandardCharsets.UTF_8),
                                64));
        assertThrows(
                IllegalArgumentException.class,
                () -> loader.load("{} {}".getBytes(StandardCharsets.UTF_8), 16));
        assertThrows(IllegalArgumentException.class, () -> loader.load(new byte[0], 16));
        assertThrows(
                IllegalArgumentException.class,
                () -> loader.load("null".getBytes(StandardCharsets.UTF_8), 16));
    }

    @Test
    void rejectsInvalidLimitsAndResourcesOutsideTheV2012Allowlist() {
        assertThrows(
                IllegalArgumentException.class,
                () -> loader.load("{}".getBytes(StandardCharsets.UTF_8), 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        loader.loadResource(
                                StrictUtf8JsonLoaderTest.class,
                                "/contracts/graphql/users-first.json",
                                128));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        loader.loadResource(
                                StrictUtf8JsonLoaderTest.class,
                                "/contracts/v2-012/../graphql/users-first.json",
                                128));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        loader.loadResource(
                                StrictUtf8JsonLoaderTest.class,
                                "/contracts/v2-012//fixtures/coletas-6908.synthetic.json",
                                128));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        loader.loadResource(
                                StrictUtf8JsonLoaderTest.class,
                                "/contracts/v2-012/fixtures/not-present.synthetic.json",
                                128));
    }

    @Test
    void rejectsDepthPathAndNodeLimitsBeforeTreeMaterialization() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        loader.load(
                                "{\"a\":{\"b\":{\"c\":1}}}".getBytes(StandardCharsets.UTF_8),
                                64,
                                new StrictUtf8JsonLoader.JsonStructureLimits(2, 16, 16)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        loader.load(
                                "{\"a\":1,\"b\":2}".getBytes(StandardCharsets.UTF_8),
                                64,
                                new StrictUtf8JsonLoader.JsonStructureLimits(4, 1, 16)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        loader.load(
                                "[1,2]".getBytes(StandardCharsets.UTF_8),
                                64,
                                new StrictUtf8JsonLoader.JsonStructureLimits(4, 4, 2)));
    }
}
