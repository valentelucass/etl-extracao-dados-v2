package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticScenario;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionStatus;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class LocalLaboratoryResultMappingTest {
    private static final String[] ANALYTIC = {"status", "--synthetic-analytic-lab"};
    private static final String[] EXPANSION = {"status", "--synthetic-expansion-lab"};
    private static final String[] RELATIONAL = {"status", "--synthetic-relational-lab"};

    @Test
    void analyticCliMapsLocalResultAndFailureWithoutOpeningTheShadowSession() {
        final var output = new ByteArrayOutputStream();
        final var print = new PrintStream(output);
        assertEquals(
                RuntimeExitCategory.SUCCESS,
                AnalyticLaboratoryMain.run(
                        ANALYTIC,
                        print,
                        (options, sink) ->
                                new AnalyticLaboratoryMain.RunResult(
                                        new JdbcAnalyticScenario.Status("COMPLETE", null, null),
                                        7,
                                        2)));
        assertTrue(output.toString().contains("jdbc_calls=2"));
        assertEquals(
                RuntimeExitCategory.DEGRADED,
                AnalyticLaboratoryMain.run(
                        ANALYTIC,
                        print,
                        (options, sink) ->
                                new AnalyticLaboratoryMain.RunResult(
                                        new JdbcAnalyticScenario.Status(
                                                "FAILED", "SYNTHETIC", null),
                                        7,
                                        2)));
        assertEquals(
                RuntimeExitCategory.LOCK,
                AnalyticLaboratoryMain.run(
                        ANALYTIC,
                        print,
                        (options, sink) -> {
                            throw sql(53502);
                        }));
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                AnalyticLaboratoryMain.run(
                        ANALYTIC,
                        print,
                        (options, sink) -> {
                            throw sql(70000);
                        }));
        assertEquals(
                RuntimeExitCategory.CANCELLED,
                AnalyticLaboratoryMain.run(
                        ANALYTIC,
                        print,
                        (options, sink) -> {
                            throw new ResilienceCancelledException();
                        }));
        assertEquals(
                RuntimeExitCategory.CONFIG_AUTH,
                AnalyticLaboratoryMain.run(
                        ANALYTIC,
                        print,
                        (options, sink) -> {
                            throw new IllegalStateException("synthetic");
                        }));
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                AnalyticLaboratoryMain.run(
                        ANALYTIC,
                        print,
                        (options, sink) -> {
                            throw new Exception("synthetic");
                        }));
    }

    @Test
    void expansionCliMapsLocalResultAndFailureWithoutOpeningTheShadowSession() {
        final var output = new ByteArrayOutputStream();
        final var print = new PrintStream(output);
        assertEquals(
                RuntimeExitCategory.SUCCESS,
                ExpansionLaboratoryMain.run(
                        EXPANSION,
                        print,
                        options -> new ExpansionLaboratoryMain.Result(expansionStatus(0), 3)));
        assertTrue(output.toString().contains("detail_rows=3"));
        assertEquals(
                RuntimeExitCategory.DEGRADED,
                ExpansionLaboratoryMain.run(
                        EXPANSION,
                        print,
                        options -> new ExpansionLaboratoryMain.Result(expansionStatus(1), 0)));
        assertEquals(
                RuntimeExitCategory.LOCK,
                ExpansionLaboratoryMain.run(
                        EXPANSION,
                        print,
                        options -> {
                            throw sql(53401);
                        }));
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                ExpansionLaboratoryMain.run(
                        EXPANSION,
                        print,
                        options -> {
                            throw sql(70000);
                        }));
        assertEquals(
                RuntimeExitCategory.CANCELLED,
                ExpansionLaboratoryMain.run(
                        EXPANSION,
                        print,
                        options -> {
                            throw new ResilienceCancelledException();
                        }));
        assertEquals(
                RuntimeExitCategory.CONFIG_AUTH,
                ExpansionLaboratoryMain.run(
                        EXPANSION,
                        print,
                        options -> {
                            throw new IllegalArgumentException("synthetic");
                        }));
    }

    @Test
    void relationalCliMapsLocalResultAndFailureWithoutOpeningTheShadowSession() {
        final var output = new ByteArrayOutputStream();
        final var print = new PrintStream(output);
        assertEquals(
                RuntimeExitCategory.SUCCESS,
                RelationalLaboratoryMain.run(RELATIONAL, print, options -> relationalStatus(0)));
        assertTrue(output.toString().contains("REL_LAB_ROLLBACK_ONLY"));
        assertEquals(
                RuntimeExitCategory.DEGRADED,
                RelationalLaboratoryMain.run(RELATIONAL, print, options -> relationalStatus(1)));
        assertEquals(
                RuntimeExitCategory.LOCK,
                RelationalLaboratoryMain.run(
                        RELATIONAL,
                        print,
                        options -> {
                            throw sql(53201);
                        }));
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                RelationalLaboratoryMain.run(
                        RELATIONAL,
                        print,
                        options -> {
                            throw sql(70000);
                        }));
        assertEquals(
                RuntimeExitCategory.CANCELLED,
                RelationalLaboratoryMain.run(
                        RELATIONAL,
                        print,
                        options -> {
                            throw new ResilienceCancelledException();
                        }));
        assertEquals(
                RuntimeExitCategory.CONFIG_AUTH,
                RelationalLaboratoryMain.run(
                        RELATIONAL,
                        print,
                        options -> {
                            throw new IllegalStateException("synthetic");
                        }));
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                RelationalLaboratoryMain.run(
                        RELATIONAL,
                        print,
                        options -> {
                            throw new RuntimeException("synthetic");
                        }));
    }

    private static SQLException sql(final int code) {
        return new SQLException("synthetic", "", code);
    }

    private static JdbcExpansionStatus.Status expansionStatus(final long pending) {
        return new JdbcExpansionStatus.Status(
                0, 0, 0, 0, 0, pending, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static JdbcRelationalLaboratory.Status relationalStatus(final long pending) {
        return new JdbcRelationalLaboratory.Status(0, 0, 0, 0, 0, pending, 0, 0, 0, 0, 0, 0, 3);
    }
}
