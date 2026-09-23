package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only aggregate evidence is separate from the disposable domain transaction. */
public final class QualificationSqlEvidence {
    public record Snapshot(Map<String, Long> tables, String sha256) {
        public Snapshot {
            if (tables == null || tables.isEmpty() || tables.size() > 2048) {
                throw new IllegalArgumentException("QUAL_SQL_SNAPSHOT_BOUND");
            }
            tables = Map.copyOf(tables);
        }
    }

    private QualificationSqlEvidence() {}

    public static void master(final QualificationConfiguration configuration) throws SQLException {
        if (!Boolean.getBoolean("shadow.local.integration.enabled")
                || !Boolean.getBoolean("shadow.local.integration.profile.active")) {
            throw new IllegalArgumentException("QUAL_SQL_OPT_IN_REQUIRED");
        }
        final var url =
                configuration
                        .jdbcUrl()
                        .replace("databaseName=ETL_SISTEMA_V2_SHADOW", "databaseName=master");
        try (var connection = DriverManager.getConnection(url);
                var statement =
                        connection.prepareStatement(
                                "SELECT name,state_desc FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW'")) {
            statement.setQueryTimeout(10);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()
                        || !"ETL_SISTEMA_V2_SHADOW".equals(rows.getString(1))
                        || !"ONLINE".equals(rows.getString(2))
                        || rows.next()) {
                    throw new SQLException("QUAL_SQL_MASTER_TARGET");
                }
            }
        }
    }

    public static Snapshot snapshot(final QualificationConfiguration configuration)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.open(configuration.jdbcUrl())) {
            return snapshot(session);
        }
    }

    public static Snapshot snapshot(final ColetaTemporalLaboratorySession session)
            throws SQLException {
        final Map<String, Long> tables = new LinkedHashMap<>();
        final var json = JsonNodeFactory.instance.objectNode();
        try (var connection = session.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT SCHEMA_NAME(t.schema_id)+N'.'+t.name,SUM(p.rows) FROM sys.tables t"
                                        + " JOIN sys.partitions p ON p.object_id=t.object_id AND p.index_id IN(0,1)"
                                        + " GROUP BY t.schema_id,t.name"
                                        + " ORDER BY SCHEMA_NAME(t.schema_id)+N'.'+t.name COLLATE Latin1_General_100_BIN2")) {
            statement.setQueryTimeout(10);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (tables.size() >= 2048) {
                        throw new SQLException("QUAL_SQL_TABLE_BOUND");
                    }
                    final String table = rows.getString(1);
                    final long count = rows.getLong(2);
                    if (table == null
                            || table.length() > 260
                            || count < 0
                            || tables.put(table, count) != null) {
                        throw new SQLException("QUAL_SQL_AGGREGATE_INVALID");
                    }
                    json.put(table, count);
                }
            }
        }
        if (tables.isEmpty()) {
            throw new SQLException("QUAL_SQL_SCHEMA_EMPTY");
        }
        return new Snapshot(
                tables, QualificationJson.sha256(json.toString().getBytes(StandardCharsets.UTF_8)));
    }

    public static int spid(final ColetaTemporalLaboratorySession session) throws SQLException {
        try (var connection = session.getConnection();
                var statement = connection.prepareStatement("SELECT @@SPID")) {
            statement.setQueryTimeout(10);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("QUAL_SQL_SPID_MISSING");
                }
                return rows.getInt(1);
            }
        }
    }
}
