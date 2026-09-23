package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

/** Offline protocol fixture only; physical SQL proof belongs to the opt-in IT. */
public final class RuntimeBootstrapTestFixture {
    private final RuntimeSyntheticJdbc jdbc;
    public int fetches;

    public RuntimeBootstrapTestFixture(final Instant now) {
        jdbc = new RuntimeSyntheticJdbc(now);
    }

    public DataSource dataSource() {
        return jdbc.dataSource();
    }

    public static DataExportHttpGatewayBundle boundGateways(
            final DataExportGateway data,
            final DataExportTemplateInfoGateway info,
            final DataExportContractObservationConfiguration observation) {
        return DataExportHttpGatewayBundle.contractBound(data, info, observation);
    }

    public static DataExportPageResponse observedPage(
            final java.util.List<com.fasterxml.jackson.databind.JsonNode> rows,
            final br.com.esl.etl.v2.plataforma.contrato.ContractResponse response,
            final br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits limits,
            final br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary boundary) {
        return DataExportPageResponse.observed(rows, response, limits, boundary);
    }

    public void loseAcknowledgement() {
        jdbc.loseApplyResponse = true;
    }

    public DataExportHttpGatewayBundle gateways(
            final DataExportContractObservationConfiguration observation) {
        final var template = observation.template();
        final var parsed =
                new DataExportTemplateInfoParser()
                        .parse(
                                read(
                                        "/contracts/"
                                                + template.templateId()
                                                + "-info.synthetic.json"));
        final var info =
                new DataExportTemplateInfo(
                        template, 200, Optional.empty(), true, parsed.fields(), parsed.filters());
        final var adapter =
                new DataExportContractAdapter(
                        observation.observationLimits(), observation.responsePathBoundary());
        return DataExportHttpGatewayBundle.contractBound(
                page -> {
                    fetches++;
                    final var root =
                            page.page() == 1
                                    ? read(
                                            "/contracts/first-wave/"
                                                    + template.templateId()
                                                    + "-per-3-page-1.synthetic.json")
                                    : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                                            .objectNode()
                                            .set(
                                                    "data",
                                                    com.fasterxml.jackson.databind.node
                                                            .JsonNodeFactory.instance
                                                            .arrayNode());
                    final var rows =
                            new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
                    root.get("data").forEach(rows::add);
                    return DataExportPageResponse.observed(
                            rows,
                            adapter.response(
                                    root, DataExportResponseForm.ENVELOPE_DATA_ARRAY, "/id"),
                            observation.observationLimits(),
                            observation.responsePathBoundary());
                },
                ignored -> info,
                observation);
    }

    private static com.fasterxml.jackson.databind.JsonNode read(final String name) {
        try (var input = RuntimeBootstrapTestFixture.class.getResourceAsStream(name)) {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(java.util.Objects.requireNonNull(input));
        } catch (final java.io.IOException failure) {
            throw new IllegalStateException("SYNTHETIC_FIXTURE", failure);
        }
    }
}
