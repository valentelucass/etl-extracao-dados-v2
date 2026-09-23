package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import org.junit.jupiter.api.Test;

class LocalizacaoCargaFieldReducerTest {

    @Test
    void absentPreservesTheKnownValueForEveryOneOfTheSeventeenPaths() {
        for (final String name : LocalizacaoCargaDataExportRecordMapper.ACCEPTED_FIELDS) {
            final String path = "/" + name;
            final var current = value(path, "known");
            final var absent =
                    new LocalizacaoCargaFieldValue<String>(
                            LocalizacaoCargaAttributePresence.ABSENT,
                            path,
                            null,
                            LocalizacaoCargaParseState.NOT_PRESENT,
                            null,
                            "DATAEXPORT_8656");

            assertSame(current, LocalizacaoCargaFieldReducer.resolve(current, absent, true));
            assertSame(
                    current,
                    LocalizacaoCargaFieldReducer.resolve(current, value(path, "new"), false));
        }
        assertEquals(17, LocalizacaoCargaDataExportRecordMapper.ACCEPTED_FIELDS.size());
    }

    @Test
    void explicitNullClearsOnlyForAnAcceptedNewerObservation() {
        final var current = value("/type", "known");
        final var explicitNull =
                new LocalizacaoCargaFieldValue<String>(
                        LocalizacaoCargaAttributePresence.NULL,
                        "/type",
                        null,
                        LocalizacaoCargaParseState.EXPLICIT_NULL,
                        null,
                        "DATAEXPORT_8656");

        assertNull(LocalizacaoCargaFieldReducer.resolve(null, explicitNull, true).typedValue());
        assertSame(current, LocalizacaoCargaFieldReducer.resolve(current, explicitNull, false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LocalizacaoCargaFieldReducer.resolve(
                                current, value("/service_type", "new"), true));
    }

    private static LocalizacaoCargaFieldValue<String> value(final String path, final String value) {
        return new LocalizacaoCargaFieldValue<>(
                LocalizacaoCargaAttributePresence.VALUE,
                path,
                '"' + value + '"',
                LocalizacaoCargaParseState.VALID,
                value,
                "DATAEXPORT_8656");
    }
}
