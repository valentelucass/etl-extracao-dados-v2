package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponseProfiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Produz metadata e shape sanitizados a partir de documento e página já limitados. */
public final class GraphQlContractAdapter {

    public static final String APPROVED_KEY_PATH = "/node/id";

    private final ContractObservationLimits limits;
    private final ContractResponseProfiler responseProfiler;

    public GraphQlContractAdapter(
            final ContractObservationLimits limits,
            final ContractResponsePathBoundary pathBoundary) {
        this(limits, ContractResponseProfiler.forRuntime(limits, pathBoundary));
    }

    private GraphQlContractAdapter(
            final ContractObservationLimits limits,
            final ContractResponseProfiler responseProfiler) {
        this.limits = Objects.requireNonNull(limits, "Os limites de observação são obrigatórios.");
        this.responseProfiler =
                Objects.requireNonNull(responseProfiler, "O profiler de resposta é obrigatório.");
    }

    public static GraphQlContractAdapter forSyntheticFixtures(
            final ContractObservationLimits limits) {
        return new GraphQlContractAdapter(
                limits, ContractResponseProfiler.forSyntheticFixtures(limits));
    }

    public static ContractMetadata metadata(final GraphQlReadOperation operation) {
        final GraphQlReadOperation required =
                Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.");
        GraphQlTransitionalFieldCatalog.validate();
        final List<ContractMetadata.Element> elements =
                new ArrayList<>(required.selectionCount() + 3);
        required.forEachSelection(
                path ->
                        elements.add(
                                new ContractMetadata.Element(
                                        ContractMetadata.ElementKind.GRAPHQL_SELECTION,
                                        path,
                                        ContractMetadata.DeclaredType.UNDECLARED)));
        elements.add(argument(required, "params", required.parametersType()));
        elements.add(argument(required, "after", "String"));
        elements.add(argument(required, "first", "Int!"));
        return new ContractMetadata(elements, Optional.of(required.approvedDocument()));
    }

    public ContractResponse response(
            final GraphQlReadOperation operation, final GraphQlPageResponse page) {
        final GraphQlReadOperation requiredOperation =
                Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.");
        final GraphQlPageResponse requiredPage =
                Objects.requireNonNull(page, "A página GraphQL é obrigatória.");
        final ObjectNode envelope = JsonNodeFactory.instance.objectNode();
        final ObjectNode data = envelope.putObject("data");
        final ObjectNode connection = data.putObject(requiredOperation.connectionName());
        final ArrayNode edges = connection.putArray("edges");
        for (final JsonNode node : requiredPage.nodeContainer()) {
            edges.addObject().set("node", node);
        }
        final ObjectNode pageInfo = connection.putObject("pageInfo");
        pageInfo.put("hasNextPage", requiredPage.hasNextPage());
        pageInfo.put("endCursorState", requiredPage.endCursor().isPresent() ? "PRESENT" : "ABSENT");
        return responseProfiler.profileWithEnvelope(
                edges,
                recordRoot(requiredOperation),
                ContractResponse.Cardinality.ARRAY,
                APPROVED_KEY_PATH,
                envelope);
    }

    public ContractObservationLimits limits() {
        return limits;
    }

    public static String recordRoot(final GraphQlReadOperation operation) {
        return "/data/"
                + Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.")
                        .connectionName()
                + "/edges";
    }

    private static ContractMetadata.Element argument(
            final GraphQlReadOperation operation, final String name, final String type) {
        return ContractMetadata.Element.fromDeclaredType(
                ContractMetadata.ElementKind.GRAPHQL_ARGUMENT,
                "/" + operation.connectionName() + "/" + name,
                Optional.of(type));
    }
}
