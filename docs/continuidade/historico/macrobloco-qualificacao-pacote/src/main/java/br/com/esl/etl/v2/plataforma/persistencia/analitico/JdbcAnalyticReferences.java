package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Small, explicit reference releases use the existing registry, receipt and immutable selection.
 */
public final class JdbcAnalyticReferences {
    private final DataSource source;
    private final Clock clock;

    public JdbcAnalyticReferences(final DataSource source, final Clock clock) {
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
    }

    public Receipt importPackaged(
            final UUID run,
            final int revision,
            final LocalDate start,
            final LocalDate end,
            final Policies policies)
            throws SQLException {
        try (var input =
                getClass().getResourceAsStream("/analytic-laboratory/references.synthetic.json")) {
            if (input == null) {
                throw new IllegalStateException("ANA_REFERENCE_RESOURCE_MISSING");
            }
            return importFixture(run, revision, start, end, policies, input.readNBytes(32769));
        } catch (final IOException failure) {
            throw new IllegalStateException("ANA_REFERENCE_RESOURCE", failure);
        }
    }

    public Receipt importManifestPackaged(
            final UUID run,
            final int revision,
            final LocalDate start,
            final LocalDate end,
            final Policies policies)
            throws SQLException {
        try (var input =
                getClass()
                        .getResourceAsStream(
                                "/analytic-laboratory/manifest-references.synthetic.json")) {
            if (input == null) {
                throw new IllegalStateException("ANA_REFERENCE_RESOURCE_MISSING");
            }
            return importFixture(run, revision, start, end, policies, input.readNBytes(32769));
        } catch (final IOException failure) {
            throw new IllegalStateException("ANA_REFERENCE_RESOURCE", failure);
        }
    }

    public Receipt importFixture(
            final UUID run,
            final int revision,
            final LocalDate start,
            final LocalDate end,
            final Policies policies,
            final byte[] fixture)
            throws SQLException {
        Objects.requireNonNull(policies);
        if (revision < 1
                || revision > 100000
                || start == null
                || end == null
                || !start.isBefore(end)
                || fixture == null
                || fixture.length == 0
                || fixture.length > 32768) {
            throw new IllegalArgumentException("ANA_REFERENCE_BOUND");
        }
        final JsonNode data;
        try {
            data = new ObjectMapper().readTree(fixture);
        } catch (final IOException failure) {
            throw new IllegalArgumentException("ANA_REFERENCE_JSON", failure);
        }
        if (!"FIXTURE_SINTETICA_EXPLICITA".equals(data.path("provenance").asText())
                || !"analytic-references-v1".equals(data.path("version").asText())) {
            throw new IllegalArgumentException("ANA_REFERENCE_PROVENANCE");
        }
        final var labels = new SQLServerDataTable();
        labels.addColumnMetadata("category", Types.VARCHAR);
        labels.addColumnMetadata("raw_value", Types.NVARCHAR);
        labels.addColumnMetadata("label", Types.NVARCHAR);
        for (final var row : bounded(data, "labels")) {
            labels.addRow(
                    required(row, "category", 32),
                    required(row, "raw", 128),
                    required(row, "label", 128));
        }
        final var registry = registryMetadata();
        for (final var row : bounded(data, "registry")) {
            if (!row.path("active").isBoolean()) {
                throw new IllegalArgumentException("ANA_REFERENCE_ACTIVE");
            }
            registry.addRow(
                    required(row, "kind", 16),
                    required(row, "key", 64),
                    required(row, "name", 256),
                    optional(row, "document", 64),
                    optional(row, "plate", 32),
                    optional(row, "branch", 64),
                    decimal(row, "capacity"),
                    optional(row, "unit", 8),
                    optional(row, "classification", 128),
                    Date.valueOf(start),
                    Date.valueOf(end),
                    row.get("active").booleanValue(),
                    required(row, "sourcePath", 256),
                    optional(row, "vehicleType", 64),
                    optional(row, "vehicleOwner", 256));
        }
        final var exclusions = new SQLServerDataTable();
        exclusions.addColumnMetadata("kind", Types.VARCHAR);
        exclusions.addColumnMetadata("match_value", Types.NVARCHAR);
        for (final var row : bounded(data, "exclusions")) {
            exclusions.addRow(required(row, "kind", 24), required(row, "value", 128));
        }
        try (var connection = source.getConnection();
                var statement =
                        (SQLServerPreparedStatement)
                                connection.prepareStatement(
                                        "EXEC ref.usp_import_analytic_references ?,?,?,?,?,?,?,?,?,?,?,?")) {
            statement.setQueryTimeout(20);
            statement.setString(1, run.toString());
            statement.setInt(2, revision);
            statement.setObject(3, start);
            statement.setObject(4, end);
            statement.setString(5, fingerprint(fixture, start, end, policies));
            statement.setStructured(6, "ref.analytic_lab_label_batch", labels);
            statement.setStructured(7, "ref.analytic_lab_registry_batch", registry);
            statement.setStructured(8, "ref.analytic_lab_exclusion_batch", exclusions);
            statement.setString(9, policies.fiscal().name());
            statement.setString(10, policies.branch().name());
            statement.setString(11, policies.driver().name());
            statement.setTimestamp(12, Timestamp.from(clock.instant()));
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("ANA_REFERENCE_RECEIPT_MISSING");
                }
                return new Receipt(row.getLong(1), row.getLong(2));
            }
        }
    }

    private static SQLServerDataTable registryMetadata() throws SQLException {
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("dimension_kind", Types.VARCHAR);
        table.addColumnMetadata("entity_key", Types.VARCHAR);
        table.addColumnMetadata("raw_name", Types.NVARCHAR);
        table.addColumnMetadata("raw_document", Types.NVARCHAR);
        table.addColumnMetadata("license_plate", Types.NVARCHAR);
        table.addColumnMetadata("branch_key", Types.VARCHAR);
        table.addColumnMetadata("capacity", Types.DECIMAL);
        table.addColumnMetadata("capacity_unit", Types.VARCHAR);
        table.addColumnMetadata("classification", Types.NVARCHAR);
        table.addColumnMetadata("valid_from", Types.DATE);
        table.addColumnMetadata("valid_to_exclusive", Types.DATE);
        table.addColumnMetadata("active", Types.BIT);
        table.addColumnMetadata("source_path", Types.NVARCHAR);
        table.addColumnMetadata("vehicle_type", Types.NVARCHAR);
        table.addColumnMetadata("vehicle_owner", Types.NVARCHAR);
        return table;
    }

    private static JsonNode bounded(final JsonNode data, final String name) {
        final var rows = data.path(name);
        if (!rows.isArray() || rows.size() > 100) {
            throw new IllegalArgumentException("ANA_REFERENCE_BATCH_BOUND");
        }
        return rows;
    }

    private static String required(final JsonNode row, final String field, final int maximum) {
        final var value = optional(row, field, maximum);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ANA_REFERENCE_FIELD_REQUIRED");
        }
        return value;
    }

    private static String optional(final JsonNode row, final String field, final int maximum) {
        final var value = row.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().length() > maximum) {
            throw new IllegalArgumentException("ANA_REFERENCE_FIELD_TYPE_BOUND");
        }
        return value.textValue();
    }

    private static BigDecimal decimal(final JsonNode row, final String field) {
        final var value = optional(row, field, 40);
        if (value == null) {
            return null;
        }
        final var number = new BigDecimal(value).setScale(8, java.math.RoundingMode.UNNECESSARY);
        if (number.precision() > 28) {
            throw new IllegalArgumentException("ANA_REFERENCE_DECIMAL");
        }
        return number;
    }

    private static String fingerprint(
            final byte[] fixture,
            final LocalDate start,
            final LocalDate end,
            final Policies policies) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            digest.update(fixture);
            digest.update(
                    ("|" + start + "|" + end + "|" + policies).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException("ANA_REFERENCE_DIGEST", failure);
        }
    }

    public enum Fiscal {
        CTE_FIRST_REAL_DOCUMENT,
        UNRESOLVED
    }

    public enum Branch {
        EXPLICIT_ASSIGNMENT,
        UNRESOLVED
    }

    public enum Driver {
        INCLUDE_ALL_BOUND,
        EXCLUDE_GENERIC,
        UNRESOLVED
    }

    public record Policies(Fiscal fiscal, Branch branch, Driver driver) {
        public Policies {
            Objects.requireNonNull(fiscal);
            Objects.requireNonNull(branch);
            Objects.requireNonNull(driver);
        }
    }

    public record Receipt(long release, long rows) {}
}
