package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** All 971 physical columns are checked against the exact schema epoch selected by the caller. */
public final class QualificationPhysicalMetadata {
    private static final String V098_SHA256 =
            "16ef4e66339805b2b164a7a802fcf6862a0af528bbb84aa5e6e3b03cfbfc72ff";
    private static final String V105_SHA256 =
            "8286ecfee465c7999002288ee9b1f5afe710613763dce9548016c6c262b2b424";
    private final JsonNode columns;

    /** The local artifact path retains its historical V098 contract. */
    public QualificationPhysicalMetadata() throws IOException {
        this(98);
    }

    public QualificationPhysicalMetadata(final int epoch) throws IOException {
        this(epoch, read(epoch));
    }

    private static JsonNode read(final int epoch) throws IOException {
        final String resource = resource(epoch);
        try (var input =
                QualificationPhysicalMetadata.class.getResourceAsStream(
                        "/qualification-laboratory/" + resource)) {
            return readResource(epoch, input);
        }
    }

    static JsonNode readResource(final int epoch, final InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("QUAL_PHYSICAL_SCHEMA_RESOURCE");
        }
        final byte[] bytes = input.readNBytes(524289);
        if (!pin(epoch).equals(QualificationJson.sha256(bytes))) {
            throw new IllegalArgumentException("QUAL_PHYSICAL_SCHEMA_HASH");
        }
        return QualificationJson.parse(bytes, 524288);
    }

    private static String resource(final int epoch) {
        return switch (epoch) {
            case 98 -> "physical-columns.v098.json";
            case 105 -> "physical-columns.v105.json";
            default -> throw new IllegalArgumentException("QUAL_PHYSICAL_SCHEMA_EPOCH");
        };
    }

    private static String pin(final int epoch) {
        return switch (epoch) {
            case 98 -> V098_SHA256;
            case 105 -> V105_SHA256;
            default -> throw new IllegalArgumentException("QUAL_PHYSICAL_SCHEMA_EPOCH");
        };
    }

    QualificationPhysicalMetadata(final JsonNode document) {
        this(98, document);
    }

    QualificationPhysicalMetadata(final int epoch, final JsonNode document) {
        resource(epoch);
        if (epoch == 105) {
            QualificationJson.fields(
                    document,
                    "version",
                    "schemaVersion",
                    "origin",
                    "baseSha256",
                    "catalogSha256",
                    "partialDmvSha256",
                    "dmvSha256",
                    "checkpointSha256",
                    "columns");
            if (!"qualification-physical-schema-v2".equals(document.path("version").asText())
                    || QualificationJson.number(document, "schemaVersion", 105, 105) != 105
                    || !"LOCAL_SCHEMA_DMV_0339".equals(document.path("origin").asText())
                    || !V098_SHA256.equals(document.path("baseSha256").asText())
                    || !"fe70f092b5d303480697c3e73da4fca85b022f15a90e09ba3e55065f1aa9becf"
                            .equals(document.path("catalogSha256").asText())
                    || !"ad68cbec1bb456ac51b73ae255bb1c77e5b09eaeddecb80cb4e33dd391ceff52"
                            .equals(document.path("partialDmvSha256").asText())
                    || !"64dde46c66f2e4088bab043ca5ed7442fe751513ccc185eeb3c236ed466d6c77"
                            .equals(document.path("dmvSha256").asText())
                    || !"e7e8c1104acc33880cb493fca45512eea566002bf88e6d220522ca0e42264415"
                            .equals(document.path("checkpointSha256").asText())) {
                throw new IllegalArgumentException("QUAL_PHYSICAL_SCHEMA_ORIGIN");
            }
        } else {
            QualificationJson.fields(
                    document, "version", "schemaVersion", "origin", "sourceSha256", "columns");
            if (!"qualification-physical-schema-v1".equals(document.path("version").asText())
                    || QualificationJson.number(document, "schemaVersion", 98, 98) != 98
                    || !"APPROVED_SCHEMA_SNAPSHOT_V098".equals(document.path("origin").asText())
                    || !"f75a2a103eb9b558dbb633fbdb8dde9d240f5ea70f7b6a5302e2be0ef492417b"
                            .equals(document.path("sourceSha256").asText())) {
                throw new IllegalArgumentException("QUAL_PHYSICAL_SCHEMA_ORIGIN");
            }
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
        return new SqlVerification().verify(session);
    }

    private final class SqlVerification {
        public int verify(final ColetaTemporalLaboratorySession session) throws SQLException {
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT o.name,c.column_id,c.name,t.name,c.max_length,c.precision,c.scale,"
                                            + "c.is_nullable,c.collation_name"
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
                        verifyColumn(
                                expected,
                                rows.getString(1),
                                rows.getInt(2),
                                rows.getString(3),
                                rows.getString(4),
                                rows.getInt(5),
                                rows.getInt(6),
                                rows.getInt(7),
                                rows.getBoolean(8),
                                rows.getString(9));
                    }
                }
                if (count != 971) {
                    throw new SQLException("QUAL_PHYSICAL_SCHEMA_MISSING_COLUMN");
                }
                return count;
            }
        }
    }

    static void verifyColumn(
            final JsonNode expected,
            final String localName,
            final int ordinal,
            final String columnName,
            final String sqlType,
            final int bytes,
            final int precision,
            final int scale,
            final boolean nullable,
            final String collation)
            throws SQLException {
        if (!expected.path("localName").textValue().equals(localName)
                || expected.path("ordinal").intValue() != ordinal
                || !expected.path("columnName").textValue().equals(columnName)
                || !expected.path("sqlType").textValue().equals(sqlType)
                || expected.path("bytes").intValue() != bytes
                || expected.path("precision").intValue() != precision
                || expected.path("scale").intValue() != scale
                || expected.path("nullable").booleanValue() != nullable
                || !Objects.equals(expected.path("collation").textValue(), collation)) {
            throw new SQLException("QUAL_PHYSICAL_SCHEMA_DRIFT");
        }
    }
}
