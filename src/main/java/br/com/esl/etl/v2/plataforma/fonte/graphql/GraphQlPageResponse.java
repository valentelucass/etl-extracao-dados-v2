package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionContext;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Uma única página limitada; não oferece coleção nem materialização da travessia completa. */
public final class GraphQlPageResponse {

    private final ArrayNode nodes;
    private final boolean hasNextPage;
    private final Optional<GraphQlCursor> endCursor;
    private final Optional<ContractResponse> contractObservation;
    private final Optional<ContractObservationLimits> observationLimits;
    private final Optional<ImmutableFingerprint> observationBoundaryFingerprint;
    private final ContractExecutionContext validatedExecutionContext;
    private final long responseBytes;
    private final int distinctRootKeys;

    GraphQlPageResponse(
            final ArrayNode nodes,
            final boolean hasNextPage,
            final Optional<GraphQlCursor> endCursor) {
        this(
                nodes,
                hasNextPage,
                endCursor,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                null,
                0L);
    }

    private GraphQlPageResponse(
            final ArrayNode nodes,
            final boolean hasNextPage,
            final Optional<GraphQlCursor> endCursor,
            final Optional<ContractResponse> contractObservation,
            final Optional<ContractObservationLimits> observationLimits,
            final Optional<ImmutableFingerprint> observationBoundaryFingerprint,
            final ContractExecutionContext validatedExecutionContext,
            final long responseBytes) {
        this.nodes = Objects.requireNonNull(nodes, "Os nodes GraphQL são obrigatórios.").deepCopy();
        this.hasNextPage = hasNextPage;
        this.endCursor = Objects.requireNonNull(endCursor, "O cursor opcional é obrigatório.");
        this.contractObservation =
                Objects.requireNonNull(contractObservation, "A observação opcional é obrigatória.");
        this.observationLimits =
                Objects.requireNonNull(observationLimits, "Os limites opcionais são obrigatórios.");
        this.observationBoundaryFingerprint =
                Objects.requireNonNull(
                        observationBoundaryFingerprint,
                        "O fingerprint opcional da fronteira é obrigatório.");
        this.validatedExecutionContext = validatedExecutionContext;
        if (responseBytes < 0) {
            throw new IllegalArgumentException("O tamanho observado da resposta é inválido.");
        }
        this.responseBytes = responseBytes;
        this.distinctRootKeys = countDistinctRootKeys(this.nodes);
        final boolean completeObservation =
                this.contractObservation.isPresent()
                        && this.observationLimits.isPresent()
                        && this.observationBoundaryFingerprint.isPresent();
        final boolean absentObservation =
                this.contractObservation.isEmpty()
                        && this.observationLimits.isEmpty()
                        && this.observationBoundaryFingerprint.isEmpty();
        if (!completeObservation && !absentObservation) {
            throw new IllegalArgumentException("A evidência contratual GraphQL está incompleta.");
        }
    }

    public int nodeCount() {
        return nodes.size();
    }

    public boolean hasNextPage() {
        return hasNextPage;
    }

    public Optional<GraphQlCursor> endCursor() {
        return endCursor;
    }

    public Optional<ContractResponse> contractObservation() {
        return contractObservation;
    }

    public Optional<ContractObservationLimits> observationLimits() {
        return observationLimits;
    }

    public Optional<ImmutableFingerprint> observationBoundaryFingerprint() {
        return observationBoundaryFingerprint;
    }

    public long responseBytes() {
        return responseBytes;
    }

    public int distinctRootKeys() {
        return distinctRootKeys;
    }

    /** Entrega cópias dos nodes de uma única página, uma por vez. */
    public void forEachNode(final Consumer<JsonNode> consumer) {
        Objects.requireNonNull(consumer, "O consumidor de nodes é obrigatório.");
        for (final JsonNode node : nodes) {
            consumer.accept(node.deepCopy());
        }
    }

    JsonNode nodeContainer() {
        return nodes;
    }

    GraphQlPageResponse withObservation(
            final ContractResponse observation,
            final ContractObservationLimits limits,
            final ImmutableFingerprint boundaryFingerprint) {
        return new GraphQlPageResponse(
                nodes,
                hasNextPage,
                endCursor,
                Optional.of(Objects.requireNonNull(observation, "A observação é obrigatória.")),
                Optional.of(Objects.requireNonNull(limits, "Os limites são obrigatórios.")),
                Optional.of(
                        Objects.requireNonNull(
                                boundaryFingerprint, "O fingerprint da fronteira é obrigatório.")),
                validatedExecutionContext,
                responseBytes);
    }

    GraphQlPageResponse withResponseBytes(final long observedResponseBytes) {
        return new GraphQlPageResponse(
                nodes,
                hasNextPage,
                endCursor,
                contractObservation,
                observationLimits,
                observationBoundaryFingerprint,
                validatedExecutionContext,
                observedResponseBytes);
    }

    GraphQlPageResponse validatedBy(final ContractExecutionContext executionContext) {
        if (contractObservation.isEmpty()) {
            throw new IllegalStateException(
                    "A validação GraphQL exige evidência contratual observada.");
        }
        return new GraphQlPageResponse(
                nodes,
                hasNextPage,
                endCursor,
                contractObservation,
                observationLimits,
                observationBoundaryFingerprint,
                Objects.requireNonNull(
                        executionContext, "O contexto contratual validado é obrigatório."),
                responseBytes);
    }

    boolean validatedFor(final ContractExecutionContext executionContext) {
        return validatedExecutionContext != null
                && validatedExecutionContext.sameOccurrenceAs(executionContext);
    }

    @Override
    public String toString() {
        return "GraphQlPageResponse[nodeCount="
                + nodes.size()
                + ", hasNextPage="
                + hasNextPage
                + ", endCursor=<redacted>]";
    }

    private static int countDistinctRootKeys(final ArrayNode boundedNodes) {
        final Set<String> identities = new HashSet<>(boundedNodes.size());
        for (final JsonNode node : boundedNodes) {
            final JsonNode id = node.get("id");
            if (id == null) {
                continue;
            }
            if (id.isIntegralNumber()) {
                identities.add("INTEGER:" + id.bigIntegerValue());
            } else if (id.isTextual()) {
                identities.add("STRING:" + id.textValue());
            }
        }
        return identities.size();
    }
}
