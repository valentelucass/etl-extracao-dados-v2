package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** All 971 physical columns are checked against the immutable approved V098 schema description. */
public final class QualificationPhysicalMetadata {
    private final JsonNode columns;

    public QualificationPhysicalMetadata() throws IOException {
        this(read());
    }

    private static JsonNode read() throws IOException {
        try (var input =
                QualificationPhysicalMetadata.class.getResourceAsStream(
                        "/qualification-laboratory/physical-columns.v098.json")) {
            if (input == null) {
                throw new IllegalArgumentException("QUAL_PHYSICAL_SCHEMA_RESOURCE");
            }
            return QualificationJson.parse(input.readNBytes(524289), 524288);
        }
    }

    QualificationPhysicalMetadata(final JsonNode document) {
        QualificationJson.fields(
                document, "version", "schemaVersion", "origin", "sourceSha256", "columns");
        if (!"qualification-physical-schema-v1".equals(document.path("version").asText())
                || QualificationJson.number(document, "schemaVersion", 98, 98) != 98
                || !"APPROVED_SCHEMA_SNAPSHOT_V098".equals(document.path("origin").asText())
                || !"f75a2a103eb9b558dbb633fbdb8dde9d240f5ea70f7b6a5302e2be0ef492417b"
                        .equals(document.path("sourceSha256").asText())) {
            throw new IllegalArgumentException("QUAL_PHYSICAL_SCHEMA_ORIGIN");
        }
        columns = document.path("columns");
        QualificationJson.array(columns, 971, 971);
        final Set<String> contracts = new HashSet<>();
        Arrays.stream(AnalyticSqlContract.values())
                .forEach(contract -> contracts.add(contract.localName().substring(4)));
        final var keys = new HashSet<String>();
        String previous = "";
        for (final var column : columns) {
            QualificationJson.fields(
                    column,
                    "localName",
                    "ordinal",
                    "columnName",
                    "sqlType",
                    "bytes",
                    "precision",
                    "scale",
                    "nullable",
                    "collation");
            final String name = QualificationJson.text(column, "localName", 128);
            final int ordinal = QualificationJson.number(column, "ordinal", 1, 256);
            QualificationJson.text(column, "columnName", 128);
            QualificationJson.text(column, "sqlType", 24);
            QualificationJson.number(column, "bytes", -1, 8000);
            QualificationJson.number(column, "precision", 0, 38);
            QualificationJson.number(column, "scale", 0, 38);
            QualificationJson.flag(column, "nullable");
            if (!column.path("collation").isNull()) {
                QualificationJson.text(column, "collation", 128);
            }
            final String key = name + String.format(java.util.Locale.ROOT, ":%03d", ordinal);
            if (!contracts.contains(name) || !keys.add(key) || key.compareTo(previous) <= 0) {
                throw new IllegalArgumentException("QUAL_PHYSICAL_SCHEMA_COLUMN_SET");
            }
            previous = key;
        }
    }

    public int verify(final ColetaTemporalLaboratorySession session) throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT o.name,c.column_id,c.name,t.name,c.max_length,c.precision,c.scale,c.is_nullable,c.collation_name"
                                        + " FROM sys.views o JOIN sys.columns c ON c.object_id=o.object_id"
                                        + " JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE o.schema_id=SCHEMA_ID(N'pub')"
                                        + " AND o.name LIKE N'analytic[_]lab[_]sql[_][0-9][0-9]'"
                                        + " ORDER BY o.name COLLATE Latin1_General_100_BIN2,c.column_id")) {
            sql.setQueryTimeout(15);
            int count = 0;
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    if (count >= columns.size()) {
                        throw new SQLException("QUAL_PHYSICAL_SCHEMA_EXTRA_COLUMN");
                    }
                    final var expected = columns.get(count++);
                    if (!expected.path("localName").textValue().equals(rows.getString(1))
                            || expected.path("ordinal").intValue() != rows.getInt(2)
                            || !expected.path("columnName").textValue().equals(rows.getString(3))
                            || !expected.path("sqlType").textValue().equals(rows.getString(4))
                            || expected.path("bytes").intValue() != rows.getInt(5)
                            || expected.path("precision").intValue() != rows.getInt(6)
                            || expected.path("scale").intValue() != rows.getInt(7)
                            || expected.path("nullable").booleanValue() != rows.getBoolean(8)
                            || !Objects.equals(
                                    expected.path("collation").textValue(), rows.getString(9))) {
                        throw new SQLException("QUAL_PHYSICAL_SCHEMA_DRIFT");
                    }
                }
            }
            if (count != 971) {
                throw new SQLException("QUAL_PHYSICAL_SCHEMA_MISSING_COLUMN");
            }
            return count;
        }
    }
}
