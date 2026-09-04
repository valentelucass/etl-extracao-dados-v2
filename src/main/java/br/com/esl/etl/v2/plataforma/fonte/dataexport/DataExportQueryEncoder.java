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

        final List<String> parameters = new ArrayList<>();
        for (final Map.Entry<SearchPath, DataExportFilterValue> entry :
                request.filters().entrySet()) {
            addParameter(
                    parameters,
                    entry.getKey().asQueryParameterName(),
                    entry.getValue().formatForSource(sourceZone));
        }
        addParameter(parameters, "page", String.valueOf(request.page()));
        addParameter(parameters, "per", String.valueOf(request.pageSize()));
        if (!request.orderBy().isEmpty()) {
            addParameter(parameters, "order_by", String.join(", ", request.orderBy()));
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
