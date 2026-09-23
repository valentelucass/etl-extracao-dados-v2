package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/** Test-only access to the actual GraphQL wire parser and gate; never builds observations. */
public final class Bloco58GraphQlAccess {
    private Bloco58GraphQlAccess() {}

    public static JsonNode strict(final String document) throws JsonProcessingException {
        return GraphQlStrictJsonParser.readTree(document, 4096);
    }

    public static GraphQlPageResponse parse(
            final String document,
            final GraphQlPageRequest request,
            final GraphQlContractObservationConfiguration configuration) {
        return new GraphQlResponseParser(configuration).parse(document, request);
    }

    public static GraphQlGateway enforce(
            final GraphQlGateway gateway,
            final GraphQlContractObservationConfiguration configuration,
            final ContractRunGuard guard,
            final CancellationToken cancellation) {
        return GraphQlContractGate.enforce(
                GraphQlTestSupport.bound(cancellation, gateway),
                configuration,
                guard,
                cancellation);
    }
}
