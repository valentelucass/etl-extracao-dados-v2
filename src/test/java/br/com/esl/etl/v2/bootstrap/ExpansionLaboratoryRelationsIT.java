package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionKey;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation.Cardinality;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation.Kind;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations.Outcome;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpansionLaboratoryRelationsIT {
    @Test
    void allFourCapturesAndExplicitRelationshipsHydrateOnlyTheMissingTarget() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            for (final var template :
                    List.of(
                            DataExportTemplate.CONTAS_A_PAGAR,
                            DataExportTemplate.FATURAS_POR_CLIENTE,
                            DataExportTemplate.INVENTARIO,
                            DataExportTemplate.SINISTROS)) {
                runtime.capture(
                        template,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        ExpansionLaboratoryFixtures.source(template, 2, 3),
                        NONE);
            }
            final var dependency = dependency(session, run);
            dependency.capture(
                    DataExportTemplate.LOCALIZACAO_CARGAS,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    ExpansionDependencyFixtures.source(DataExportTemplate.LOCALIZACAO_CARGAS, 2, 3),
                    NONE);
            dependency.capture(
                    DataExportTemplate.FRETES,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, 1, 3),
                    NONE);
            final var relations = new JdbcExpansionRelations(session, CLOCK);
            final var bindings = bindings(2);
            relations.bind(run, bindings, NONE);
            relations.bind(run, bindings, NONE);
            assertEquals(
                    14,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM stg.expansion_lab_relation WHERE run_id=?",
                            run));
            assertEquals(
                    new JdbcExpansionRelations.Resolution(14, 7, 7, 0), relations.resolve(run));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_queue WHERE run_id=?",
                            run));
            final var hydration =
                    new ExpansionLaboratoryHydrator(relations, dependency).hydrate(run, 3, NONE);
            assertEquals(1, hydration.claimed());
            assertEquals(1, hydration.completed());
            assertEquals(1, hydration.capturedObservations());
            assertEquals(
                    new JdbcExpansionRelations.Resolution(14, 14, 0, 0), hydration.resolution());
            assertEquals(
                    0,
                    new ExpansionLaboratoryHydrator(relations, dependency)
                            .hydrate(run, 3, NONE)
                            .claimed());
            assertEquals(
                    8,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
            assertEquals(
                    800,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(amount)) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
        }
    }

    @Test
    void revisionAndCardinalityConflictsNeverChooseAnAliasOrFirstTarget() throws SQLException {
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
            final var relations = new JdbcExpansionRelations(session, CLOCK);
            final var first =
                    binding(
                            Kind.FAT_DOCUMENT_FREIGHT,
                            1,
                            1,
                            1,
                            Cardinality.ONE_TO_ONE,
                            "synthetic-one",
                            1);
            final var second =
                    binding(
                            Kind.FAT_DOCUMENT_FREIGHT,
                            1,
                            1,
                            2,
                            Cardinality.ONE_TO_ONE,
                            "synthetic-two",
                            1);
            relations.bind(run, List.of(first, second), NONE);
            assertEquals(2, relations.resolve(run).conflicts());
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_queue WHERE run_id=?",
                            run));
            final var revised =
                    binding(
                            Kind.FAT_DOCUMENT_FREIGHT,
                            1,
                            1,
                            1,
                            Cardinality.ONE_TO_ONE,
                            "synthetic-two",
                            2);
            relations.bind(run, List.of(revised), NONE);
            assertEquals(2, relations.resolve(run).missing());
            final var orphan =
                    binding(
                            Kind.FAT_DOCUMENT_FREIGHT,
                            99,
                            1,
                            1,
                            Cardinality.MANY_TO_MANY,
                            "synthetic-orphan",
                            1);
            relations.bind(run, List.of(orphan), NONE);
            relations.resolve(run);
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_link WHERE run_id=? AND "
                                    + "state='MISSING_SOURCE'",
                            run));
            assertThrows(
                    SQLException.class,
                    () ->
                            relations.bind(
                                    run,
                                    List.of(
                                            binding(
                                                    Kind.FAT_DOCUMENT_FREIGHT,
                                                    1,
                                                    1,
                                                    2,
                                                    Cardinality.ONE_TO_ONE,
                                                    "synthetic-one",
                                                    1)),
                                    NONE));
        }
    }

    @Test
    void queueDistinguishesEmptyTemporaryLeaseExpiryAndAttemptCeiling() throws SQLException {
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
            final var relations = new JdbcExpansionRelations(session, CLOCK);
            relations.bind(
                    run,
                    List.of(
                            binding(
                                    Kind.FAT_DOCUMENT_FREIGHT,
                                    1,
                                    1,
                                    1,
                                    Cardinality.MANY_TO_MANY,
                                    "synthetic-empty",
                                    1)),
                    NONE);
            relations.resolve(run);
            final UUID owner = UUID.randomUUID();
            final var first = relations.claimBatch(run, owner, 1, 5).get(0);
            assertEquals(0, relations.claimBatch(run, UUID.randomUUID(), 1, 5).size());
            final var empty =
                    dependency(session, run)
                            .capture(
                                    DataExportTemplate.FRETES,
                                    DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    ExpansionDependencyFixtures.source(
                                            DataExportTemplate.FRETES, 0, 3),
                                    NONE);
            relations.finish(run, first, owner, Outcome.EMPTY, empty.executionId());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_queue WHERE run_id=? AND state='EMPTY'",
                            run));
            assertEquals(0, relations.claimBatch(run, owner, 1, 5).size());
            final var later =
                    new JdbcExpansionRelations(
                            session, Clock.offset(CLOCK, Duration.ofSeconds(10)));
            final var second = later.claimBatch(run, owner, 1, 5).get(0);
            assertEquals(2, second.attempt());
            later.finish(run, second, owner, Outcome.TEMPORARY, null);
            final var thirdClock =
                    new JdbcExpansionRelations(
                            session, Clock.offset(CLOCK, Duration.ofSeconds(30)));
            assertEquals(3, thirdClock.claimBatch(run, owner, 1, 5).get(0).attempt());
            final var expired =
                    new JdbcExpansionRelations(
                            session, Clock.offset(CLOCK, Duration.ofSeconds(35)));
            assertEquals(0, expired.claimBatch(run, owner, 1, 5).size());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_queue WHERE run_id=? AND state='ABANDONED'",
                            run));
            assertEquals(
                    3,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_queue_attempt a JOIN "
                                    + "ctl.expansion_lab_queue q ON q.queue_id=a.queue_id WHERE q.run_id=? AND "
                                    + "a.finished_at IS NOT NULL",
                            run));
        }
    }

    static LocalExpansionDependencyRuntime dependency(
            final ColetaTemporalLaboratorySession session, final UUID run) throws SQLException {
        return new LocalExpansionDependencyRuntime(
                session,
                run,
                new JdbcExpansionLaboratory(session, CLOCK).policy(run),
                CLOCK,
                Clock.systemUTC());
    }

    static List<ExpansionRelation> bindings(final int roots) {
        final var result = new ArrayList<ExpansionRelation>();
        for (int index = 1; index <= roots; index++) {
            for (final var kind :
                    List.of(Kind.FAT_DOCUMENT_FREIGHT, Kind.INV_FREIGHT, Kind.SIN_FREIGHT)) {
                for (int component = 1; component <= 2; component++) {
                    result.add(
                            binding(
                                    kind,
                                    index,
                                    component,
                                    index,
                                    Cardinality.MANY_TO_MANY,
                                    "synthetic-"
                                            + kind.name()
                                                    .substring(0, 3)
                                                    .toLowerCase(java.util.Locale.ROOT)
                                            + "-"
                                            + index
                                            + "-"
                                            + component,
                                    1));
                }
            }
            result.add(
                    binding(
                            Kind.LOC_FREIGHT,
                            index,
                            1,
                            index,
                            Cardinality.ONE_TO_ONE,
                            "synthetic-loc-" + index,
                            1));
        }
        return List.copyOf(result);
    }

    static ExpansionRelation binding(
            final Kind kind,
            final int root,
            final int component,
            final int target,
            final Cardinality cardinality,
            final String key,
            final int revision) {
        return new ExpansionRelation(
                key,
                revision,
                kind,
                kind == Kind.LOC_FREIGHT
                        ? new ExpansionKey(
                                ExpansionKey.Kind.INTEGER, Integer.toString(600000 + root))
                        : string(kind.name().substring(0, 3) + "-root-" + root),
                string("part-" + root),
                string("component-" + component),
                string("document-" + root + "-" + component),
                "INTEGER:" + (300000 + target),
                DATE,
                cardinality,
                true,
                "synthetic-explicit-link-v1");
    }

    private static ExpansionKey string(final String value) {
        return new ExpansionKey(ExpansionKey.Kind.STRING, value);
    }
}
