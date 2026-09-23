package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import java.util.ArrayList;
import java.util.List;

/** Contrato local explícito; nullable/ausente não recebe equivalência temporal. */
public final class GraphQlColetasTemporalContractCatalog {
    private static final SourceContractRelease RELEASE = create();

    private GraphQlColetasTemporalContractCatalog() {}

    public static SourceContractRelease release() {
        return RELEASE;
    }

    private static SourceContractRelease create() {
        final var operation = GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE;
        final List<ContractResponse.Field> fields = new ArrayList<>();
        fields.add(
                field(
                        ContractResponse.FieldScope.RECORD,
                        "/node",
                        ContractResponse.Cardinality.OBJECT,
                        true,
                        ContractResponse.JsonType.OBJECT));
        fields.add(
                field(
                        ContractResponse.FieldScope.RECORD,
                        "/node/id",
                        ContractResponse.Cardinality.SCALAR,
                        true,
                        ContractResponse.JsonType.STRING,
                        ContractResponse.JsonType.INTEGER));
        for (final String name : List.of("status", "statusUpdatedAt", "requestDate")) {
            fields.add(
                    field(
                            ContractResponse.FieldScope.RECORD,
                            "/node/" + name,
                            ContractResponse.Cardinality.SCALAR,
                            false,
                            ContractResponse.JsonType.STRING));
        }
        for (final String path : List.of("/data", "/data/pick", "/data/pick/pageInfo")) {
            fields.add(
                    field(
                            ContractResponse.FieldScope.ENVELOPE,
                            path,
                            ContractResponse.Cardinality.OBJECT,
                            true,
                            ContractResponse.JsonType.OBJECT));
        }
        fields.add(
                field(
                        ContractResponse.FieldScope.ENVELOPE,
                        "/data/pick/edges",
                        ContractResponse.Cardinality.ARRAY,
                        true,
                        ContractResponse.JsonType.ARRAY));
        fields.add(
                field(
                        ContractResponse.FieldScope.ENVELOPE,
                        "/data/pick/pageInfo/hasNextPage",
                        ContractResponse.Cardinality.SCALAR,
                        true,
                        ContractResponse.JsonType.BOOLEAN));
        fields.add(
                field(
                        ContractResponse.FieldScope.ENVELOPE,
                        "/data/pick/pageInfo/endCursorState",
                        ContractResponse.Cardinality.SCALAR,
                        true,
                        ContractResponse.JsonType.STRING));
        return SourceContractRelease.create(
                ContractSourceKind.GRAPHQL,
                operation.documentReference(),
                operation.contractVersion(),
                GraphQlContractAdapter.metadata(operation),
                new ContractResponse(
                        GraphQlContractAdapter.recordRoot(operation),
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        GraphQlContractAdapter.APPROVED_KEY_PATH,
                        fields));
    }

    private static ContractResponse.Field field(
            final ContractResponse.FieldScope scope,
            final String path,
            final ContractResponse.Cardinality cardinality,
            final boolean required,
            final ContractResponse.JsonType... types) {
        return new ContractResponse.Field(
                scope,
                path,
                cardinality,
                required ? ContractResponse.Presence.REQUIRED : ContractResponse.Presence.OPTIONAL,
                !required,
                List.of(types));
    }
}
