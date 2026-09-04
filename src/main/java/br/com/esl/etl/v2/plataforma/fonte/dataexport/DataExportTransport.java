package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.Locale;

/** Formas de transporte aceitas pelo endpoint Data Export. */
public enum DataExportTransport {
    GET_WITH_BODY,
    GET_WITH_QUERY,
    POST_JSON;

    public static DataExportTransport parse(final String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Transporte Data Export inválido: " + value, exception);
        }
    }
}
