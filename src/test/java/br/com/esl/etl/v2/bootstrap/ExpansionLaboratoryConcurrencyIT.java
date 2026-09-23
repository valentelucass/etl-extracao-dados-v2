package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Contention on effective procedures, followed by first-owner rollback and valid second-owner work.
 */
class ExpansionLaboratoryConcurrencyIT {
    @ParameterizedTest
    @ValueSource(strings = {"CLAIM", "APPLY", "INVOICE", "REVENUE"})
    void secondPhysicalOwnerIsRefusedThenConsumesAfterFirstRollback(final String operation)
            throws Exception {
        final var worker = Executors.newSingleThreadExecutor();
        try (var first = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = RelationalLaboratoryLocalIntegrationIT.counts(first);
            final UUID run = UUID.randomUUID();
            final UUID capture = seed(first, run, operation);
            consume(first, run, capture, operation);
            final var denied =
                    worker.submit(
                            () -> {
                                try (var second =
                                        ColetaTemporalLaboratorySession.openFromEnvironment()) {
                                    try (var connection = second.getConnection();
                                            var begin = connection.createStatement()) {
                                        begin.setQueryTimeout(5);
                                        begin.execute("IF @@TRANCOUNT=0 BEGIN TRANSACTION;");
                                    }
                                    return assertThrows(
                                                    SQLException.class,
                                                    () -> consume(second, run, capture, operation))
                                            .getErrorCode();
                                }
                            });
            assertEquals(
                    operation.equals("APPLY") ? 53302 : 53401, denied.get(10, TimeUnit.SECONDS));
            first.rollback();
            final var resumed =
                    worker.submit(
                            () -> {
                                try (var second =
                                        ColetaTemporalLaboratorySession.openFromEnvironment()) {
                                    final UUID recovered = seed(second, run, operation);
                                    if (operation.equals("CLAIM")) {
                                        return new ExpansionLaboratoryHydrator(
                                                        new JdbcExpansionRelations(second, CLOCK),
                                                        ExpansionLaboratoryRelationsIT.dependency(
                                                                second, run))
                                                .hydrate(run, 1, NONE)
                                                .completed();
                                    }
                                    return consume(second, run, recovered, operation);
                                }
                            });
            assertEquals(1, resumed.get(45, TimeUnit.SECONDS));
            assertEquals(before, RelationalLaboratoryLocalIntegrationIT.counts(first));
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static UUID seed(
            final ColetaTemporalLaboratorySession session, final UUID run, final String operation)
            throws SQLException {
        if (operation.equals("INVOICE") || operation.equals("REVENUE")) {
            ExpansionLaboratoryRevenueIT.prepare(session, run, 1, true);
            return null;
        }
        final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
        final var result =
                runtime.capture(
                        DataExportTemplate.FATURAS_POR_CLIENTE,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        ExpansionLaboratoryFixtures.source(
                                DataExportTemplate.FATURAS_POR_CLIENTE, 1, 3),
                        NONE);
        if (operation.equals("CLAIM")) {
            final var relations = new JdbcExpansionRelations(session, CLOCK);
            relations.bind(
                    run,
                    List.of(
                            ExpansionLaboratoryRelationsIT.binding(
                                    ExpansionRelation.Kind.FAT_DOCUMENT_FREIGHT,
                                    1,
                                    1,
                                    1,
                                    ExpansionRelation.Cardinality.MANY_TO_MANY,
                                    "synthetic-concurrent",
                                    1)),
                    NONE);
            assertEquals(1, relations.resolve(run).missing());
        }
        return result.executionId();
    }

    private static int consume(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID capture,
            final String operation)
            throws SQLException {
        return switch (operation) {
            case "CLAIM" ->
                    new JdbcExpansionRelations(session, CLOCK)
                            .claimBatch(run, UUID.randomUUID(), 1, 60)
                            .size();
            case "APPLY" ->
                    new JdbcExpansionLaboratory(session, CLOCK).apply(run, capture).inserts() == 2
                            ? 1
                            : 0;
            case "INVOICE" ->
                    (int)
                            new JdbcExpansionMaterializations(session, CLOCK)
                                    .invoices(
                                            run,
                                            UUID.randomUUID(),
                                            1,
                                            ExecutionMode.BOOTSTRAP,
                                            true,
                                            DATE,
                                            DATE.plusDays(3))
                                    .ready();
            case "REVENUE" ->
                    (int)
                            new JdbcExpansionMaterializations(session, CLOCK)
                                    .revenue(
                                            run,
                                            UUID.randomUUID(),
                                            1,
                                            ExecutionMode.BOOTSTRAP,
                                            true,
                                            DATE,
                                            DATE.plusDays(3))
                                    .ready();
            default -> throw new IllegalArgumentException("EXP_CONCURRENCY_OPERATION");
        };
    }
}
