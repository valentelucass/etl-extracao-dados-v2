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
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionDependencies;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ExpansionLaboratoryDependenciesIT {
    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"FRETES", "LOCALIZACAO_CARGAS"})
    void existingPipelinesSupplyCompleteDedupedDependenciesAndExactReplay(
            final DataExportTemplate template) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = runtime(session, run);
            final var first =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            ExpansionDependencyFixtures.source(template, 2, 3),
                            NONE);
            assertEquals(4, first.receipt().observed());
            assertEquals(2, first.receipt().inserts());
            assertEquals(2, first.receipt().duplicates());
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_dependency WHERE run_id=?",
                            run));
            final var replay =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.REPLAY,
                            first.executionId(),
                            ExpansionDependencyFixtures.source(template, 2, 1),
                            NONE);
            assertEquals(2, replay.receipt().noops());
            assertEquals(0, replay.receipt().updates());
            assertEquals(
                    first.receipt(),
                    new JdbcExpansionDependencies(session, CLOCK)
                            .sealAndApply(run, first.executionId()));
            if (template == DataExportTemplate.FRETES) {
                assertEquals(
                        123456789,
                        scalar(
                                session,
                                "SELECT MIN(fresh_nano) FROM stg.expansion_lab_dependency_observation WHERE run_id=?",
                                run));
            }
            assertTrue(session.releasedConnections() > 0);
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"FRETES", "LOCALIZACAO_CARGAS"})
    void sameTimeDivergenceBlocksConsumerAndNewerCorrectionRecovers(
            final DataExportTemplate template) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = runtime(session, run);
            runtime.capture(
                    template,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    ExpansionDependencyFixtures.source(template, 1, 3),
                    NONE);
            final var correction = ExpansionDependencyFixtures.data(template, 1);
            correction.put("total", "121.00");
            final var conflict =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.INCREMENTAL,
                            null,
                            new ExpansionDependencySource(
                                    page -> page == 1 ? "[" + correction + "]" : "[]"),
                            NONE);
            assertEquals(1, conflict.receipt().quarantine());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_dependency WHERE run_id=? AND "
                                    + "state='CONFLICT'",
                            run));
            correction.put(
                    template == DataExportTemplate.FRETES ? "cte_created_at" : "service_at",
                    "2036-04-01T13:00:00.123456790Z");
            final var update =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.INCREMENTAL,
                            null,
                            new ExpansionDependencySource(
                                    page -> page == 1 ? "[" + correction + "]" : "[]"),
                            NONE);
            assertEquals(1, update.receipt().updates());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_dependency WHERE run_id=? AND state='VALID'",
                            run));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"FRETES", "LOCALIZACAO_CARGAS"})
    void emptyMinimalAndPartialAreDistinct(final DataExportTemplate template) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = runtime(session, run);
            final var empty =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            ExpansionDependencyFixtures.source(template, 0, 3),
                            NONE);
            assertEquals(0, empty.receipt().observed());
            final String key =
                    template == DataExportTemplate.FRETES ? "INTEGER:300002" : "INTEGER:600002";
            final var minimal =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.BACKFILL,
                            null,
                            ExpansionDependencyFixtures.hydration(template, key),
                            NONE);
            assertEquals(1, minimal.receipt().observed());
            assertEquals(1, minimal.receipt().inserts());
            assertThrows(
                    RuntimeException.class,
                    () ->
                            runtime.capture(
                                    template,
                                    DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    new ExpansionDependencySource(
                                            page -> {
                                                if (page == 1) {
                                                    return "["
                                                            + ExpansionDependencyFixtures.data(
                                                                    template, 3)
                                                            + "]";
                                                }
                                                throw new IllegalStateException(
                                                        "SYNTHETIC_PARTIAL");
                                            }),
                                    NONE));
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_dependency_capture WHERE run_id=?",
                            run));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_dependency WHERE run_id=?",
                            run));
        }
    }

    static LocalExpansionDependencyRuntime runtime(
            final ColetaTemporalLaboratorySession session, final UUID run) throws SQLException {
        ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
        return new LocalExpansionDependencyRuntime(
                session,
                run,
                new JdbcExpansionLaboratory(session, CLOCK).policy(run),
                CLOCK,
                Clock.systemUTC());
    }
}
