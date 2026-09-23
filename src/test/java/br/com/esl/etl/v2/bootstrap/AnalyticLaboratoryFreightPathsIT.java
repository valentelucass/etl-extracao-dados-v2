package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticFreightRelationBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFreightRelationBinding.Kind;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFreightRelations;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryFreightPathsIT {
    @Test
    void differingSourceKeysCrosswalkExplicitlyAndDirectSetIsSealedWithoutCopyingFacts()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var setup = setup(session);
            final var repository = new JdbcAnalyticFreightRelations(session);
            final var bindings =
                    List.of(
                            binding(
                                    setup,
                                    Kind.CROSSWALK,
                                    "INTEGER:300101",
                                    setup.relationalFreight(),
                                    "INTEGER:300001",
                                    1,
                                    null),
                            binding(
                                    setup,
                                    Kind.DIRECT,
                                    "INTEGER:1",
                                    setup.manifest(),
                                    "INTEGER:300001",
                                    1,
                                    null));
            assertEquals(
                    2,
                    repository.bindBatch(
                            setup.fixture().run(), bindings, CancellationToken.none()));
            assertEquals(
                    2,
                    repository.bindBatch(
                            setup.fixture().run(), bindings, CancellationToken.none()));
            final long seal =
                    repository.sealComposition(
                            setup.fixture().run(), "INTEGER:1", setup.manifest(), 1, 1);
            assertTrue(seal > 0);
            assertEquals(
                    seal,
                    repository.sealComposition(
                            setup.fixture().run(), "INTEGER:1", setup.manifest(), 1, 1));
            assertEquals(
                    2,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_freight_relation_current WHERE run_id=? AN"
                                    + "D active=1",
                            setup.fixture().run()));
            assertEquals(
                    1,
                    repository.bindBatch(
                            setup.fixture().run(),
                            List.of(
                                    binding(
                                            setup,
                                            Kind.CROSSWALK,
                                            "INTEGER:300101",
                                            setup.relationalFreight(),
                                            "INTEGER:300002",
                                            2,
                                            "INTEGER:300001")),
                            CancellationToken.none()));
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_freight_relation_current WHERE run_id=? AN"
                                    + "D kind='CROSSWALK' AND freight_key='INTEGER:300002' AND revision=2",
                            setup.fixture().run()));
        }
    }

    @Test
    void cardinalityAndRekeyWithoutEvidenceAreRefused() throws Exception {
        for (int variant = 0; variant < 2; variant++) {
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                final var setup = setup(session);
                final var repository = new JdbcAnalyticFreightRelations(session);
                repository.bindBatch(
                        setup.fixture().run(),
                        List.of(
                                binding(
                                        setup,
                                        Kind.CROSSWALK,
                                        "INTEGER:300101",
                                        setup.relationalFreight(),
                                        "INTEGER:300001",
                                        1,
                                        null)),
                        CancellationToken.none());
                final var invalid =
                        variant == 0
                                ? binding(
                                        setup,
                                        Kind.CROSSWALK,
                                        "INTEGER:300102",
                                        setup.relationalFreight(),
                                        "INTEGER:300001",
                                        1,
                                        null)
                                : binding(
                                        setup,
                                        Kind.CROSSWALK,
                                        "INTEGER:300101",
                                        setup.relationalFreight(),
                                        "INTEGER:300002",
                                        2,
                                        null);
                final var failure =
                        assertThrows(
                                SQLException.class,
                                () ->
                                        repository.bindBatch(
                                                setup.fixture().run(),
                                                List.of(invalid),
                                                CancellationToken.none()));
                assertEquals(variant == 0 ? 53644 : 53643, failure.getErrorCode());
            }
        }
    }

    @Test
    void sealedDirectSetRejectsAnotherEdgeAtTheSameRevision() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var setup = setup(session);
            final var repository = new JdbcAnalyticFreightRelations(session);
            repository.bindBatch(
                    setup.fixture().run(),
                    List.of(
                            binding(
                                    setup,
                                    Kind.DIRECT,
                                    "INTEGER:1",
                                    setup.manifest(),
                                    "INTEGER:300001",
                                    1,
                                    null)),
                    CancellationToken.none());
            repository.sealComposition(setup.fixture().run(), "INTEGER:1", setup.manifest(), 1, 1);
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    repository.bindBatch(
                                            setup.fixture().run(),
                                            List.of(
                                                    binding(
                                                            setup,
                                                            Kind.DIRECT,
                                                            "INTEGER:1",
                                                            setup.manifest(),
                                                            "INTEGER:300002",
                                                            1,
                                                            null)),
                                            CancellationToken.none()));
            assertEquals(53642, failure.getErrorCode());
        }
    }

    private static AnalyticFreightRelationBinding binding(
            final Setup setup,
            final Kind kind,
            final String origin,
            final UUID originExecution,
            final String target,
            final int revision,
            final String previous) {
        return new AnalyticFreightRelationBinding(
                kind,
                origin,
                originExecution,
                target,
                setup.fixture().freight(),
                revision,
                true,
                previous);
    }

    static Setup setup(final ColetaTemporalLaboratorySession session) throws Exception {
        final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
        final var manifest =
                AnalyticLaboratoryManifestPreparationIT.runtime(session, fixture)
                        .capture(
                                DATE,
                                ExecutionMode.BOOTSTRAP,
                                null,
                                AnalyticLaboratoryManifestPreparationIT.source(
                                        AnalyticLaboratoryManifestCaptureIT.manifest()));
        final var relational =
                new LocalRelationalRuntime(
                        session,
                        fixture.relational(),
                        AnalyticLaboratoryManifestPreparationIT.policy(),
                        CLOCK,
                        Clock.systemUTC());
        final var freights =
                relational.capture(
                        DataExportTemplate.FRETES,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        RelationalLaboratoryFixtures.source(
                                DataExportTemplate.FRETES, DATE, 101, 2, 2, false),
                        CancellationToken.none());
        return new Setup(fixture, manifest.source().executionId(), freights.executionId());
    }

    record Setup(
            AnalyticLaboratoryDimensionsIT.Fixture fixture,
            UUID manifest,
            UUID relationalFreight) {}
}
