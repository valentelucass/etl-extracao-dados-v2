package br.com.esl.etl.v2.plataforma.qualificacao;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Ordered, exact typed comparison; only counts and bounded diagnostic coordinates are retained. */
public final class QualificationComparator implements Consumer<JdbcAnalyticQueries.Row> {
    public enum Difference {
        VALUE,
        NULLABILITY,
        TYPE,
        COLUMN,
        ROW_COUNT,
        CONTRACT,
        METADATA
    }

    public record Diff(long row, int ordinal, Difference kind) {}

    public record Result(
            String contract,
            long expectedRows,
            long observedRows,
            long differences,
            List<Diff> sample,
            QualificationGate.State gate) {
        public Result {
            if (sample == null || sample.size() > 24) {
                throw new IllegalArgumentException("QUAL_COMPARATOR_SAMPLE_BOUND");
            }
            sample = List.copyOf(sample);
        }
    }

    /**
     * Implementations must generate one expected fixture row, never use a queried result as seed.
     */
    public interface ExpectedRows {
        long count();

        List<AnalyticSqlValue> at(long ordinal);

        /** Semantic rules are restricted to explicitly declared technical/structured cells. */
        default boolean equivalent(
                final long row,
                final AnalyticSqlCatalog.Column column,
                final AnalyticSqlValue wanted,
                final AnalyticSqlValue actual) {
            return wanted.equals(actual);
        }
    }

    public record Binding(
            String packageRevision, String fixtureSha256, String oracleSha256, String origin) {
        public Binding {
            for (final var hash : List.of(packageRevision, fixtureSha256, oracleSha256)) {
                if (!hash.matches("[a-f0-9]{64}")) {
                    throw new IllegalArgumentException("QUAL_ORACLE_BINDING");
                }
            }
            if (!"INDEPENDENT_SYNTHETIC_RULES_V1".equals(origin)) {
                throw new IllegalArgumentException("QUAL_ORACLE_ORIGIN");
            }
        }
    }

    private final AnalyticSqlContract contract;
    private final ExpectedRows expected;
    private final List<AnalyticSqlCatalog.Column> columns;
    private final List<Diff> sample = new ArrayList<>(24);
    private long observed;
    private long differences;
    private boolean finished;
    private boolean metadataObserved;

    public QualificationComparator(
            final AnalyticSqlContract contract,
            final Binding approved,
            final Binding oracle,
            final List<AnalyticSqlCatalog.Column> columns,
            final ExpectedRows expected) {
        this.contract = Objects.requireNonNull(contract);
        this.expected = Objects.requireNonNull(expected);
        this.columns = List.copyOf(columns);
        if (!Objects.requireNonNull(approved).equals(Objects.requireNonNull(oracle))) {
            throw new IllegalArgumentException("QUAL_ORACLE_REVISION");
        }
        if (expected.count() < 0
                || expected.count() > 4096
                || columns.size() != contract.columns()) {
            throw new IllegalArgumentException("QUAL_ORACLE_SCOPE");
        }
        if (!this.columns.equals(AnalyticSqlCatalog.columns(contract))) {
            throw new IllegalArgumentException("QUAL_ORACLE_COLUMN_CONTRACT");
        }
    }

    /** Actual metadata is separately supplied from the JDBC/schema inspection, not fabricated. */
    public void metadata(final List<AnalyticSqlCatalog.Column> actual) {
        ensureOpen();
        if (metadataObserved) {
            throw new IllegalStateException("QUAL_ORACLE_METADATA_DUPLICATE");
        }
        metadataObserved = true;
        if (!columns.equals(actual)) {
            add(0, 0, Difference.METADATA);
        }
    }

    @Override
    public void accept(final JdbcAnalyticQueries.Row row) {
        ensureOpen();
        if (observed >= 4096) {
            throw new IllegalArgumentException("QUAL_ORACLE_ROW_LIMIT");
        }
        if (row.contract() != contract) {
            add(observed, 0, Difference.CONTRACT);
        } else if (observed >= expected.count()) {
            add(observed, 0, Difference.ROW_COUNT);
        } else {
            final var wanted = expected.at(observed);
            if (wanted.size() != columns.size() || row.values().size() != columns.size()) {
                add(observed, 0, Difference.COLUMN);
            } else {
                for (int index = 0; index < wanted.size(); index++) {
                    final var actual = row.values().get(index);
                    final var value = wanted.get(index);
                    if (value == null) {
                        throw new IllegalArgumentException("QUAL_ORACLE_JAVA_NULL");
                    }
                    if (actual instanceof AnalyticSqlValue.Missing
                            != value instanceof AnalyticSqlValue.Missing) {
                        add(observed, index + 1, Difference.NULLABILITY);
                    } else if (!actual.getClass().equals(value.getClass())) {
                        add(observed, index + 1, Difference.TYPE);
                    } else if (!expected.equivalent(observed, columns.get(index), value, actual)) {
                        add(observed, index + 1, Difference.VALUE);
                    }
                }
            }
        }
        observed++;
    }

    public Result finish() {
        ensureOpen();
        finished = true;
        if (!metadataObserved) {
            add(0, 0, Difference.METADATA);
        }
        if (observed != expected.count()) {
            add(observed, 0, Difference.ROW_COUNT);
        }
        return new Result(
                contract.id(),
                expected.count(),
                observed,
                differences,
                sample,
                differences == 0
                        ? QualificationGate.State.PASS_LOCAL
                        : QualificationGate.State.FAILED);
    }

    private void add(final long row, final int ordinal, final Difference kind) {
        differences++;
        if (sample.size() < 24) {
            sample.add(new Diff(row, ordinal, kind));
        }
    }

    private void ensureOpen() {
        if (finished) {
            throw new IllegalStateException("QUAL_ORACLE_ALREADY_FINISHED");
        }
    }
}
