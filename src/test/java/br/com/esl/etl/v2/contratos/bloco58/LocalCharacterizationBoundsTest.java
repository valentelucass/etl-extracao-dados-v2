package br.com.esl.etl.v2.contratos.bloco58;

import static br.com.esl.etl.v2.contratos.bloco58.CharacterizationFixtures.input;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalCharacterizationBoundsTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "{} {}", "{\"data\":[],\"data\":[]}", "{", "null"})
    void strictIngress(final String text) {
        assertThrows(IllegalArgumentException.class, () -> LocalCharacterization.read(input(text)));
    }

    @Test
    void limitsApplyBeforeMaterialization() throws Exception {
        final String exact = "{\"v\":\"" + "s".repeat(65528) + "\"}";
        assertEquals(65536, LocalCharacterization.read(input(exact)).length());
        assertThrows(IOException.class, () -> LocalCharacterization.read(input(exact + " ")));
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalCharacterization.read(input("[".repeat(17) + "0" + "]".repeat(17))));
        final String fields =
                IntStream.range(0, 257)
                        .mapToObj(i -> "\"f" + i + "\":0")
                        .collect(Collectors.joining(",", "{", "}"));
        assertThrows(
                IllegalArgumentException.class, () -> LocalCharacterization.read(input(fields)));
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalCharacterization.read(input("[" + "0,".repeat(4095) + "0]")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LocalCharacterization.read(
                                new ByteArrayInputStream(new byte[] {(byte) 0xc3, 0x28})));
    }

    @Test
    void missingFailureAndNonProgressingInputStop() {
        assertThrows(IOException.class, () -> LocalCharacterization.read(null));
        assertThrows(
                IOException.class,
                () ->
                        LocalCharacterization.read(
                                new InputStream() {
                                    @Override
                                    public int read() throws IOException {
                                        throw new IOException("SYNTHETIC");
                                    }
                                }));
        assertThrows(
                IOException.class,
                () ->
                        LocalCharacterization.read(
                                new InputStream() {
                                    @Override
                                    public int read() {
                                        return 0;
                                    }

                                    @Override
                                    public int read(byte[] bytes, int offset, int length) {
                                        return 0;
                                    }
                                }));
    }

    @Test
    void caseLimitsRefuseBeforeUnboundedSource() {
        final LocalCharacterization.Pages forbidden =
                page -> {
                    throw new AssertionError("NO_READ");
                };
        final var pages = ColetasCharacterization.mapper(forbidden, 101, (page, row) -> null);
        pages.expect(LocalCharacterization.Layer.INPUT, 0, false);
        assertTrue(pages.matches());
        final var rows =
                ColetasCharacterization.mapper(
                        page -> input("{\"data\":[" + "{},".repeat(1000) + "{}]}"),
                        1,
                        (page, row) -> null);
        rows.expect(LocalCharacterization.Layer.INPUT, 0, false);
        assertTrue(rows.matches());
        assertEquals(0, rows.mappedRows());
    }
}
