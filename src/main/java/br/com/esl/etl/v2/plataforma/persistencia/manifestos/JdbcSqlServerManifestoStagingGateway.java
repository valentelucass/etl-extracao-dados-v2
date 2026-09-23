package br.com.esl.etl.v2.plataforma.persistencia.manifestos;

import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoStagingGateway;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoStageBatch;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoStageRecord;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import javax.sql.DataSource;

/** JDBC batched para uma página 6399; o SQL conserva a deduplicação set-based. */
public final class JdbcSqlServerManifestoStagingGateway implements ManifestoStagingGateway {
    private static final String CALL =
            "{call stg.usp_stage_manifesto_observation(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private final DataSource dataSource;

    public JdbcSqlServerManifestoStagingGateway(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "O DataSource é obrigatório.");
    }

    @Override
    public void stage(final ManifestoStageBatch batch) {
        stage(batch, CancellationToken.none());
    }

    @Override
    public void stage(final ManifestoStageBatch batch, final CancellationToken cancellationToken) {
        final ManifestoStageBatch required =
                Objects.requireNonNull(batch, "O batch de Manifestos é obrigatório.");
        final CancellationToken cancellation =
                Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.");
        cancellation.throwIfCancellationRequested();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (CallableStatement statement = connection.prepareCall(CALL)) {
                for (int index = 0; index < required.size(); index++) {
                    bind(statement, required, required.recordAt(index));
                    statement.addBatch();
                }
                cancellation.throwIfCancellationRequested();
                statement.executeBatch();
                cancellation.throwIfCancellationRequested();
                connection.commit();
            } catch (final SQLException error) {
                rollback(connection, error);
                throw error;
            } catch (final RuntimeException error) {
                rollback(connection, error);
                throw error;
            }
        } catch (final SQLException error) {
            throw new StagingPersistenceException("o microbatch tipado de Manifestos", error);
        }
    }

    private static void bind(
            final CallableStatement statement,
            final ManifestoStageBatch batch,
            final ManifestoStageRecord record)
            throws SQLException {
        statement.setString(1, batch.executionId().toString());
        statement.setInt(2, batch.batchNumber());
        statement.setInt(3, record.inputOrdinal());
        nullableString(
                statement,
                4,
                record.sourceKey() == null ? null : record.sourceKey().storageValue());
        final boolean valid =
                record.disposition()
                        == br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoStageDisposition
                                .VALID;
        nullableString(statement, 5, valid ? record.payloadJson() : "{}");
        nullableString(statement, 6, valid ? record.fieldPresenceJson() : "{}");
        nullableString(statement, 7, valid ? rootJson(record) : null);
        nullableString(statement, 8, valid ? metricJson(record) : null);
        nullableString(statement, 9, valid ? record.relationCandidatesJson() : "{}");
        final var status = record.rootFields().get("mdfe_status");
        nullableString(statement, 10, status == null ? null : status.presence().name());
        nullableString(
                statement,
                11,
                status == null || status.canonicalJson() == null
                        ? null
                        : parse(status.canonicalJson()).asText());
        nullableString(
                statement, 12, record.pickSourceKey().map(key -> key.storageValue()).orElse(null));
        nullableString(statement, 13, record.mdfe().map(value -> value.key()).orElse(null));
        nullableString(
                statement, 14, record.mdfe().map(value -> value.number().toString()).orElse(null));
        nullableInstant(statement, 15, record.freshnessAtUtc());
        nullableString(statement, 16, valid ? record.freshnessOrigin().name() : null);
        nullableString(statement, 17, valid ? json(record.competence()) : null);
        statement.setString(18, record.disposition().name());
        nullableString(statement, 19, record.quarantineReasonCode());
        statement.setTimestamp(20, Timestamp.from(batch.observedAt()), utc());
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static String rootJson(final ManifestoStageRecord record) {
        final var root = JSON.createObjectNode();
        new java.util.TreeMap<>(record.rootFields())
                .forEach(
                        (name, field) -> {
                            if (field.presence()
                                    != br.com.esl.etl.v2.modulos.manifestos.domain
                                            .ManifestoAttributePresence.ABSENT) {
                                root.set(
                                        name,
                                        field.canonicalJson() == null
                                                ? JSON.nullNode()
                                                : parse(field.canonicalJson()));
                            }
                        });
        return root.toString();
    }

    private static String metricJson(final ManifestoStageRecord record) {
        final var root = JSON.createObjectNode();
        record.metrics().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            final var value = root.putObject(entry.getKey().name());
                            value.put("presence", entry.getValue().presence().name());
                            value.put("value", entry.getValue().canonicalNumber());
                        });
        return root.toString();
    }

    private static com.fasterxml.jackson.databind.JsonNode parse(final String value) {
        try {
            return JSON.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalArgumentException("MANIFESTO_CANONICAL_JSON_INVALID", failure);
        }
    }

    private static String json(final Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalArgumentException("MANIFESTO_CANONICAL_JSON_INVALID", failure);
        }
    }

    private static void nullableString(
            final CallableStatement statement, final int index, final String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NVARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void nullableInstant(
            final CallableStatement statement, final int index, final java.time.Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value), utc());
        }
    }

    private static void rollback(final Connection connection, final Exception original) {
        try {
            connection.rollback();
        } catch (final SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static Calendar utc() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }
}
