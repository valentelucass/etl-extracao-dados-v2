package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Converte uma requisição tipada para o fallback GET com filtros na query string. */
public final class DataExportQueryEncoder {

    public URI appendQuery(
            final URI endpoint, final DataExportPageRequest request, final ZoneId sourceZone) {
        Objects.requireNonNull(endpoint, "O endpoint é obrigatório.");
        Objects.requireNonNull(request, "A requisição é obrigatória.");
        Objects.requireNonNull(sourceZone, "O timezone da fonte é obrigatório.");
        return appendQuery(
                endpoint,
                request.filters(),
                request.page(),
                request.pageSize(),
                request.orderBy(),
                sourceZone);
    }

    /**
     * Serialization only; the caller owns the separately qualified request/profile and admission.
     */
    public URI appendQuery(
            final URI endpoint,
            final Map<SearchPath, DataExportFilterValue> filters,
            final int page,
            final int per,
            final List<String> orderBy,
            final ZoneId sourceZone) {
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(filters);
        Objects.requireNonNull(orderBy);
        Objects.requireNonNull(sourceZone);
        if (page < 1 || per < 1 || filters.isEmpty()) {
            throw new IllegalArgumentException("EXPLICIT_QUERY_SERIALIZATION_INPUT");
        }

        final List<String> parameters = new ArrayList<>();
        for (final Map.Entry<SearchPath, DataExportFilterValue> entry : filters.entrySet()) {
            addParameter(
                    parameters,
                    entry.getKey().asQueryParameterName(),
                    entry.getValue().formatForSource(sourceZone));
        }
        addParameter(parameters, "page", String.valueOf(page));
        addParameter(parameters, "per", String.valueOf(per));
        if (!orderBy.isEmpty()) {
            addParameter(parameters, "order_by", String.join(", ", orderBy));
        }

        final String separator = endpoint.getRawQuery() == null ? "?" : "&";
        return URI.create(endpoint + separator + String.join("&", parameters));
    }

    private void addParameter(
            final List<String> parameters, final String name, final String value) {
        parameters.add(encode(name) + "=" + encode(value));
    }

    private String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
