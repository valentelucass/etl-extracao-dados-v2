package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** Define se incompatibilidade comprovada pode tentar outro transporte HTTP. */
enum DataExportTransportFallbackPolicy {
    ALLOW_COMPATIBLE_FALLBACK,
    PREFERRED_TRANSPORT_ONLY
}
