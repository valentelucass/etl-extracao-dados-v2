package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import java.util.List;
import java.util.Objects;

/** Baseline estrutural V2-025a de Usuários consumível sem fixture ou documento externo. */
public final class GraphQlFirstWaveContractCatalog {

    private static final SourceContractRelease USERS = createUsers();

    private GraphQlFirstWaveContractCatalog() {}

    public static SourceContractRelease release(final GraphQlReadOperation operation) {
        final GraphQlReadOperation required =
                Objects.requireNonNull(operation, "A operação GraphQL é obrigatória.");
        if (required != GraphQlReadOperation.USERS_SNAPSHOT) {
            throw new IllegalArgumentException("A operação não pertence à primeira onda.");
        }
        return USERS;
    }

    private static SourceContractRelease createUsers() {
        final GraphQlReadOperation operation = GraphQlReadOperation.USERS_SNAPSHOT;
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
                        List.of(
                                field(
                                        ContractResponse.FieldScope.RECORD,
                                        "/node",
                                        ContractResponse.Cardinality.OBJECT,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        ContractResponse.JsonType.OBJECT),
                                field(
                                        ContractResponse.FieldScope.RECORD,
                                        "/node/id",
                                        ContractResponse.Cardinality.SCALAR,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        ContractResponse.JsonType.STRING,
                                        ContractResponse.JsonType.INTEGER),
                                field(
                                        ContractResponse.FieldScope.RECORD,
                                        "/node/name",
                                        ContractResponse.Cardinality.SCALAR,
                                        ContractResponse.Presence.OPTIONAL,
                                        true,
                                        ContractResponse.JsonType.STRING),
                                field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data",
                                        ContractResponse.Cardinality.OBJECT,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        ContractResponse.JsonType.OBJECT),
                                field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data/individual",
                                        ContractResponse.Cardinality.OBJECT,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        ContractResponse.JsonType.OBJECT),
                                field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data/individual/edges",
                                        ContractResponse.Cardinality.ARRAY,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        ContractResponse.JsonType.ARRAY),
                                field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data/individual/pageInfo",
                                        ContractResponse.Cardinality.OBJECT,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        ContractResponse.JsonType.OBJECT),
                                field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data/individual/pageInfo/endCursorState",
                                        ContractResponse.Cardinality.SCALAR,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        ContractResponse.JsonType.STRING),
                                field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data/individual/pageInfo/hasNextPage",
                                        ContractResponse.Cardinality.SCALAR,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        ContractResponse.JsonType.BOOLEAN))));
    }

    private static ContractResponse.Field field(
            final ContractResponse.FieldScope scope,
            final String path,
            final ContractResponse.Cardinality cardinality,
            final ContractResponse.Presence presence,
            final boolean nullable,
            final ContractResponse.JsonType... types) {
        return new ContractResponse.Field(
                scope, path, cardinality, presence, nullable, List.of(types));
    }
}
