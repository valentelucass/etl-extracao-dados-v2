package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class GraphQlTestSupport {

    static final ImmutableFingerprint RUNTIME_FINGERPRINT =
            new ImmutableFingerprint("graphql-runtime-v1", "a".repeat(64));
    static final ContractObservationLimits LIMITS = ContractObservationLimits.runtimeDefaults();
    static final ObjectMapper MAPPER = new ObjectMapper();

    private GraphQlTestSupport() {}

    static GraphQlPageRequest request(final GraphQlReadOperation operation) {
        final GraphQlQueryParameters parameters =
                switch (operation) {
                    case USERS_SNAPSHOT -> GraphQlQueryParameters.enabledUsers();
                    case PICKS_TRANSITIONAL_SIDECAR ->
                            GraphQlQueryParameters.picksForDate(LocalDate.of(2026, 8, 30));
                    case PICKS_TEMPORAL_REFERENCE ->
                            GraphQlQueryParameters.picksTemporalForDate(LocalDate.of(2026, 8, 30));
                    case FREIGHTS_TRANSITIONAL_SIDECAR ->
                            GraphQlQueryParameters.freightsForWindow(
                                    LocalDate.of(2026, 8, 29), LocalDate.of(2026, 8, 30));
                };
        return GraphQlPageRequest.initial(
                operation, parameters, Math.min(20, operation.maximumPageSize()));
    }

    static GraphQlPageResponse usersPage(
            final boolean hasNextPage, final String cursor, final String... nodeDocuments) {
        return page(GraphQlReadOperation.USERS_SNAPSHOT, hasNextPage, cursor, nodeDocuments);
    }

    static GraphQlPageResponse page(
            final GraphQlReadOperation operation,
            final boolean hasNextPage,
            final String cursor,
            final String... nodeDocuments) {
        final ArrayNode nodes = MAPPER.createArrayNode();
        for (final String document : nodeDocuments) {
            try {
                nodes.add(MAPPER.readTree(document));
            } catch (final JsonProcessingException exception) {
                throw new AssertionError(exception);
            }
        }
        return new GraphQlPageResponse(
                nodes,
                hasNextPage,
                cursor == null ? Optional.empty() : Optional.of(GraphQlCursor.observed(cursor)));
    }

    static Fixture usersFixture() {
        return fixture(
                GraphQlReadOperation.USERS_SNAPSHOT,
                usersPage(false, null, "{\"id\":930001,\"name\":\"Synthetic\"}"));
    }

    static Fixture fixture(
            final GraphQlReadOperation operation, final GraphQlPageResponse baselinePage) {
        return fixture(operation, baselinePage, RUNTIME_FINGERPRINT);
    }

    static Fixture fixture(
            final GraphQlReadOperation operation,
            final GraphQlPageResponse baselinePage,
            final ImmutableFingerprint runtimeFingerprint) {
        final GraphQlContractAdapter synthetic =
                GraphQlContractAdapter.forSyntheticFixtures(LIMITS);
        final SourceContractRelease release =
                operation == GraphQlReadOperation.USERS_SNAPSHOT
                        ? GraphQlFirstWaveContractCatalog.release(operation)
                        : operation == GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE
                                ? GraphQlColetasTemporalContractCatalog.release()
                                : SourceContractRelease.create(
                                        ContractSourceKind.GRAPHQL,
                                        operation.documentReference(),
                                        operation.contractVersion(),
                                        GraphQlContractAdapter.metadata(operation),
                                        synthetic.response(operation, baselinePage));
        final ContractCompatibilityPolicy policy =
                ContractCompatibilityPolicy.create(
                        "graphql-policy-v1", release.contractFingerprint(), List.of());
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(), release, policy, runtimeFingerprint, LIMITS);
        final ContractRunGuard guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        alert -> {});
        final ContractResponsePathBoundary boundary = guard.responsePathBoundary();
        final GraphQlContractObservationConfiguration configuration =
                new GraphQlContractObservationConfiguration(
                        operation, LIMITS, boundary, runtimeFingerprint);
        return new Fixture(binding, release, guard, configuration);
    }

    static GraphQlPageResponse observed(final Fixture fixture, final GraphQlPageResponse page) {
        final GraphQlContractAdapter adapter =
                new GraphQlContractAdapter(LIMITS, fixture.configuration().responsePathBoundary());
        return page.withObservation(
                adapter.response(fixture.configuration().operation(), page),
                LIMITS,
                fixture.configuration().responsePathBoundary().fingerprint());
    }

    static GraphQlGateway bound(
            final CancellationToken cancellationToken, final GraphQlGateway delegate) {
        final CancellationToken requiredToken =
                Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.");
        final GraphQlGateway requiredDelegate =
                Objects.requireNonNull(delegate, "O gateway é obrigatório.");
        return new GraphQlGateway() {
            @Override
            public GraphQlPageResponse fetch(final GraphQlPageRequest request) {
                return requiredDelegate.fetch(request);
            }

            @Override
            public void verifyCancellationToken(final CancellationToken candidate) {
                if (requiredToken
                        != Objects.requireNonNull(candidate, "O cancelamento é obrigatório.")) {
                    throw new IllegalArgumentException(
                            "A capability sintética não corresponde ao gateway.");
                }
            }
        };
    }

    static String usersEnvelope(
            final String edges, final boolean hasNextPage, final String rawCursor) {
        return "{\"data\":{\"individual\":{\"edges\":"
                + edges
                + ",\"pageInfo\":{\"hasNextPage\":"
                + hasNextPage
                + ",\"endCursor\":"
                + rawCursor
                + "}}}}";
    }

    record Fixture(
            ContractExecutionBinding binding,
            SourceContractRelease release,
            ContractRunGuard guard,
            GraphQlContractObservationConfiguration configuration) {}
}
