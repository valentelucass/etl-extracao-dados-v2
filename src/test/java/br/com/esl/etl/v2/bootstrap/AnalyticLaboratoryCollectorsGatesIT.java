package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryCollectorsIT.activate;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryCollectorsIT.binding;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticMaterializationRequest;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(90)
class AnalyticLaboratoryCollectorsGatesIT {
    @ParameterizedTest
    @ValueSource(
            strings = {"Carga Fechada", "Acerto de Motorista", "Frete Retorno", "Viagem Vazia"})
    void governedOperationPrefixesExcludeBothContributions(final String prefix) throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            AnalyticLaboratoryCollectorsIT.inventory(session, fixture, 0);
            final var row =
                    AnalyticLaboratoryManifestCaptureIT.manifest()
                            .put("mft_man_name", "  " + prefix + " synthetic");
            final var captured =
                    AnalyticLaboratoryManifestPreparationIT.runtime(session, fixture)
                            .capture(
                                    DATE,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    AnalyticLaboratoryManifestPreparationIT.source(row));
            activate(session, fixture.run(), captured.source().executionId(), 1, true, false);
            final var receipt = materialize(session, fixture);
            assertEquals(2, receipt.blocked());
            assertEquals(0, receipt.ready());
            assertEquals(2, disposition(session, fixture.run(), "OPERATION_EXCLUDED"));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Picking",
                "Return",
                "Receipt",
                "Loading",
                "Unloading",
                "CheckIn::Order::Loading"
            })
    void allowedInventoryTypesContributeOnceWithZeroDenominator(final String type)
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = emptyManifest(session);
            final var row = inventoryData().put("type", type).putNull("finished_at");
            final var execution = inventory(session, fixture, List.of(row, row.deepCopy()));
            bindInventory(session, fixture, execution);
            final var receipt = materialize(session, fixture);
            assertEquals(1, receipt.ready());
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_collectors WHERE run_id=?"
                                    + " AND scanned=1 AND incomplete=1 AND issued=0 AND unloaded=0"
                                    + " AND total=0 AND percentage=0 AND classification=N'Geral'",
                            fixture.run()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"TYPE", "DATE", "CONFLICT", "BRANCH"})
    void invalidInventoryPrerequisitesHaveExplicitDisposition(final String condition)
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = emptyManifest(session);
            final var first = inventoryData();
            if (condition.equals("TYPE")) {
                first.put("type", "Unapproved");
            }
            if (condition.equals("DATE")) {
                first.putNull("started_at");
            }
            final var second = first.deepCopy();
            if (condition.equals("CONFLICT")) {
                second.put("type", "Return");
            }
            final var execution = inventory(session, fixture, List.of(first, second));
            if (!condition.equals("BRANCH")) {
                bindInventory(session, fixture, execution);
            }
            final var receipt = materialize(session, fixture);
            final String reason =
                    switch (condition) {
                        case "TYPE" -> "TYPE_EXCLUDED";
                        case "DATE" -> "STARTED_DATE_MISSING";
                        case "CONFLICT" -> "INVENTORY_ROOT_CONFLICT";
                        default -> "BRANCH_BINDING_MISSING";
                    };
            assertEquals(0, receipt.ready());
            assertEquals(1, disposition(session, fixture.run(), reason));
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_collectors WHERE run_id=?",
                            fixture.run()));
        }
    }

    @Test
    void freightFallbackRequiresOneCanonicalTargetAndPreservesExplicitProvenance()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = emptyManifest(session);
            inventory(session, fixture, List.of(inventoryData().putNull("cnr_crn_psn_nickname")));
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    binding(
                                            Entity.FRETE,
                                            "INTEGER:300001",
                                            fixture.freight(),
                                            Role.BRANCH,
                                            "synthetic-branch-a",
                                            1,
                                            null)),
                            CancellationToken.none());
            final var relations = new JdbcExpansionRelations(session, CLOCK);
            relations.bind(
                    fixture.expansion(),
                    List.of(
                            ExpansionLaboratoryRelationsIT.binding(
                                    ExpansionRelation.Kind.INV_FREIGHT,
                                    1,
                                    1,
                                    1,
                                    ExpansionRelation.Cardinality.MANY_TO_MANY,
                                    "synthetic-mat02-fallback",
                                    1)),
                    CancellationToken.none());
            relations.resolve(fixture.expansion());
            assertEquals(1, materialize(session, fixture).ready());
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM mart.analytic_collector_contribution c"
                                    + " JOIN mart.analytic_collector_contribution_observation o ON o.observation_id=c.observ"
                                    + "ation_id"
                                    + " WHERE c.run_id=? AND o.branch_provenance='EXPLICIT_FREIGHT_FALLBACK'"
                                    + " AND o.fallback_dependency_id IS NOT NULL AND o.branch_binding_id IS NOT NULL",
                            fixture.run()));
            relations.bind(
                    fixture.expansion(),
                    List.of(
                            ExpansionLaboratoryRelationsIT.binding(
                                    ExpansionRelation.Kind.INV_FREIGHT,
                                    1,
                                    1,
                                    2,
                                    ExpansionRelation.Cardinality.MANY_TO_MANY,
                                    "synthetic-mat02-second",
                                    1)),
                    CancellationToken.none());
            relations.resolve(fixture.expansion());
            assertEquals(1, materialize(session, fixture).blocked());
            assertEquals(1, disposition(session, fixture.run(), "FREIGHT_FALLBACK_AMBIGUOUS"));
        }
    }

    @Test
    void emptyCompleteInputsAreValidButMissingCaptureAndNarrowFullAreRefused() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = emptyManifest(session);
            assertEquals(
                    53622,
                    assertThrows(SQLException.class, () -> materialize(session, fixture))
                            .getErrorCode());
            AnalyticLaboratoryCollectorsIT.inventory(session, fixture, 0);
            assertEquals(0, materialize(session, fixture).candidates());
            final var narrow =
                    new AnalyticMaterializationRequest(
                            fixture.run(),
                            UUID.randomUUID(),
                            1,
                            ExecutionMode.BOOTSTRAP,
                            true,
                            DATE,
                            DATE.plusDays(1));
            assertEquals(
                    53621,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            new JdbcAnalyticMaterializations(session, CLOCK)
                                                    .collectors(narrow, CancellationToken.none()))
                            .getErrorCode());
            assertEquals(0, materialize(session, fixture).candidates());
        }
    }

    @Test
    void reusedReceiptWithChangedCapturedRootIsRefusedWithoutNewObservations() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = emptyManifest(session);
            final var execution = inventory(session, fixture, List.of(inventoryData()));
            bindInventory(session, fixture, execution);
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            final var request = request(fixture.run());
            assertEquals(1, repository.collectors(request, CancellationToken.none()).ready());
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    binding(
                                            Entity.INV,
                                            "STRING:INV-root-1",
                                            execution,
                                            Role.BRANCH,
                                            "synthetic-branch-b",
                                            2,
                                            "synthetic-branch-a")),
                            CancellationToken.none());
            assertEquals(
                    53625,
                    assertThrows(
                                    SQLException.class,
                                    () -> repository.collectors(request, CancellationToken.none()))
                            .getErrorCode());
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM mart.analytic_collector_contribution_observation WHERE run_id=?",
                            fixture.run()));
            assertEquals(1, materialize(session, fixture).ready());
        }
    }

    private static AnalyticLaboratoryDimensionsIT.Fixture emptyManifest(
            final ColetaTemporalLaboratorySession session) throws Exception {
        final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
        AnalyticLaboratoryManifestPreparationIT.runtime(session, fixture)
                .capture(DATE, ExecutionMode.BOOTSTRAP, null, page -> "[]");
        return fixture;
    }

    private static ObjectNode inventoryData() {
        return ExpansionLaboratoryFixtures.data(DataExportTemplate.INVENTARIO)
                .put("type", "Picking")
                .put("started_at", "2036-04-01T12:00:00Z");
    }

    private static UUID inventory(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final List<ObjectNode> data)
            throws Exception {
        final var execution = UUID.randomUUID();
        final var source =
                new ExpansionSyntheticSource(
                        page -> {
                            final var rows = JsonNodeFactory.instance.arrayNode();
                            if (page == 1) {
                                for (int index = 0; index < data.size(); index++) {
                                    rows.add(
                                            ExpansionLaboratoryFixtures.envelope(
                                                    DataExportTemplate.INVENTARIO,
                                                    data.get(index),
                                                    index + 1,
                                                    1,
                                                    index + 1));
                                }
                            }
                            return rows.toString();
                        });
        final var policy =
                new ExpansionPolicy(
                        DATE, DATE.plusDays(3), DATE, 2, 100, 1000, FiscalPolicy.SYNTHETIC_CTE);
        new LocalExpansionRuntime(session, fixture.expansion(), policy, CLOCK, Clock.systemUTC())
                .capture(
                        execution,
                        DataExportTemplate.INVENTARIO,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        source,
                        CancellationToken.none());
        return execution;
    }

    private static void bindInventory(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final UUID execution)
            throws Exception {
        new JdbcAnalyticDimensions(session)
                .bindBatch(
                        fixture.run(),
                        List.of(
                                binding(
                                        Entity.INV,
                                        "STRING:INV-root-1",
                                        execution,
                                        Role.BRANCH,
                                        "synthetic-branch-a",
                                        1,
                                        null)),
                        CancellationToken.none());
    }

    private static JdbcAnalyticMaterializations.Receipt materialize(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture)
            throws Exception {
        return new JdbcAnalyticMaterializations(session, CLOCK)
                .collectors(request(fixture.run()), CancellationToken.none());
    }

    private static long disposition(
            final ColetaTemporalLaboratorySession session, final UUID run, final String reason)
            throws Exception {
        try (var connection = session.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT COUNT_BIG(*) FROM mart.analytic_collector_contribution c"
                                        + " JOIN mart.analytic_collector_contribution_observation o ON o.observation_id=c.observ"
                                        + "ation_id"
                                        + " WHERE c.run_id=? AND o.disposition=?")) {
            statement.setQueryTimeout(10);
            statement.setString(1, run.toString());
            statement.setString(2, reason);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }
}
