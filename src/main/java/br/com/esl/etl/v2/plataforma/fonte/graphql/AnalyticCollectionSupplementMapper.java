package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticCollectionSupplement;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Mapping of documented legacy GraphQL names in an explicitly synthetic lateral fixture. */
public final class AnalyticCollectionSupplementMapper {
    public AnalyticCollectionSupplement map(final JsonNode envelope) {
        if (envelope == null
                || !envelope.isObject()
                || !envelope.path("version")
                        .asText()
                        .equals("synthetic-analytic-collection-supplement-v1")
                || !envelope.path("provenance").asText().equals("FIXTURE_SINTETICA_EXPLICITA")
                || !envelope.path("data").isObject()) {
            throw new IllegalArgumentException("ANA_COLLECTION_SUPPLEMENT_ENVELOPE");
        }
        final var input = envelope.path("data");
        final var fields = JsonNodeFactory.instance.objectNode();
        field(fields, input, "requestHour", "requestHour");
        field(fields, input, "vehicleTypeId", "vehicleTypeId");
        field(fields, input, "customerName", "customer", "name");
        field(fields, input, "customerDocument", "customer", "cnpj");
        field(fields, input, "addressLine", "pickAddress", "line1");
        field(fields, input, "addressNumber", "pickAddress", "number");
        field(fields, input, "addressComplement", "pickAddress", "line2");
        field(fields, input, "branchSourceId", "corporation", "id");
        field(fields, input, "cancellationUserId", "cancellationUserId");
        field(fields, input, "destroyReason", "destroyReason");
        field(fields, input, "destroyUserId", "destroyUserId");
        field(fields, input, "statusUpdatedAt", "statusUpdatedAt");
        return new AnalyticCollectionSupplement(
                ExpansionFieldParser.time(fields, "requestHour"),
                ExpansionFieldParser.integer(fields, "vehicleTypeId"),
                ExpansionFieldParser.text(fields, "customerName"),
                ExpansionFieldParser.text(fields, "customerDocument"),
                ExpansionFieldParser.text(fields, "addressLine"),
                ExpansionFieldParser.text(fields, "addressNumber"),
                ExpansionFieldParser.text(fields, "addressComplement"),
                ExpansionFieldParser.integer(fields, "branchSourceId"),
                ExpansionFieldParser.integer(fields, "cancellationUserId"),
                ExpansionFieldParser.text(fields, "destroyReason"),
                ExpansionFieldParser.integer(fields, "destroyUserId"),
                ExpansionFieldParser.instant(fields, "statusUpdatedAt"));
    }

    private static void field(
            final ObjectNode target,
            final JsonNode input,
            final String name,
            final String... path) {
        JsonNode current = input;
        for (final String segment : path) {
            if (current == null || current.isMissingNode()) {
                return;
            }
            if (current.isNull()) {
                target.putNull(name);
                return;
            }
            if (!current.isObject()) {
                throw new IllegalArgumentException("ANA_COLLECTION_SUPPLEMENT_PARENT_TYPE");
            }
            current = current.get(segment);
        }
        if (current != null) {
            target.set(name, current);
        }
    }
}
