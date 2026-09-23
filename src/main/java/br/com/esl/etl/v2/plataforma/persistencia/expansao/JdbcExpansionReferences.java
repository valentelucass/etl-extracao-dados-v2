package br.com.esl.etl.v2.plataforma.persistencia.expansao;

import com.fasterxml.jackson.databind.JsonNode;
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
import javax.sql.DataSource;

/** Imports only the packaged reference fixture into the governed V2-035a foundation. */
public final class JdbcExpansionReferences {
    private final DataSource source;
    private final Clock clock;

    public JdbcExpansionReferences(final DataSource source, final Clock clock) {
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
    }

    public Releases importPackaged(
            final UUID run, final int revision, final LocalDate start, final LocalDate end)
            throws SQLException {
        return importFixture(run, revision, start, end, packagedLimited(16384));
    }

    /** Imports the exact caller-supplied synthetic release through the existing governed SQL. */
    public Releases importFixture(
            final UUID run,
            final int revision,
            final LocalDate start,
            final LocalDate end,
            final byte[] bytes)
            throws SQLException {
        if (revision < 1
                || revision > 1000
                || start == null
                || end == null
                || !start.isBefore(end)
                || start.plusDays(62).isBefore(end)
                || bytes == null
                || bytes.length == 0
                || bytes.length > 16384) {
            throw new IllegalArgumentException("EXP_REF_BOUND");
        }
        final JsonNode data;
        try {
            data = br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson.parse(bytes, 16384);
        } catch (final IOException failure) {
            throw new IllegalStateException("EXP_REF_RESOURCE_INVALID", failure);
        }
        if (!data.path("provenance").asText().equals("FIXTURE_SINTETICA_EXPLICITA")
                || !data.path("version").asText().equals("expansion-references-v1")) {
            throw new IllegalArgumentException("EXP_REF_PROVENANCE");
        }
        br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson.fields(
                data,
                "provenance",
                "version",
                "branchCode",
                "branchLabel",
                "payerReference",
                "labels");
        br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson.array(
                data.path("labels"), 1, 100);
        final var labels = new SQLServerDataTable();
        labels.addColumnMetadata("category", Types.VARCHAR);
        labels.addColumnMetadata("raw_value", Types.NVARCHAR);
        labels.addColumnMetadata("label", Types.NVARCHAR);
        for (final var label : data.path("labels")) {
            br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson.fields(
                    label, "category", "raw", "label");
            labels.addRow(
                    label.path("category").asText(),
                    label.path("raw").asText(),
                    label.path("label").asText());
        }
        final String fingerprint = fingerprint(bytes, start, end);
        try (var connection = source.getConnection();
                var sql =
                        (SQLServerPreparedStatement)
                                connection.prepareStatement(
                                        "EXEC ref.usp_import_expansion_references ?,?,?,?,?,?,?,?,?,?")) {
            sql.setQueryTimeout(20);
            sql.setString(1, run.toString());
            sql.setInt(2, revision);
            sql.setObject(3, start);
            sql.setObject(4, end);
            sql.setString(5, fingerprint);
            sql.setStructured(6, "ref.expansion_lab_label_batch", labels);
            sql.setString(7, data.path("branchCode").asText());
            sql.setString(8, data.path("branchLabel").asText());
            sql.setString(9, data.path("payerReference").asText());
            sql.setTimestamp(
                    10,
                    Timestamp.from(clock.instant()),
                    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            try (var rows = sql.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("EXP_REFERENCE_RECEIPT_MISSING");
                }
                return new Releases(
                        rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getLong(4));
            }
        }
    }

    private static byte[] packagedLimited(final int maximum) {
        if (maximum < 1 || maximum > 16384) {
            throw new IllegalArgumentException("EXP_REF_RESOURCE_BOUND");
        }
        try (var input =
                JdbcExpansionReferences.class.getResourceAsStream(
                        "/expansion-laboratory/references.synthetic.json")) {
            if (input == null) {
                throw new IllegalArgumentException("EXP_REF_RESOURCE_MISSING");
            }
            final var bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum) {
                throw new IllegalArgumentException("EXP_REF_RESOURCE_BOUND");
            }
            return bytes;
        } catch (final IOException failure) {
            throw new IllegalStateException("EXP_REF_RESOURCE_UNAVAILABLE", failure);
        }
    }

    private static String fingerprint(
            final byte[] bytes, final LocalDate start, final LocalDate end) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            digest.update(
                    ("|calendar-seed-v1|" + start + "|" + end).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException("EXP_REF_DIGEST", failure);
        }
    }

    public record Releases(long labels, long calendar, long branch, long payer) {}
}
