package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences.Branch;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences.Driver;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences.Fiscal;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences.Policies;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryReferencesIT {
    static final Policies POLICIES =
            new Policies(
                    Fiscal.CTE_FIRST_REAL_DOCUMENT,
                    Branch.EXPLICIT_ASSIGNMENT,
                    Driver.INCLUDE_ALL_BOUND);

    @Test
    void sealedReleasePreservesHomonymsRawNormalizationRolesCapacityAndExactReplay()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            AnalyticLaboratoryRasterIT.start(session, run);
            final var repository =
                    new JdbcAnalyticReferences(session, AnalyticLaboratoryRasterIT.CLOCK);
            final var receipt =
                    repository.importPackaged(
                            run,
                            1,
                            AnalyticLaboratoryRasterIT.DATE,
                            AnalyticLaboratoryRasterIT.DATE.plusDays(3),
                            POLICIES);
            assertEquals(75, receipt.rows());
            assertEquals(
                    receipt,
                    repository.importPackaged(
                            run,
                            1,
                            AnalyticLaboratoryRasterIT.DATE,
                            AnalyticLaboratoryRasterIT.DATE.plusDays(3),
                            POLICIES));
            try (var connection = session.getConnection();
                    var statement =
                            connection.prepareStatement(
                                    "SELECT COUNT_BIG(*),COUNT(DISTINCT dimension_kind),SUM(CASE WHEN dimension_kind='CLIE"
                                            + "NTE' THEN 1 ELSE 0 END),"
                                            + " SUM(CASE WHEN raw_name<>normalized_name COLLATE Latin1_General_100_BIN2 THEN 1 ELSE "
                                            + "0 END)"
                                            + " FROM ref.analytic_lab_registry WHERE reference_release_id=?")) {
                statement.setLong(1, receipt.release());
                statement.setQueryTimeout(10);
                try (var row = statement.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(13, row.getLong(1));
                    assertEquals(6, row.getInt(2));
                    assertEquals(2, row.getInt(3));
                    assertEquals(1, row.getInt(4));
                }
            }
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ref.ufn_analytic_reference(?,1,'20360404')",
                            run));
        }
    }

    @Test
    void changedPolicyCannotReuseAnExistingRevision() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            AnalyticLaboratoryRasterIT.start(session, run);
            final var repository =
                    new JdbcAnalyticReferences(session, AnalyticLaboratoryRasterIT.CLOCK);
            repository.importPackaged(
                    run,
                    1,
                    AnalyticLaboratoryRasterIT.DATE,
                    AnalyticLaboratoryRasterIT.DATE.plusDays(3),
                    POLICIES);
            assertThrows(
                    java.sql.SQLException.class,
                    () ->
                            repository.importPackaged(
                                    run,
                                    1,
                                    AnalyticLaboratoryRasterIT.DATE,
                                    AnalyticLaboratoryRasterIT.DATE.plusDays(3),
                                    new Policies(
                                            Fiscal.UNRESOLVED,
                                            Branch.EXPLICIT_ASSIGNMENT,
                                            Driver.INCLUDE_ALL_BOUND)));
        }
    }

    @Test
    void releaseOfAnotherRunCannotBeSelectedAndSealedEntriesCannotMutate() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            AnalyticLaboratoryRasterIT.start(session, first);
            AnalyticLaboratoryRasterIT.start(session, second);
            final var receipt =
                    new JdbcAnalyticReferences(session, AnalyticLaboratoryRasterIT.CLOCK)
                            .importPackaged(
                                    first,
                                    1,
                                    AnalyticLaboratoryRasterIT.DATE,
                                    AnalyticLaboratoryRasterIT.DATE.plusDays(3),
                                    POLICIES);
            try (var connection = session.getConnection();
                    var statement =
                            connection.prepareStatement(
                                    "INSERT ctl.analytic_lab_reference_selection VALUES(?,1,'20360401','20360404',?,'UNRES"
                                            + "OLVED','UNRESOLVED','UNRESOLVED')")) {
                statement.setString(1, second.toString());
                statement.setLong(2, receipt.release());
                statement.setQueryTimeout(10);
                assertThrows(java.sql.SQLException.class, statement::executeUpdate);
            }
        }
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            AnalyticLaboratoryRasterIT.start(session, run);
            final var receipt =
                    new JdbcAnalyticReferences(session, AnalyticLaboratoryRasterIT.CLOCK)
                            .importPackaged(
                                    run,
                                    1,
                                    AnalyticLaboratoryRasterIT.DATE,
                                    AnalyticLaboratoryRasterIT.DATE.plusDays(3),
                                    POLICIES);
            try (var connection = session.getConnection();
                    var statement =
                            connection.prepareStatement(
                                    "UPDATE ref.analytic_lab_registry SET raw_name=N'changed' WHERE reference_release_id=?")) {
                statement.setLong(1, receipt.release());
                statement.setQueryTimeout(10);
                assertThrows(java.sql.SQLException.class, statement::executeUpdate);
            }
        }
    }
}
