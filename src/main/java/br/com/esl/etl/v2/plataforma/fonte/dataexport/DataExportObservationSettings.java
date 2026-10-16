package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;

/** Opt-in for one finite observation; it never approves promotion or a full traversal. */
public record DataExportObservationSettings(
        boolean enabled, DataExportProperties source, int maximumRows) {
    public DataExportObservationSettings {
        if (!enabled || source == null) {
            throw new IllegalArgumentException("OBS_DISABLED");
        }
        if (!"https".equalsIgnoreCase(source.baseUri().getScheme())
                || source.preferredTransport() != DataExportTransport.GET_WITH_QUERY
                || source.retryPolicy().maxAttempts() != 1
                || maximumRows < 1
                || maximumRows > 100) {
            throw new IllegalArgumentException("OBS_POLICY_REFUSED");
        }
    }

    @Override
    public String toString() {
        return "DataExportObservationSettings[REDACTED]";
    }
}
