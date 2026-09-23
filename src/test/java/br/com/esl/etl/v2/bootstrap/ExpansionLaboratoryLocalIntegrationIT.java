package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Physical tests never install schema; every invocation proves rollback on the existing target. */
class ExpansionLaboratoryLocalIntegrationIT {
    static final LocalDate DATE = LocalDate.of(2036, 4, 1);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2036-04-15T12:00:00Z"), ZoneOffset.UTC);
    static final CancellationToken NONE = CancellationToken.none();

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void completeCrossPageExpansionAppliesOnceReplaysAndRollsBack(final DataExportTemplate template)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final long before = scalar(session, "SELECT COUNT_BIG(*) FROM core.expansion_lab_root");
            final UUID run = UUID.randomUUID();
            final var runtime = runtime(session, run, 2, 100);
            final var capture =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            ExpansionLaboratoryFixtures.source(template, 2, 2),
                            NONE);
            assertEquals(6, capture.receipt().observations());
            assertEquals(4, capture.receipt().inserts());
            assertEquals(2, capture.receipt().duplicates());
            assertEquals(0, capture.receipt().quarantine());
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
            assertEquals(
                    200,
                    scalar(
                            session,
                            "SELECT CONVERT(bigint,SUM(amount)) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
            final var replay =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.REPLAY,
                            capture.executionId(),
                            ExpansionLaboratoryFixtures.source(template, 2, 1),
                            NONE);
            assertEquals(4, replay.receipt().noops());
            assertEquals(0, replay.receipt().updates());
            assertEquals(
                    capture.receipt(),
                    new JdbcExpansionLaboratory(session, CLOCK).apply(run, capture.executionId()));
            session.rollback();
            assertEquals(
                    before, scalar(session, "SELECT COUNT_BIG(*) FROM core.expansion_lab_root"));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void completeEmptyIsDifferentFromPageFailureAndNeverSealsPartial(
            final DataExportTemplate template) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = runtime(session, run, 2, 100);
            final var empty =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            ExpansionLaboratoryFixtures.source(template, 0, 2),
                            NONE);
            assertEquals(0, empty.receipt().observations());
            assertEquals(1, empty.metrics().fetchedPages());
            final var data = ExpansionLaboratoryFixtures.data(template);
            final var one = ExpansionLaboratoryFixtures.envelope(template, data, 1, 1, 1);
            final var source =
                    new ExpansionSyntheticSource(
                            page -> {
                                if (page == 1) {
                                    return "[" + one + "]";
                                }
                                throw new IllegalStateException("SYNTHETIC_PAGE_FAILURE");
                            });
            assertThrows(
                    RuntimeException.class,
                    () ->
                            runtime.capture(
                                    template, DATE, ExecutionMode.BACKFILL, null, source, NONE));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_capture WHERE run_id=?",
                            run));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM stg.expansion_lab_observation WHERE run_id=?",
                            run));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void unboundObservationNeverPromotesSyntheticOrSourceCandidate(
            final DataExportTemplate template) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = runtime(session, run, 2, 100);
            final var row =
                    ExpansionLaboratoryFixtures.envelope(
                            template, ExpansionLaboratoryFixtures.data(template), 1, 1, 1);
            row.remove("binding");
            final var capture =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            new ExpansionSyntheticSource(
                                    page -> page == 1 ? "[" + row + "]" : "[]"),
                            NONE);
            assertEquals(1, capture.receipt().unbound());
            assertEquals(0, capture.receipt().inserts());
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
            assertTrue(session.releasedConnections() > 0);
        }
    }

    static LocalExpansionRuntime runtime(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final int pageSize,
            final int rows)
            throws SQLException {
        final var policy =
                new ExpansionPolicy(
                        DATE,
                        DATE.plusDays(3),
                        DATE.plusDays(14),
                        pageSize,
                        100,
                        rows,
                        FiscalPolicy.UNRESOLVED);
        new JdbcExpansionLaboratory(session, CLOCK).start(run, policy);
        return new LocalExpansionRuntime(session, run, policy, CLOCK, Clock.systemUTC());
    }

    static long scalar(
            final ColetaTemporalLaboratorySession session, final String sql, final UUID... run)
            throws SQLException {
        try (var connection = session.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(10);
            if (run.length == 1) {
                statement.setString(1, run[0].toString());
            }
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("EXP_PROBE_EMPTY");
                }
                return rows.getLong(1);
            }
        }
    }
}
