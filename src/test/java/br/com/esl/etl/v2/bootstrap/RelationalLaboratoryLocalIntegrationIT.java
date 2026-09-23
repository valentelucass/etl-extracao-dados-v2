package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalRecomposition;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Real Java/JDBC/SQL; schema is installed separately and every session rolls back. */
class RelationalLaboratoryLocalIntegrationIT {
    private static final LocalDate DATE = LocalDate.of(2036, 4, 1);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2036-04-02T12:00:00Z"), ZoneOffset.UTC);
    private static final CancellationToken NONE = CancellationToken.none();

    @ParameterizedTest
    @ValueSource(strings = {"scenario", "hydrate", "replay", "status"})
    void commandsConsumeRealPipelineAndExposeHonestDisposition(final String command)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(session);
            final var status =
                    RelationalLaboratoryMain.execute(
                            new RelationalLaboratoryMain.Options(command, 2, 3, 2), session, NONE);
            assertEquals(4, status.manifestos());
            assertEquals(4, status.fretes());
            assertEquals(!command.equals("status"), status.complete());
            assertEquals(command.equals("status") ? 0 : 4, status.coletaFretes());
            session.rollback();
            assertEquals(before, counts(session));
        }
    }

    @Test
    void persistedPlanHonorsGapsAndKeepsIncrementalWatermarkSeparate() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = new JdbcRelationalLaboratory(session, CLOCK);
            lab.start(run, policy(), RelationalSyntheticSource.contracts());
            final String watermark =
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.incremental_publication_watermark");
            var executor = new RelationalLaboratoryExecutor(session, run, CLOCK, Clock.systemUTC());
            executor.plan(ExecutionMode.BOOTSTRAP, 1, 2);
            var progress =
                    executor.executePartition(ExecutionMode.BOOTSTRAP, 3, NONE, boundary -> {});
            assertEquals(0, progress.contiguousCompleted());
            assertEquals(1, progress.completed());
            executor = new RelationalLaboratoryExecutor(session, run, CLOCK, Clock.systemUTC());
            executor.executePartition(ExecutionMode.BOOTSTRAP, 1, NONE, boundary -> {});
            progress = executor.executePartition(ExecutionMode.BOOTSTRAP, 2, NONE, boundary -> {});
            assertEquals(3, progress.contiguousCompleted());
            final long captures = lab.status(run).captures();
            executor.executePartition(ExecutionMode.BOOTSTRAP, 2, NONE, boundary -> {});
            assertEquals(captures, lab.status(run).captures());
            for (final var mode :
                    List.of(
                            ExecutionMode.INCREMENTAL,
                            ExecutionMode.BACKFILL,
                            ExecutionMode.REPLAY)) {
                executor.plan(mode, 1, 2);
                for (int ordinal = 1; ordinal <= 3; ordinal++) {
                    executor.executePartition(mode, ordinal, NONE, boundary -> {});
                }
                assertEquals(
                        3,
                        new JdbcRelationalRecomposition(session)
                                .progress(run, mode)
                                .contiguousCompleted());
            }
            assertEquals(6, lab.status(run).coletas());
            assertEquals(6, lab.status(run).coletaFretes());
            assertEquals(
                    watermark,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.incremental_publication_watermark"));
        }
    }

    @ParameterizedTest
    @EnumSource(RelationalLaboratoryExecutor.Boundary.class)
    void reopensFromSqlAfterEveryExecutionBoundary(final RelationalLaboratoryExecutor.Boundary stop)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = new JdbcRelationalLaboratory(session, CLOCK);
            lab.start(run, policy(), RelationalSyntheticSource.contracts());
            final var executor =
                    new RelationalLaboratoryExecutor(session, run, CLOCK, Clock.systemUTC());
            executor.plan(ExecutionMode.BOOTSTRAP, 1, 2);
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            executor.executePartition(
                                    ExecutionMode.BOOTSTRAP,
                                    1,
                                    NONE,
                                    boundary -> {
                                        if (boundary == stop) {
                                            throw new IllegalStateException(
                                                    "SYNTHETIC_BOUNDARY_STOP");
                                        }
                                    }));
            final var reopened =
                    new RelationalLaboratoryExecutor(session, run, CLOCK, Clock.systemUTC());
            reopened.executePartition(ExecutionMode.BOOTSTRAP, 1, NONE, boundary -> {});
            assertEquals(3, lab.status(run).captures());
            assertEquals(2, lab.status(run).coletaFretes());
            assertEquals(
                    1,
                    new JdbcRelationalRecomposition(session)
                            .progress(run, ExecutionMode.BOOTSTRAP)
                            .contiguousCompleted());
        }
    }

    @Test
    void orphanHydratesThroughPipelineResolvesChainAndReplaysWithoutMultiplyingRoots()
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(session);
            final UUID run = UUID.randomUUID();
            final var runtime = runtime(session, run);
            final var lab = runtime.laboratory();
            lab.start(run, policy(), RelationalSyntheticSource.contracts());
            final var manifesto =
                    runtime.capture(
                            DataExportTemplate.MANIFESTOS,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            source(
                                    "{\"sequence_code\":1,\"mft_pfs_pck_sequence_code\":7,"
                                            + "\"created_at\":\"2036-04-01T10:00:00Z\",\"synthetic_fixture\":true}"),
                            NONE);
            runtime.capture(
                    DataExportTemplate.COLETAS,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    source(),
                    NONE);
            runtime.capture(
                    DataExportTemplate.FRETES,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    source(
                            "{\"id\":21,\"criado_em\":\"2036-04-01T10:00:00Z\","
                                    + "\"synthetic_pick_item\":\"item\",\"synthetic_fixture\":true}"),
                    NONE);
            lab.bindBatch(run, bindings(), NONE);
            assertEquals(1, lab.resolve(run, UUID.randomUUID(), NONE).orphaned());
            assertEquals(1, lab.status(run).pending());
            final UUID owner = UUID.randomUUID();
            final var claims =
                    new JdbcRelationalLaboratory(session, CLOCK).claimBatch(run, owner, 2, NONE);
            assertEquals(1, claims.size());
            final var hydrated =
                    runtime.capture(
                            DataExportTemplate.COLETAS,
                            claims.get(0).date(),
                            ExecutionMode.BACKFILL,
                            null,
                            source(
                                    "{\"id\":11,\"sequence_code\":99,\"status\":\"pending\",\"request_date\":\"2036-04-01\","
                                            + "\"status_updated_at\":\"2036-04-01T10:00:00.123456789Z\","
                                            + "\"synthetic_item_key\":0,\"synthetic_fixture\":true}"),
                            NONE);
            final UUID receipt = UUID.randomUUID();
            final var resolved = lab.resolve(run, receipt, NONE);
            assertEquals(2, resolved.resolved());
            lab.finish(
                    run,
                    claims.get(0).backlogId(),
                    owner,
                    JdbcRelationalLaboratory.AttemptResult.RESOLVED,
                    hydrated.executionId());
            final var status = new JdbcRelationalLaboratory(session, CLOCK).status(run);
            assertEquals(1, status.manifestos());
            assertEquals(1, status.coletas());
            assertEquals(1, status.fretes());
            assertEquals(1, status.manifestoColetas());
            assertEquals(1, status.coletaFretes());
            assertTrue(status.complete());
            assertEquals(resolved, lab.resolve(run, receipt, NONE));
            assertEquals(2, lab.resolve(run, UUID.randomUUID(), NONE).noop());
            assertEquals(
                    manifesto.receipt(),
                    lab.capture(run, manifesto.executionId(), UUID.randomUUID(), NONE));
            assertEquals(
                    "123456789",
                    scalar(
                            session,
                            "SELECT nano FROM core.relational_lab_root WHERE entity_name=N'coletas'"));
            session.rollback();
            assertEquals(before, counts(session));
        }
    }

    static RelationalLaboratoryPolicy policy() {
        return new RelationalLaboratoryPolicy(
                DATE, DATE.plusDays(2), 10_000, 4, 3, 20, 2, 1, 100, 200);
    }

    static LocalRelationalRuntime runtime(
            final ColetaTemporalLaboratorySession session, final UUID run) {
        return new LocalRelationalRuntime(session, run, policy(), CLOCK, Clock.systemUTC());
    }

    static List<RelationalBinding> bindings() {
        return List.of(
                new RelationalBinding(
                        "synthetic-mc",
                        RelationalBinding.Relation.MC,
                        RelationalBinding.Key.integer(1),
                        RelationalBinding.Key.integer(7),
                        RelationalBinding.Key.integer(11),
                        RelationalBinding.Key.root(),
                        DATE,
                        1,
                        RelationalBinding.Cardinality.ONE_TO_ONE),
                new RelationalBinding(
                        "synthetic-cf",
                        RelationalBinding.Relation.CF,
                        RelationalBinding.Key.integer(11),
                        RelationalBinding.Key.integer(0),
                        RelationalBinding.Key.integer(21),
                        new RelationalBinding.Key(RelationalBinding.WireType.STRING, "item"),
                        DATE,
                        1,
                        RelationalBinding.Cardinality.ONE_TO_ONE));
    }

    static RelationalSyntheticSource source(final String... rows) {
        final String page = "[" + String.join(",", rows) + "]";
        return new RelationalSyntheticSource(number -> number == 1 ? page : "[]");
    }

    static String scalar(final ColetaTemporalLaboratorySession session, final String query)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql = connection.createStatement()) {
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery(query)) {
                assertTrue(row.next());
                return row.getString(1);
            }
        }
    }

    static String counts(final ColetaTemporalLaboratorySession session) throws SQLException {
        return scalar(
                session,
                """
                SELECT CONCAT((SELECT COUNT_BIG(*) FROM ctl.execution_attempt),N'|',
                    (SELECT COUNT_BIG(*) FROM ctl.execution_audit),N'|',
                    (SELECT COUNT_BIG(*) FROM ctl.relational_lab_run),N'|',
                    (SELECT COUNT_BIG(*) FROM core.relational_lab_root),N'|',
                    (SELECT COUNT_BIG(*) FROM core.relational_lab_link),N'|',
                    (SELECT COUNT_BIG(*) FROM ctl.relational_lab_backlog))
                """);
    }
}
