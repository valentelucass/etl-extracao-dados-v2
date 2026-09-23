package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaTemporalReferenceStore;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStatus;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalIdentityBinding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalObservation;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** Adaptador opt-in de observações próprias; os procedimentos antigos não são chamados. */
public final class JdbcSqlServerColetaTemporalGateway implements ColetaTemporalReferenceStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcSqlServerColetaTemporalGateway(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "O DataSource é obrigatório.");
    }

    @Override
    public void stage(
            final ColetaTemporalObservation observation, final CancellationToken cancellation) {
        final String payload = observationJson(Objects.requireNonNull(observation));
        transact(
                "{call stg.usp_stage_coleta_temporal(?)}",
                cancellation,
                statement -> {
                    statement.setNString(1, payload);
                    statement.executeUpdate();
                    return null;
                });
    }

    @Override
    public void complete(
            final GraphQlExtractionResult result, final CancellationToken cancellation) {
        Objects.requireNonNull(result, "O resultado é obrigatório.");
        if (result.operation() != GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE
                || result.traversalVerification().provesCoverageOrSnapshot()) {
            throw new IllegalArgumentException("A travessia temporal é incompatível.");
        }
        transact(
                "{call stg.usp_complete_coleta_temporal(?,?,?)}",
                cancellation,
                statement -> {
                    statement.setString(1, result.executionId().toString());
                    statement.setInt(2, result.pagesFetched());
                    statement.setLong(3, result.nodesDelivered());
                    statement.executeUpdate();
                    return null;
                });
    }

    @Override
    public void bind(
            final ColetaTemporalIdentityBinding binding, final CancellationToken cancellation) {
        Objects.requireNonNull(binding, "O binding é obrigatório.");
        transact(
                "{call stg.usp_bind_coleta_temporal(?,?,?,?,?,?,?,?,?)}",
                cancellation,
                statement -> {
                    statement.setString(1, binding.dataExportExecutionId().toString());
                    statement.setString(2, binding.referenceExecutionId().toString());
                    statement.setNString(3, binding.dataExportIdentity().sourceInstance());
                    statement.setNString(4, binding.dataExportIdentity().tenantScope());
                    statement.setNString(
                            5, binding.dataExportIdentity().sourceKey().storageValue());
                    statement.setNString(6, binding.referenceIdentity().sourceKey().storageValue());
                    statement.setString(7, binding.requestDate().toString());
                    statement.setNString(8, binding.identityEvidence().version());
                    statement.setString(9, binding.identityEvidence().sha256());
                    statement.executeUpdate();
                    return null;
                });
    }

    @Override
    public Qualification qualify(
            final UUID dataExportExecutionId,
            final UUID referenceExecutionId,
            final CancellationToken cancellation) {
        Objects.requireNonNull(dataExportExecutionId, "A execução Data Export é obrigatória.");
        Objects.requireNonNull(referenceExecutionId, "A referência é obrigatória.");
        return transact(
                "{call recon.usp_qualify_coleta_temporal(?,?)}",
                cancellation,
                statement -> {
                    statement.setString(1, dataExportExecutionId.toString());
                    statement.setString(2, referenceExecutionId.toString());
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next()) {
                            throw new SQLException("Resumo temporal ausente.");
                        }
                        final long considered = requiredCount(rows, "considered_rows");
                        final long candidates = requiredCount(rows, "candidate_rows");
                        final long blocked = requiredCount(rows, "blocked_rows");
                        if (rows.next()) {
                            throw new SQLException("Resumo temporal duplicado.");
                        }
                        return new Qualification(considered, candidates, blocked);
                    }
                });
    }

    private static long requiredCount(final java.sql.ResultSet rows, final String name)
            throws SQLException {
        final long value = rows.getLong(name);
        if (rows.wasNull()) {
            throw new SQLException("Contagem temporal ausente.");
        }
        return value;
    }

    private <T> T transact(
            final String sql, final CancellationToken cancellation, final Action<T> action) {
        Objects.requireNonNull(cancellation, "O cancelamento é obrigatório.");
        cancellation.throwIfCancellationRequested();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (CallableStatement statement = connection.prepareCall(sql)) {
                statement.setQueryTimeout(20);
                cancellation.throwIfCancellationRequested();
                final T result = action.execute(statement);
                cancellation.throwIfCancellationRequested();
                connection.commit();
                return result;
            } catch (final SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (final SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (final SQLException failure) {
            throw new StagingPersistenceException("Falha no staging temporal de Coletas.", failure);
        }
    }

    private static String observationJson(final ColetaTemporalObservation observation) {
        final var operation = GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE;
        if (!operation.contractVersion().equals(observation.contractVersion())
                || !operation
                        .approvedDocument()
                        .fingerprint()
                        .equals(observation.selectionFingerprint())
                || !Objects.equals(
                        parseInstant(observation.statusUpdatedAt().text()),
                        observation.statusAtUtc())) {
            throw new IllegalArgumentException("Contrato ou instante temporal incompatível.");
        }
        final var node = JSON.createObjectNode();
        node.put("executionId", observation.executionId().toString());
        node.put("source", observation.identity().sourceInstance());
        node.put("tenant", observation.identity().tenantScope());
        node.put("queryDate", observation.queryDate().toString());
        node.put("contractVersion", observation.contractVersion());
        node.put("selectionVersion", observation.selectionFingerprint().version());
        node.put("selectionSha256", observation.selectionFingerprint().sha256());
        node.put("sourceKey", observation.identity().sourceKey().storageValue());
        node.put("page", observation.pageNumber());
        node.put("ordinal", observation.inputOrdinal());
        node.put("observedAt", observation.observedAt().toString());
        field(node, "status", observation.status());
        field(node, "statusUpdatedAt", observation.statusUpdatedAt());
        field(node, "requestDate", observation.requestDate());
        node.put(
                "statusCode",
                ColetaStatus.normalizeKnown(observation.status().text()).orElse(null));
        if (observation.statusAtUtc() == null) {
            node.putNull("epochSecond");
            node.putNull("nano");
        } else {
            node.put("epochSecond", observation.statusAtUtc().getEpochSecond());
            node.put("nano", observation.statusAtUtc().getNano());
        }
        final String payload = node.toString();
        if (payload.getBytes(StandardCharsets.UTF_8).length > 65_536) {
            throw new IllegalArgumentException("Observação temporal excede 64KiB.");
        }
        return payload;
    }

    private static void field(
            final ObjectNode node, final String name, final ColetaTemporalObservation.Field field) {
        if (field.rawJson() != null) {
            try {
                final var raw =
                        JSON.reader()
                                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                                .readTree(field.rawJson());
                if (raw == null || raw.isNull() || !Objects.equals(raw.textValue(), field.text())) {
                    throw new IllegalArgumentException("Valor bruto e texto temporal divergem.");
                }
            } catch (final JsonProcessingException failure) {
                throw new IllegalArgumentException("JSON bruto temporal inválido.", failure);
            }
        }
        node.put(name + "Presence", field.presence().name());
        node.put(name + "RawJson", field.rawJson());
        node.put(name + "Text", field.text());
    }

    private static Instant parseInstant(final String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (final DateTimeParseException failure) {
            return null;
        }
    }

    @FunctionalInterface
    private interface Action<T> {
        T execute(CallableStatement statement) throws SQLException;
    }
}
