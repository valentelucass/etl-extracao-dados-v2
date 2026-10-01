package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.UUID;

/** Independent bounded fact tuples compared as multisets in SQL, using each declared grain. */
public final class LocalFactOracle {
    private enum Fact {
        MAT01(
                "sourceKey,indicator",
                "sourceKey nvarchar(256),indicator varchar(2),amount decimal(28,8)",
                "SELECT d.source_key sourceKey,f.indicator,o.total_value amount FROM mart.analytic_freight_operational f"
                        + " JOIN mart.analytic_freight_operational_observation o ON o.observation_id=f.observation_id"
                        + " JOIN core.expansion_lab_dependency d ON d.dependency_id=f.dependency_id WHERE f.run_id=?"),
        MAT02(
                "date,branch",
                "date date,branch varchar(64),issued bigint,unloaded bigint,scanned bigint,"
                        + "incomplete bigint,total bigint,percentage decimal(28,8)",
                "SELECT reference_date date,branch_key branch,issued,unloaded,scanned,incomplete,total,percentage"
                        + " FROM pub.analytic_lab_collectors WHERE run_id=?"),
        MAT03(
                "sourceKey",
                "sourceKey nvarchar(256),amount decimal(28,8)",
                "SELECT source_key sourceKey,revenue_value amount FROM mart.expansion_lab_revenue WHERE run_id=?"),
        MAT04(
                "sourceKey",
                "sourceKey nvarchar(256),amount decimal(28,8)",
                "SELECT r.root_key sourceKey,i.operational_value amount FROM mart.expansion_lab_invoice i"
                        + " JOIN core.expansion_lab_root r ON r.root_id=i.root_id WHERE i.run_id=?"),
        MAT05(
                "sourceKey",
                "sourceKey nvarchar(256),date date,amount decimal(28,8)",
                "SELECT source_key sourceKey,reference_date date,total_revenue amount FROM pub.analytic_lab_manifests WHERE run_id=?");

        private final String keys;
        private final String schema;
        private final String query;

        Fact(final String keys, final String schema, final String query) {
            this.keys = keys;
            this.schema = schema;
            this.query = query;
        }
    }

    private final Path directory;
    private final EnumMap<Fact, Pin> files = new EnumMap<>(Fact.class);

    public LocalFactOracle(final Path path) throws IOException {
        this(PinnedLocalJson.open(path, 16384));
    }

    public LocalFactOracle(final PinnedLocalJson pin) throws IOException {
        final var root = pin.read();
        QualificationJson.fields(root, "version", "origin", "facts");
        if (!"local-fact-oracles-v1".equals(QualificationJson.text(root, "version", 40))
                || !"INDEPENDENT_SYNTHETIC_RULES_V1"
                        .equals(QualificationJson.text(root, "origin", 40))) {
            throw new IllegalArgumentException("LOCAL_FACT_ORACLE_ORIGIN");
        }
        QualificationJson.array(root.path("facts"), 5, 5);
        directory = pin.file().toPath().getParent();
        for (final var entry : root.path("facts")) {
            QualificationJson.fields(entry, "id", "grain", "file", "sha256");
            final var fact = Fact.valueOf(QualificationJson.text(entry, "id", 5));
            final String file = QualificationJson.text(entry, "file", 80);
            if (!fact.keys.equals(QualificationJson.text(entry, "grain", 64))
                    || !file.matches("[a-z0-9][a-z0-9-]{0,70}\\.json")
                    || files.putIfAbsent(
                                    fact, new Pin(file, QualificationJson.digest(entry, "sha256")))
                            != null) {
                throw new IllegalArgumentException("LOCAL_FACT_ORACLE_GRAIN");
            }
            tuples(fact);
        }
    }

    public void verify(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID expansion,
            final CancellationToken token)
            throws IOException, SQLException {
        for (final var fact : Fact.values()) {
            token.throwIfCancellationRequested();
            final String expected = tuples(fact);
            new SqlComparison().verify(session, run, expansion, fact, expected);
        }
    }

    private static final class SqlComparison {
        private void verify(
                final ColetaTemporalLaboratorySession session,
                final UUID run,
                final UUID expansion,
                final Fact fact,
                final String expected)
                throws SQLException {
            final String columns = String.join(",", names(fact));
            // The expected JSON is independently supplied. These queries only read fact tuples;
            // they do not reproduce the calculations that materialized them.
            final String sql =
                    "WITH actual AS ("
                            + fact.query
                            + "), expected AS (SELECT * FROM OPENJSON(?) WITH ("
                            + fact.schema
                            + ")), missing AS (SELECT "
                            + columns
                            + " FROM expected EXCEPT SELECT "
                            + columns
                            + " FROM actual), extra AS (SELECT "
                            + columns
                            + " FROM actual EXCEPT SELECT "
                            + columns
                            + " FROM expected) SELECT (SELECT COUNT_BIG(*) FROM missing),"
                            + "(SELECT COUNT_BIG(*) FROM extra),(SELECT COUNT_BIG(*) FROM actual),"
                            + "(SELECT COUNT_BIG(*) FROM expected),(SELECT COUNT_BIG(*) FROM (SELECT "
                            + fact.keys
                            + " FROM expected GROUP BY "
                            + fact.keys
                            + " HAVING COUNT_BIG(*)<>1) e),"
                            + "(SELECT COUNT_BIG(*) FROM (SELECT "
                            + fact.keys
                            + " FROM actual GROUP BY "
                            + fact.keys
                            + " HAVING COUNT_BIG(*)<>1) a)";
            try (var connection = session.getConnection();
                    var statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(30);
                statement.setString(
                        1, (fact == Fact.MAT03 || fact == Fact.MAT04 ? expansion : run).toString());
                statement.setNString(2, expected);
                try (var row = statement.executeQuery()) {
                    if (!row.next()
                            || row.getLong(1) != 0
                            || row.getLong(2) != 0
                            || row.getLong(3) != row.getLong(4)
                            || row.getLong(5) != 0
                            || row.getLong(6) != 0) {
                        throw new SQLException("LOCAL_FACT_ORACLE_DIVERGENCE_" + fact);
                    }
                }
            }
        }
    }

    public void verifyFiles(final CancellationToken token) throws IOException {
        for (final var fact : Fact.values()) {
            token.throwIfCancellationRequested();
            tuples(fact);
        }
    }

    private String tuples(final Fact fact) throws IOException {
        final var pin = files.get(fact);
        final Path path = directory.resolve(pin.file());
        QualificationJson.regular(path);
        final byte[] bytes;
        try (var stream = Files.newInputStream(path)) {
            bytes = stream.readNBytes(262145);
        }
        final var rows = QualificationJson.parse(bytes, 262144);
        if (!QualificationJson.sha256(bytes).equals(pin.sha256())) {
            throw new IllegalArgumentException("LOCAL_FACT_ORACLE_CHANGED");
        }
        QualificationJson.array(rows, 0, 4096);
        final String[] names = names(fact);
        for (final var row : rows) {
            QualificationJson.fields(row, names);
            for (final String name : names) {
                final var value = row.get(name);
                if (value.isContainerNode() || value.isBoolean()) {
                    throw new IllegalArgumentException("LOCAL_FACT_ORACLE_TYPE");
                }
                if (name.equals("sourceKey") || name.equals("branch") || name.equals("indicator")) {
                    QualificationJson.text(
                            row,
                            name,
                            name.equals("indicator") ? 2 : name.equals("branch") ? 64 : 256);
                } else if (name.equals("date")) {
                    java.time.LocalDate.parse(QualificationJson.text(row, name, 10));
                } else if (name.equals("amount") || name.equals("percentage")) {
                    if (!value.isNull()) {
                        final var number =
                                new java.math.BigDecimal(value.asText())
                                        .setScale(8, java.math.RoundingMode.UNNECESSARY);
                        if (number.precision() > 28) {
                            throw new IllegalArgumentException("LOCAL_FACT_ORACLE_PRECISION");
                        }
                    }
                } else if (!value.isIntegralNumber() || !value.canConvertToLong()) {
                    throw new IllegalArgumentException("LOCAL_FACT_ORACLE_INTEGER");
                }
            }
        }
        return rows.toString();
    }

    private record Pin(String file, String sha256) {}

    private static String[] names(final Fact fact) {
        return switch (fact) {
            case MAT01 -> new String[] {"sourceKey", "indicator", "amount"};
            case MAT02 ->
                    new String[] {
                        "date",
                        "branch",
                        "issued",
                        "unloaded",
                        "scanned",
                        "incomplete",
                        "total",
                        "percentage"
                    };
            case MAT03, MAT04 -> new String[] {"sourceKey", "amount"};
            case MAT05 -> new String[] {"sourceKey", "date", "amount"};
        };
    }
}
