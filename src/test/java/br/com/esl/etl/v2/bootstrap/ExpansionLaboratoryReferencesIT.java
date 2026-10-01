package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionReferences;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
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
    void caseOnlyLabelReplayUsesContentComparisonAndRollsBack() throws SQLException {
        final UUID run = UUID.randomUUID();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            assertTrue(ReferenceState.empty().equals(referenceState(session, run)));
            ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            final var references = new JdbcExpansionReferences(session, CLOCK);
            final var imported = references.importPackaged(run, 1, DATE, DATE.plusDays(10));
            assertTrue(imported.equals(references.importPackaged(run, 1, DATE, DATE.plusDays(10))));
            final var before = referenceState(session, run);
            assertEquals(4, before.releases());
            assertEquals(4, before.receipts());
            assertEquals(15, before.labels());
            assertEquals(4, before.selections());
            assertTrue(
                    Set.of(
                                    imported.labels(),
                                    imported.calendar(),
                                    imported.branch(),
                                    imported.payer())
                            .equals(before.selectedIds()));

            final var replay = caseOnlyReplay(session, imported);
            try (var connection = session.getConnection();
                    var sql =
                            (SQLServerPreparedStatement)
                                    connection.prepareStatement(
                                            "EXEC ref.usp_import_expansion_references ?,?,?,?,?,?,?,?,?,?")) {
                sql.setQueryTimeout(20);
                sql.setString(1, run.toString());
                sql.setInt(2, 1);
                sql.setObject(3, DATE);
                sql.setObject(4, DATE.plusDays(10));
                sql.setString(5, replay.fingerprint());
                sql.setStructured(6, "ref.expansion_lab_label_batch", replay.labels());
                sql.setString(7, replay.branchCode());
                sql.setString(8, replay.branchLabel());
                sql.setString(9, replay.payerToken());
                sql.setTimestamp(
                        10,
                        Timestamp.from(CLOCK.instant()),
                        Calendar.getInstance(TimeZone.getTimeZone("UTC")));
                final var rejected = assertThrows(SQLException.class, sql::executeQuery);
                assertEquals(53437, rejected.getErrorCode());
                assertTrue(rejected.getMessage().contains("EXP_REF_CONTENT_DIVERGENT"));
            }
            final long transactionState = scalar(session, "SELECT XACT_STATE()");
            assertTrue(transactionState == -1 || transactionState == 0);
            final var after = referenceState(session, run);
            // XACT_ABORT can leave the transaction doomed or roll it back immediately.
            assertTrue(
                    (transactionState == -1 && before.equals(after))
                            || (transactionState == 0 && ReferenceState.empty().equals(after)));
        }
        try (var readback = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            assertTrue(ReferenceState.empty().equals(referenceState(readback, run)));
        }
    }

    private static CaseOnlyReplay caseOnlyReplay(
            final ColetaTemporalLaboratorySession session,
            final JdbcExpansionReferences.Releases imported)
            throws SQLException {
        final var labels = new SQLServerDataTable();
        labels.addColumnMetadata("category", Types.VARCHAR);
        labels.addColumnMetadata("raw_value", Types.NVARCHAR);
        labels.addColumnMetadata("label", Types.NVARCHAR);
        final String fingerprint;
        final String branchCode;
        final String branchLabel;
        final String payerToken;
        try (var connection = session.getConnection();
                var metadata =
                        connection.prepareStatement(
                                "SELECT r.source_fingerprint,b.branch_code,b.branch_label,p.payer_document_token "
                                        + "FROM ref.reference_release r CROSS JOIN ref.filial_operacional b "
                                        + "CROSS JOIN ref.atribuicao_filial p WHERE r.reference_release_id=? "
                                        + "AND b.reference_release_id=? AND p.reference_release_id=?");
                var source =
                        connection.prepareStatement(
                                "SELECT category,raw_value,label FROM ref.expansion_lab_label "
                                        + "WHERE reference_release_id=? ORDER BY category,raw_value")) {
            metadata.setQueryTimeout(10);
            metadata.setLong(1, imported.labels());
            metadata.setLong(2, imported.branch());
            metadata.setLong(3, imported.payer());
            try (var rows = metadata.executeQuery()) {
                assertTrue(rows.next());
                fingerprint = rows.getString(1);
                branchCode = rows.getString(2);
                branchLabel = rows.getString(3);
                payerToken = rows.getString(4);
                assertTrue(!rows.next());
            }
            source.setQueryTimeout(10);
            source.setLong(1, imported.labels());
            int changed = 0;
            try (var rows = source.executeQuery()) {
                while (rows.next()) {
                    final String label = rows.getString(3);
                    final boolean change = label.equals("Autorizado");
                    labels.addRow(
                            rows.getString(1), rows.getString(2), change ? "autorizado" : label);
                    if (change) {
                        changed++;
                    }
                }
            }
            assertEquals(1, changed);
        }
        return new CaseOnlyReplay(fingerprint, branchCode, branchLabel, payerToken, labels);
    }

    private static ReferenceState referenceState(
            final ColetaTemporalLaboratorySession session, final UUID run) throws SQLException {
        final long releases =
                scalar(
                        session,
                        "SELECT COUNT_BIG(*) FROM ref.reference_release WHERE scope_code="
                                + "CONCAT(N'SYNTHETIC_EXPANSION_LAB:',CONVERT(NVARCHAR(36),CONVERT(UNIQUEIDENTIFIER,?)))",
                        run);
        final long receipts =
                scalar(
                        session,
                        "SELECT COUNT_BIG(*) FROM ref.reference_import_receipt i JOIN "
                                + "ref.reference_release r ON r.reference_release_id=i.reference_release_id "
                                + "WHERE r.scope_code=CONCAT(N'SYNTHETIC_EXPANSION_LAB:',"
                                + "CONVERT(NVARCHAR(36),CONVERT(UNIQUEIDENTIFIER,?)))",
                        run);
        final long labels =
                scalar(
                        session,
                        "SELECT COUNT_BIG(*) FROM ref.expansion_lab_label l JOIN ref.reference_release r "
                                + "ON r.reference_release_id=l.reference_release_id WHERE "
                                + "r.scope_code=CONCAT(N'SYNTHETIC_EXPANSION_LAB:',CONVERT(NVARCHAR(36),CONVERT(UNIQUEIDENTIFIER,?)))",
                        run);
        final long selections =
                scalar(
                        session,
                        "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_reference_selection WHERE run_id=?",
                        run);
        final var ids = new HashSet<Long>();
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT reference_release_id FROM ctl.expansion_lab_reference_selection "
                                        + "WHERE run_id=? AND revision=1")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    assertTrue(ids.add(rows.getLong(1)));
                }
            }
        }
        return new ReferenceState(releases, receipts, labels, selections, Set.copyOf(ids));
    }

    private record CaseOnlyReplay(
            String fingerprint,
            String branchCode,
            String branchLabel,
            String payerToken,
            SQLServerDataTable labels) {}

    private record ReferenceState(
            long releases, long receipts, long labels, long selections, Set<Long> selectedIds) {
        private static ReferenceState empty() {
            return new ReferenceState(0, 0, 0, 0, Set.of());
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
