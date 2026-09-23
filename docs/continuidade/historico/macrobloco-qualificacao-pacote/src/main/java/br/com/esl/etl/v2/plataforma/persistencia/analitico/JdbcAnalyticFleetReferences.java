package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Bounded synthetic import into the existing, sealed OWNED_FLEET reference family. */
public final class JdbcAnalyticFleetReferences {
    private final ColetaTemporalLaboratorySession session;
    private final Clock clock;

    public JdbcAnalyticFleetReferences(
            final ColetaTemporalLaboratorySession session, final Clock clock) {
        this.session = Objects.requireNonNull(session);
        this.clock = Objects.requireNonNull(clock);
    }

    public JdbcAnalyticReferences.Receipt importPackaged(
            final UUID run, final int revision, final LocalDate start, final LocalDate end)
            throws SQLException {
        try (var input =
                getClass()
                        .getResourceAsStream(
                                "/analytic-laboratory/fleet-references.synthetic.json")) {
            if (input == null) {
                throw new IllegalStateException("ANA_FLEET_RESOURCE_MISSING");
            }
            return importFixture(run, revision, start, end, input.readNBytes(32769));
        } catch (final IOException failure) {
            throw new IllegalStateException("ANA_FLEET_RESOURCE", failure);
        }
    }

    public JdbcAnalyticReferences.Receipt importFixture(
            final UUID run,
            final int revision,
            final LocalDate start,
            final LocalDate end,
            final byte[] fixture)
            throws SQLException {
        Objects.requireNonNull(run);
        if (revision < 1
                || revision > 100000
                || start == null
                || end == null
                || !start.isBefore(end)
                || fixture == null
                || fixture.length == 0
                || fixture.length > 32768) {
            throw new IllegalArgumentException("ANA_FLEET_REFERENCE_BOUND");
        }
        final JsonNode data;
        try {
            data =
                    new ObjectMapper()
                            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readTree(fixture);
        } catch (final IOException failure) {
            throw new IllegalArgumentException("ANA_FLEET_REFERENCE_JSON", failure);
        }
        if (!"FIXTURE_SINTETICA_EXPLICITA".equals(data.path("provenance").asText())
                || !"analytic-fleet-references-v1".equals(data.path("version").asText())
                || !"sha256-utf16le-v1".equals(data.path("tokenScheme").asText())
                || !"ascii-trim-upper-v1".equals(data.path("normalization").asText())) {
            throw new IllegalArgumentException("ANA_FLEET_REFERENCE_PROVENANCE");
        }
        final var documents = table("document_token", "reason_code");
        for (final var row : bounded(data, "documents")) {
            documents.addRow(token(row, "documentToken"), required(row, "reason", 64));
        }
        final var aliases =
                table(
                        "classification_scope",
                        "alias_scope",
                        "normalized_alias",
                        "contract_class",
                        "reason_code");
        for (final var row : bounded(data, "aliases")) {
            aliases.addRow(
                    required(row, "scope", 32),
                    required(row, "aliasScope", 32),
                    required(row, "raw", 128),
                    required(row, "classification", 32),
                    required(row, "reason", 64));
        }
        final var matrix =
                table(
                        "vehicle_contract_class",
                        "driver_contract_class",
                        "classification_code",
                        "reason_code");
        for (final var row : bounded(data, "matrix")) {
            matrix.addRow(
                    required(row, "vehicle", 32),
                    required(row, "driver", 32),
                    required(row, "classification", 32),
                    required(row, "reason", 64));
        }
        final var exceptions =
                table(
                        "classification_scope",
                        "subject_token",
                        "required_vehicle_contract_class",
                        "classification_code");
        exceptions.addColumnMetadata("priority", Types.SMALLINT);
        exceptions.addColumnMetadata("reason_code", Types.NVARCHAR);
        for (final var row : bounded(data, "exceptions")) {
            final var priority = row.path("priority");
            if (!priority.isIntegralNumber()
                    || !priority.canConvertToInt()
                    || priority.intValue() < 1
                    || priority.intValue() > 1000) {
                throw new IllegalArgumentException("ANA_FLEET_REFERENCE_PRIORITY");
            }
            exceptions.addRow(
                    required(row, "scope", 32),
                    token(row, "subjectToken"),
                    optional(row, "requiredVehicle", 32),
                    required(row, "classification", 32),
                    priority.shortValue(),
                    required(row, "reason", 64));
        }
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var sql =
                    (SQLServerPreparedStatement)
                            connection.prepareStatement(
                                    "EXEC ref.usp_import_analytic_fleet ?,?,?,?,?,?,?,?,?,?,?")) {
                sql.setQueryTimeout(20);
                sql.setString(1, run.toString());
                sql.setInt(2, revision);
                sql.setObject(3, start);
                sql.setObject(4, end);
                sql.setString(5, fingerprint(fixture, start, end));
                sql.setStructured(6, "ref.analytic_fleet_document_batch", documents);
                sql.setStructured(7, "ref.analytic_fleet_alias_batch", aliases);
                sql.setStructured(8, "ref.analytic_fleet_matrix_batch", matrix);
                sql.setStructured(9, "ref.analytic_fleet_exception_batch", exceptions);
                sql.setTimestamp(10, Timestamp.from(clock.instant()));
                sql.setInt(11, fixture.length);
                try (var row = sql.executeQuery()) {
                    if (!row.next()) {
                        throw new SQLException("ANA_FLEET_REFERENCE_RECEIPT_MISSING");
                    }
                    final var receipt =
                            new JdbcAnalyticReferences.Receipt(row.getLong(1), row.getLong(2));
                    if (row.next()) {
                        throw new SQLException("ANA_FLEET_REFERENCE_RECEIPT_MULTIPLE");
                    }
                    return receipt;
                }
            } catch (final SQLException | RuntimeException failure) {
                try {
                    connection.rollback(savepoint);
                } catch (final SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }

    private static SQLServerDataTable table(final String... columns) throws SQLException {
        final var table = new SQLServerDataTable();
        for (final var column : columns) {
            table.addColumnMetadata(column, Types.NVARCHAR);
        }
        return table;
    }

    private static JsonNode bounded(final JsonNode data, final String field) {
        final var rows = data.path(field);
        if (!rows.isArray() || rows.size() > 100) {
            throw new IllegalArgumentException("ANA_FLEET_REFERENCE_BATCH_BOUND");
        }
        return rows;
    }

    private static String token(final JsonNode row, final String field) {
        final var value = required(row, field, 64);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("ANA_FLEET_REFERENCE_TOKEN");
        }
        return value;
    }

    private static String required(final JsonNode row, final String field, final int limit) {
        final var value = optional(row, field, limit);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ANA_FLEET_REFERENCE_REQUIRED");
        }
        return value;
    }

    private static String optional(final JsonNode row, final String field, final int limit) {
        final var value = row.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().length() > limit) {
            throw new IllegalArgumentException("ANA_FLEET_REFERENCE_TYPE_BOUND");
        }
        return value.textValue();
    }

    private static String fingerprint(
            final byte[] fixture, final LocalDate start, final LocalDate end) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            digest.update(fixture);
            digest.update(("|" + start + "|" + end).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException("ANA_FLEET_REFERENCE_DIGEST", failure);
        }
    }
}
