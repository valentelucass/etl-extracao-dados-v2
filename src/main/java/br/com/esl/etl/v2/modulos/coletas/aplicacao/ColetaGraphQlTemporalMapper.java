package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalObservation;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;

/** Converte um nó sem coerção de ID nem fallback civil para o evento GraphQL (ADR0045). */
public final class ColetaGraphQlTemporalMapper {
    public ColetaTemporalObservation map(
            final UUID executionId,
            final String sourceInstance,
            final String tenantScope,
            final LocalDate queryDate,
            final int pageNumber,
            final int ordinal,
            final Instant observedAt,
            final JsonNode node) {
        Objects.requireNonNull(node, "O nó é obrigatório.");
        if (!node.isObject()) {
            throw new IllegalArgumentException("A referência exige um objeto.");
        }
        final var identity =
                new ScopedSourceIdentity(
                        sourceInstance,
                        tenantScope,
                        FirstWaveIdentityContract.Entity.COLETAS,
                        ScopedSourceIdentity.SourceKey.fromJson(
                                FirstWaveIdentityContract.WireTypePolicy
                                        .STRING_OR_INTEGER_TYPE_TAGGED_DISTINCT,
                                node.get("id")));
        final var timestamp = field(node, "statusUpdatedAt");
        final var operation = GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE;
        return new ColetaTemporalObservation(
                executionId,
                identity,
                queryDate,
                operation.contractVersion(),
                operation.approvedDocument().fingerprint(),
                pageNumber,
                ordinal,
                observedAt,
                field(node, "status"),
                timestamp,
                field(node, "requestDate"),
                parseOffset(timestamp.text()));
    }

    private static Instant parseOffset(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (final DateTimeParseException ignored) {
            // Este contrato exige offset; captura, updatedAt e data civil não são substitutos.
            return null;
        }
    }

    private static ColetaTemporalObservation.Field field(final JsonNode node, final String name) {
        final JsonNode value = node.get(name);
        if (value == null) {
            return new ColetaTemporalObservation.Field(ColetaAttributePresence.ABSENT, null, null);
        }
        if (value.isNull()) {
            return new ColetaTemporalObservation.Field(ColetaAttributePresence.NULL, null, null);
        }
        return new ColetaTemporalObservation.Field(
                ColetaAttributePresence.VALUE,
                value.toString(),
                value.isTextual() ? value.textValue() : null);
    }
}
