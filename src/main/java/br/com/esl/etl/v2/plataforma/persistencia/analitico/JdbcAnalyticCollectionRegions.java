package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Types;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Bounded logistics region import with exact versioned normalization and governed ratification. */
public final class JdbcAnalyticCollectionRegions {
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticCollectionRegions(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public JdbcAnalyticReferences.Receipt importPackaged(
            final UUID run, final int revision, final LocalDate start, final LocalDate end)
            throws SQLException {
        try (var input =
                getClass()
                        .getResourceAsStream(
                                "/analytic-laboratory/collection-regions.synthetic.json")) {
            if (input == null) {
                throw new IllegalStateException("ANA_COLLECTION_REGION_RESOURCE");
            }
            return importFixture(run, revision, start, end, input.readNBytes(32769));
        } catch (final IOException failure) {
            throw new IllegalStateException("ANA_COLLECTION_REGION_RESOURCE", failure);
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
            throw new IllegalArgumentException("ANA_COLLECTION_REGION_BOUND");
        }
        final JsonNode data;
        try {
            data =
                    new ObjectMapper()
                            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readTree(fixture);
        } catch (final IOException failure) {
            throw new IllegalArgumentException("ANA_COLLECTION_REGION_JSON", failure);
        }
        if (!"FIXTURE_SINTETICA_EXPLICITA".equals(data.path("provenance").asText())
                || !"synthetic-analytic-collection-region-v1".equals(data.path("version").asText())
                || !data.path("rows").isArray()
                || data.path("rows").isEmpty()
                || data.path("rows").size() > 100) {
            throw new IllegalArgumentException("ANA_COLLECTION_REGION_PROVENANCE");
        }
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("kind", Types.VARCHAR);
        table.addColumnMetadata("key1", Types.NVARCHAR);
        table.addColumnMetadata("key2", Types.NVARCHAR);
        table.addColumnMetadata("region_code", Types.NVARCHAR);
        table.addColumnMetadata("priority", Types.SMALLINT);
        if (!"trim-upper-nfc-v1".equals(data.path("normalizationVersion").asText())) {
            throw new IllegalArgumentException("ANA_COLLECTION_REGION_NORMALIZATION");
        }
        for (final var row : data.path("rows")) {
            final String key = required(row, "key1", 128);
            if (!Normalizer.isNormalized(key, Normalizer.Form.NFC)
                    || !row.path("priority").isIntegralNumber()
                    || !row.path("priority").canConvertToInt()
                    || row.path("priority").intValue() < 1
                    || row.path("priority").intValue() > 1000) {
                throw new IllegalArgumentException("ANA_COLLECTION_REGION_FIELD");
            }
            table.addRow(
                    required(row, "kind", 4),
                    key,
                    required(row, "key2", 128),
                    required(row, "region", 64),
                    row.path("priority").intValue());
        }
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var sql =
                    (SQLServerPreparedStatement)
                            connection.prepareStatement(
                                    "EXEC ref.usp_import_analytic_collection_regions ?,?,?,?,?,?,?")) {
                sql.setQueryTimeout(10);
                sql.setString(1, run.toString());
                sql.setInt(2, revision);
                sql.setObject(3, start);
                sql.setObject(4, end);
                sql.setString(5, fingerprint(fixture));
                sql.setInt(6, fixture.length);
                sql.setStructured(7, "ref.analytic_collection_region_batch", table);
                try (var rows = sql.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("ANA_COLLECTION_REGION_RECEIPT");
                    }
                    final var result =
                            new JdbcAnalyticReferences.Receipt(rows.getLong(1), rows.getLong(2));
                    if (result.rows() != data.path("rows").size() || rows.next()) {
                        throw new SQLException("ANA_COLLECTION_REGION_RECEIPT");
                    }
                    return result;
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

    private static String required(final JsonNode row, final String name, final int maximum) {
        final var value = row.path(name);
        if (!value.isTextual()
                || value.textValue().isBlank()
                || value.textValue().length() > maximum) {
            throw new IllegalArgumentException("ANA_COLLECTION_REGION_FIELD");
        }
        return value.textValue();
    }

    private static String fingerprint(final byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
