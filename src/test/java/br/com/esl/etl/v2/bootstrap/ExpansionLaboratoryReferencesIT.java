package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionReferences;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpansionLaboratoryReferencesIT {
    @Test
    void sealedReferencesReplayAndJoinCapturedLabelsWithoutInferringUnknowns() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            runtime.capture(
                    DataExportTemplate.FATURAS_POR_CLIENTE,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    ExpansionLaboratoryFixtures.source(
                            DataExportTemplate.FATURAS_POR_CLIENTE, 1, 3),
                    NONE);
            final var references = new JdbcExpansionReferences(session, CLOCK);
            final var imported = references.importPackaged(run, 1, DATE, DATE.plusDays(10));
            assertEquals(imported, references.importPackaged(run, 1, DATE, DATE.plusDays(10)));
            assertEquals(
                    4,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_reference_selection WHERE run_id=?",
                            run));
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root r JOIN "
                                    + "core.expansion_lab_component c ON c.root_id=r.root_id JOIN "
                                    + "stg.expansion_lab_observation o ON o.observation_id=c.observation_id JOIN "
                                    + "stg.expansion_lab_fat f ON f.execution_id=o.execution_id AND "
                                    + "f.occurrence=o.occurrence CROSS APPLY ref.ufn_expansion_reference(r.run_id,1,"
                                    + "'LABELS',f.fit_ant_issue_date) s JOIN ref.expansion_lab_label l ON "
                                    + "l.reference_release_id=s.reference_release_id AND l.category='FAT_CTE' AND "
                                    + "l.raw_value=f.fit_fhe_cte_status COLLATE Latin1_General_100_BIN2 WHERE "
                                    + "r.run_id=? AND l.label=N'Autorizado'",
                            run));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ref.ufn_expansion_reference(?,1,'LABELS',"
                                    + "'20360401') s JOIN ref.expansion_lab_label l ON "
                                    + "l.reference_release_id=s.reference_release_id WHERE "
                                    + "l.raw_value=N'UNKNOWN_STATUS'",
                            run));
        }
    }

    @Test
    void calendarHasManualWeekendOracleAndSelectionHasExclusiveBoundary() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            new JdbcExpansionReferences(session, CLOCK)
                    .importPackaged(run, 1, DATE, DATE.plusDays(10));
            // Saturday 2036-04-05 retreats to Friday 2036-04-04; no copied production expression.
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ref.ufn_expansion_reference(?,1,'CALENDAR',"
                                    + "'20360405') s JOIN ref.calendario c ON "
                                    + "c.reference_release_id=s.reference_release_id WHERE c.calendar_date='20360405' "
                                    + "AND c.is_business_day=0 AND c.billing_reference_date='20360404'",
                            run));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ref.ufn_expansion_reference(?,1,'CALENDAR','20360411')",
                            run));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ref.ufn_expansion_reference(?,2,'CALENDAR','20360405')",
                            run));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT COUNT_BIG(*) FROM ref.ufn_expansion_reference(?,1,'PAYER',"
                                            + "'20360405') s JOIN ref.atribuicao_filial p ON "
                                            + "p.reference_release_id=s.reference_release_id JOIN ref.filial_operacional b ON "
                                            + "b.reference_release_id=p.branch_reference_release_id AND "
                                            + "b.branch_code=p.branch_code WHERE "
                                            + "p.payer_document_token=? AND b.branch_code='SYNTHETIC_BRANCH'")) {
                sql.setQueryTimeout(10);
                sql.setString(1, run.toString());
                sql.setString(
                        2, "0000000000000000000000000000000000000000000000000000000000000001");
                try (var rows = sql.executeQuery()) {
                    org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                    assertEquals(1, rows.getLong(1));
                }
            }
        }
    }

    @Test
    void sameReleaseVersionDivergenceIsRejected() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            final var references = new JdbcExpansionReferences(session, CLOCK);
            references.importPackaged(run, 1, DATE, DATE.plusDays(10));
            assertThrows(
                    SQLException.class,
                    () -> references.importPackaged(run, 1, DATE, DATE.plusDays(11)));
        }
    }

    @Test
    void selectionCannotOverlapOrMutateSealedContent() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            new JdbcExpansionReferences(session, CLOCK)
                    .importPackaged(run, 1, DATE, DATE.plusDays(10));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "INSERT ctl.expansion_lab_reference_selection SELECT run_id,revision,purpose,"
                                            + "'20360402',valid_to_exclusive,"
                                            + "reference_release_id FROM ctl.expansion_lab_reference_selection WHERE run_id=? "
                                            + "AND purpose='CALENDAR'")) {
                sql.setQueryTimeout(10);
                sql.setString(1, run.toString());
                assertThrows(SQLException.class, sql::executeUpdate);
            }
        }
    }
}
