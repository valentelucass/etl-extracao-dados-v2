package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualificationComparatorTest {
    private static QualificationComparator.Binding binding() {
        return new QualificationComparator.Binding(
                "a".repeat(64), "b".repeat(64), "c".repeat(64), "INDEPENDENT_SYNTHETIC_RULES_V1");
    }

    private static QualificationComparator comparator(final long count) {
        return new QualificationComparator(
                AnalyticSqlContract.SQL_15,
                binding(),
                binding(),
                AnalyticSqlCatalog.columns(AnalyticSqlContract.SQL_15),
                new QualificationComparator.ExpectedRows() {
                    @Override
                    public long count() {
                        return count;
                    }

                    @Override
                    public List<AnalyticSqlValue> at(final long ordinal) {
                        return List.of(new AnalyticSqlValue.Text("CLIENTE SINTÉTICO " + ordinal));
                    }
                });
    }

    @Test
    void comparesAnOrderedIndependentOracleAndDoesNotRetainObservedRows() {
        final var comparator = comparator(64);
        comparator.metadata(AnalyticSqlCatalog.columns(AnalyticSqlContract.SQL_15));
        for (int row = 0; row < 64; row++) {
            comparator.accept(
                    new JdbcAnalyticQueries.Row(
                            AnalyticSqlContract.SQL_15,
                            List.of(new AnalyticSqlValue.Text("CLIENTE SINTÉTICO " + row))));
        }
        final var result = comparator.finish();
        assertEquals(QualificationGate.State.PASS_LOCAL, result.gate());
        assertEquals(64, result.observedRows());
        assertEquals(0, result.differences());
        assertThrows(IllegalStateException.class, comparator::finish);
    }

    @Test
    void valueNullTypeGrainAndMetadataMutantsCannotPassAndDiffsNeverContainValues() {
        final var comparator = comparator(64);
        comparator.metadata(AnalyticSqlCatalog.columns(AnalyticSqlContract.SQL_15));
        comparator.accept(
                new JdbcAnalyticQueries.Row(
                        AnalyticSqlContract.SQL_15, List.of(new AnalyticSqlValue.Missing())));
        comparator.accept(
                new JdbcAnalyticQueries.Row(
                        AnalyticSqlContract.SQL_15, List.of(new AnalyticSqlValue.IntegerValue(0))));
        for (int row = 2; row < 64; row++) {
            comparator.accept(
                    new JdbcAnalyticQueries.Row(
                            AnalyticSqlContract.SQL_15,
                            List.of(new AnalyticSqlValue.Text("INTENTIONAL_DIVERGENCE"))));
        }
        final var result = comparator.finish();
        assertEquals(QualificationGate.State.FAILED, result.gate());
        assertEquals(64, result.differences());
        assertEquals(24, result.sample().size());
        assertEquals(QualificationComparator.Difference.NULLABILITY, result.sample().get(0).kind());
        assertEquals(QualificationComparator.Difference.TYPE, result.sample().get(1).kind());
        assertEquals(QualificationGate.State.FAILED, comparator(1).finish().gate());
        final var metadata = comparator(0);
        metadata.metadata(
                List.of(new AnalyticSqlCatalog.Column(1, "wrong", "nvarchar", 0, 0, true)));
        assertEquals(
                QualificationComparator.Difference.METADATA,
                metadata.finish().sample().get(0).kind());
        assertTrue(result.sample().stream().allMatch(diff -> diff.ordinal() == 1));
    }

    @Test
    void missingMetadataCannotApproveEvenAnEmptyOutput() {
        assertEquals(QualificationGate.State.FAILED, comparator(0).finish().gate());
    }

    @Test
    void rejectsAnotherFixtureRevisionOriginAndColumnVersionBeforeComparison() {
        final var wrong =
                new QualificationComparator.Binding(
                        "d".repeat(64),
                        "b".repeat(64),
                        "c".repeat(64),
                        "INDEPENDENT_SYNTHETIC_RULES_V1");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new QualificationComparator(
                                AnalyticSqlContract.SQL_15,
                                binding(),
                                wrong,
                                AnalyticSqlCatalog.columns(AnalyticSqlContract.SQL_15),
                                new QualificationComparator.ExpectedRows() {
                                    @Override
                                    public long count() {
                                        return 0;
                                    }

                                    @Override
                                    public List<AnalyticSqlValue> at(final long ordinal) {
                                        return List.of();
                                    }
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new QualificationComparator.Binding(
                                "a".repeat(64),
                                "b".repeat(64),
                                "c".repeat(64),
                                "COPIED_SQL_OUTPUT"));
    }
}
