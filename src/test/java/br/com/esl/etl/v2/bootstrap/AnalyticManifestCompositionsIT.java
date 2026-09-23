package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticFreightRelationBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFreightRelationBinding.Kind;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFreightRelations;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticManifestCompositions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticManifestCompositions.Declaration;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticManifestCompositionsIT {
    @Test
    void batchSealsCapturedRelationsAtomicallyAndRejectsDivergentReplayWithoutLosingSession()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryDimensionsIT.start(session, true);
            final var manifest =
                    AnalyticLaboratoryManifestPreparationIT.runtime(session, f)
                            .runtime()
                            .capture(
                                    DATE,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    AnalyticScenarioFixtures.manifests(1, 2, 2, 1, false),
                                    CancellationToken.none())
                            .source()
                            .executionId();
            new JdbcAnalyticFreightRelations(session)
                    .bindBatch(
                            f.run(),
                            List.of(
                                    new AnalyticFreightRelationBinding(
                                            Kind.DIRECT,
                                            "INTEGER:1",
                                            manifest,
                                            "INTEGER:300001",
                                            f.freight(),
                                            1,
                                            true,
                                            null),
                                    new AnalyticFreightRelationBinding(
                                            Kind.DIRECT,
                                            "INTEGER:2",
                                            manifest,
                                            "INTEGER:300002",
                                            f.freight(),
                                            1,
                                            true,
                                            null)),
                            CancellationToken.none());
            final var declarations =
                    List.of(
                            new Declaration("INTEGER:1", manifest, 1, 1),
                            new Declaration("INTEGER:2", manifest, 1, 1));
            final var repository = new JdbcAnalyticManifestCompositions(session);
            assertEquals(2, repository.seal(f.run(), declarations, CancellationToken.none()));
            assertEquals(2, repository.seal(f.run(), declarations, CancellationToken.none()));
            assertEquals(
                    53649,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            repository.seal(
                                                    f.run(),
                                                    List.of(
                                                            new Declaration(
                                                                    "INTEGER:1", manifest, 1, 2)),
                                                    CancellationToken.none()))
                            .getErrorCode());
            assertEquals(
                    53646,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            repository.seal(
                                                    f.run(),
                                                    List.of(
                                                            new Declaration(
                                                                    "INTEGER:1", manifest, 2, 1),
                                                            new Declaration(
                                                                    "INTEGER:2", manifest, 2, 2)),
                                                    CancellationToken.none()))
                            .getErrorCode());
            assertEquals(1, scalar(session, "SELECT XACT_STATE()"));
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_manifest_composition WHERE run_id=?",
                            f.run()));
            assertEquals(2, repository.seal(f.run(), declarations, CancellationToken.none()));
        }
    }
}
