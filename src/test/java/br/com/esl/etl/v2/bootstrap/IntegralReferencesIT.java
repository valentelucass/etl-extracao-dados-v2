package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuoteTariffs;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IntegralReferencesIT {
    @TempDir Path folder;

    @Test
    @Timeout(120)
    void explicitTariffReleaseRejectsChangedRetryOverlappingSelectionAndAmbiguousRoute()
            throws Exception {
        final var input = input();
        final Path tariffsFile = folder.resolve("references/tariffs.json");
        final byte[] tariffs = Files.readAllBytes(tariffsFile);
        final var changed = (ObjectNode) IntegralArtifactFixtures.read(tariffsFile);
        ((ObjectNode) changed.path("rows").path(0)).put("amount", "99.99");
        final var duplicate = (ObjectNode) IntegralArtifactFixtures.read(tariffsFile);
        ((com.fasterxml.jackson.databind.node.ArrayNode) duplicate.path("rows"))
                .add(duplicate.path("rows").path(0).deepCopy());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var run = runtime(session, input).start(2, 2);
            final var importer = new JdbcAnalyticQuoteTariffs(session);
            assertEquals(
                    run.tariff(),
                    importer.importFixture(run.id(), 2, input.start(), input.end(), tariffs)
                            .release());
            assertEquals(
                    53705,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            importer.importFixture(
                                                    run.id(),
                                                    2,
                                                    input.start(),
                                                    input.end(),
                                                    changed.toString()
                                                            .getBytes(
                                                                    java.nio.charset
                                                                            .StandardCharsets
                                                                            .UTF_8)))
                            .getErrorCode());
            assertEquals(
                    53702,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            importer.importFixture(
                                                    run.id(),
                                                    2,
                                                    input.start().plusDays(1),
                                                    input.end(),
                                                    tariffs))
                            .getErrorCode());
            assertThrows(
                    SQLException.class,
                    () ->
                            importer.importFixture(
                                    run.id(),
                                    3,
                                    input.start(),
                                    input.end(),
                                    duplicate
                                            .toString()
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_run WHERE run_id=?"));
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_scenario_cycle WHERE run_id=?"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(180)
    void foreignOrDifferentRevisionCannotPublishQuotesInTheIntegralChain(final boolean foreign)
            throws Exception {
        final var input = input();
        final byte[] tariffs = Files.readAllBytes(folder.resolve("references/tariffs.json"));
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var runtime = runtime(session, input);
            final var run = runtime.start(2, 2);
            final long rejected =
                    foreign
                            ? runtime.start(2, 2).tariff()
                            : new JdbcAnalyticQuoteTariffs(session)
                                    .importFixture(
                                            run.id(),
                                            3,
                                            input.start().plusDays(1),
                                            input.end(),
                                            tariffs)
                                    .release();
            final var wrong =
                    new AnalyticScenarioRuntime.Run(
                            run.id(),
                            run.expansion(),
                            run.relational(),
                            run.roots(),
                            run.pageSize(),
                            rejected,
                            run.fault(),
                            run.variant());
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    runtime.capture(
                                            wrong,
                                            ExecutionMode.BOOTSTRAP,
                                            input.revision(),
                                            false,
                                            null,
                                            CancellationToken.none()));
            assertTrue(hasCode(failure, 53721), failure::getMessage);
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_scenario_cycle WHERE run_id=? AND state='COMPLETE'"));
            assertEquals(
                    0,
                    IntegralArtifactResilienceIT.count(
                            session,
                            run.id(),
                            "SELECT COUNT_BIG(*) FROM mart.analytic_freight_operational WHERE run_id=?"));
            assertEquals(0, session.openControlledStatements());
        }
    }

    private DeclaredIntegralInputs input() throws Exception {
        return new DeclaredIntegralInputs(
                PinnedLocalJson.open(IntegralArtifactFixtures.write(folder, false, 2), 16384),
                CancellationToken.none());
    }

    private static AnalyticScenarioRuntime runtime(
            final ColetaTemporalLaboratorySession session, final DeclaredIntegralInputs input) {
        return new AnalyticScenarioRuntime(
                session, Clock.systemUTC(), AnalyticScenarioObserver.NONE, input);
    }

    private static boolean hasCode(final Throwable failure, final int code) {
        for (Throwable cursor = failure; cursor != null; cursor = cursor.getCause()) {
            if (cursor instanceof SQLException sql && sql.getErrorCode() == code) {
                return true;
            }
        }
        return false;
    }
}
