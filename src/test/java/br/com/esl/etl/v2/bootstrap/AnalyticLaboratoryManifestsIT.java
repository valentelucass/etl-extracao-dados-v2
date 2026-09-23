package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFreightRelationBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFreightRelationBinding.Kind;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticMaterializationRequest;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFleetReferences;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFreightRelations;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryManifestsIT {
    private static final LocalDate DATE = AnalyticLaboratoryRasterIT.DATE;
    private static final Clock CLOCK = AnalyticLaboratoryRasterIT.CLOCK;
    private static final CancellationToken NONE = CancellationToken.none();

    @Test
    void directCanonicalFreightAndTwoTrailersSurviveExpandedRootReplayAndExactReceipt()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var setup = setup(session, false, true);
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            final var request = request(setup.fixture().run());
            final var first = repository.manifests(request, NONE);
            assertDisposition(session, setup.fixture().run(), "READY");
            assertEquals(new JdbcAnalyticMaterializations.Receipt(1, 1, 0, 0, 1, 0), first);
            assertEquals(first, repository.manifests(request, NONE));
            assertEquals(1, repository.manifests(request(setup.fixture().run()), NONE).noops());
            assertQueries(session, setup.fixture().run(), false);
            assertFact(
                    session,
                    setup.fixture().run(),
                    DATE,
                    "synthetic-branch-a",
                    "120.00000000",
                    1,
                    0,
                    0);
            capture(session, setup.fixture(), setup.row());
            assertEquals(1, repository.manifests(request(setup.fixture().run()), NONE).noops());
            assertEquals(
                    6,
                    scalar(
                            session,
                            setup.fixture().run(),
                            "SELECT s.source_rows FROM pub.analytic_lab_manifests f JOIN core.analytic_manifest_sn"
                                    + "apshot s ON s.snapshot_id=f.snapshot_id WHERE f.run_id=?"));
        }
    }

    @Test
    void directAndCollectionPathsPreserveBothLineagesButCountTheSameFreightOnce() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var setup = setup(session, true, true);
            final var fixture = setup.fixture();
            final var runtime =
                    new LocalRelationalRuntime(
                            session,
                            fixture.relational(),
                            AnalyticLaboratoryManifestPreparationIT.policy(),
                            CLOCK,
                            Clock.systemUTC());
            runtime.capture(
                    DataExportTemplate.COLETAS,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    RelationalLaboratoryFixtures.source(
                            DataExportTemplate.COLETAS, DATE, 1, 1, 2, true),
                    NONE);
            final var freight =
                    runtime.capture(
                            DataExportTemplate.FRETES,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            RelationalLaboratoryFixtures.source(
                                    DataExportTemplate.FRETES, DATE, 1, 1, 2, true),
                            NONE);
            final var relations = new JdbcRelationalLaboratory(session, CLOCK);
            relations.bindBatch(
                    fixture.relational(),
                    RelationalLaboratoryFixtures.bindingBatch(DATE, 1, 1),
                    NONE);
            assertEquals(
                    2, relations.resolve(fixture.relational(), UUID.randomUUID(), NONE).resolved());
            new JdbcAnalyticFreightRelations(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    new AnalyticFreightRelationBinding(
                                            Kind.CROSSWALK,
                                            "INTEGER:300001",
                                            freight.executionId(),
                                            "INTEGER:300001",
                                            fixture.freight(),
                                            1,
                                            true,
                                            null)),
                            NONE);
            assertEquals(
                    1,
                    new JdbcAnalyticMaterializations(session, CLOCK)
                            .manifests(request(fixture.run()), NONE)
                            .ready());
            assertFact(session, fixture.run(), DATE, "synthetic-branch-a", "120.00000000", 1, 1, 1);
            assertQueries(session, fixture.run(), true);
            assertEquals(
                    2,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM mart.analytic_manifest_freight_path p JOIN pub.analytic_lab_"
                                    + "manifests f ON f.observation_id=p.observation_id WHERE f.run_id=?"));
            assertEquals(
                    1,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM mart.analytic_manifest_freight p JOIN pub.analytic_lab_manif"
                                    + "ests f ON f.observation_id=p.observation_id WHERE f.run_id=?"));
            final var withoutPick = setup.row().deepCopy();
            withoutPick.putNull("mft_pfs_pck_sequence_code");
            withoutPick.put("finished_at", "2036-04-02T12:00:00Z");
            capture(session, fixture, withoutPick);
            assertEquals(
                    1,
                    new JdbcAnalyticMaterializations(session, CLOCK)
                            .manifests(request(fixture.run()), NONE)
                            .ready());
            assertFact(session, fixture.run(), DATE, "synthetic-branch-a", "120.00000000", 1, 0, 0);
            assertEquals(
                    1,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM mart.analytic_manifest_freight_path p JOIN pub.analytic_lab_"
                                    + "manifests f ON f.observation_id=p.observation_id WHERE f.run_id=?"));
        }
    }

    @Test
    void competenceAndBranchCorrectionMovesOneFactAndLifecycleExclusionCanReactivate()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var setup = setup(session, false, true);
            final var fixture = setup.fixture();
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            assertEquals(1, repository.manifests(request(fixture.run()), NONE).ready());
            final var correction = setup.row().deepCopy();
            correction.put("departured_at", "2036-04-02T12:00:00.000000001Z");
            correction.put("finished_at", "2036-04-02T13:00:00.000000001Z");
            final var execution = capture(session, fixture, correction);
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    binding(
                                            execution,
                                            Role.BRANCH,
                                            "synthetic-branch-b",
                                            2,
                                            "synthetic-branch-a")),
                            NONE);
            assertEquals(1, repository.manifests(request(fixture.run()), NONE).updates());
            assertFact(
                    session,
                    fixture.run(),
                    DATE.plusDays(1),
                    "synthetic-branch-b",
                    "120.00000000",
                    1,
                    0,
                    0);
            assertEquals(
                    0,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM mart.analytic_manifest_current WHERE run_id=? AND reference_"
                                    + "date='20360401'"));
            AnalyticLaboratoryCollectorsIT.activate(
                    session, fixture.run(), execution, 2, false, false);
            assertEquals(1, repository.manifests(request(fixture.run()), NONE).blocked());
            assertEquals(
                    0,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_manifests WHERE run_id=?"));
            AnalyticLaboratoryCollectorsIT.activate(
                    session, fixture.run(), execution, 3, true, true);
            assertEquals(1, repository.manifests(request(fixture.run()), NONE).ready());
        }
    }

    @Test
    void unresolvedBindingsRevenueDisagreementAndOutdatedCompositionRemainAuditable()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var setup = setup(session, false, false);
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            assertEquals(1, repository.manifests(request(setup.fixture().run()), NONE).blocked());
            assertDisposition(session, setup.fixture().run(), "FLEET_BINDING_MISSING");
            bindFleet(session, setup.fixture().run(), setup.execution());
            assertEquals(1, repository.manifests(request(setup.fixture().run()), NONE).ready());
            final var changed = setup.row().deepCopy();
            changed.put("manifest_freights_total", "119.99000000");
            changed.put("finished_at", "2036-04-02T12:00:00Z");
            final var execution = capture(session, setup.fixture(), changed);
            assertEquals(1, repository.manifests(request(setup.fixture().run()), NONE).blocked());
            assertDisposition(session, setup.fixture().run(), "DIRECT_AGGREGATE_CONFLICT");
            new JdbcAnalyticFreightRelations(session)
                    .bindBatch(
                            setup.fixture().run(),
                            List.of(
                                    new AnalyticFreightRelationBinding(
                                            Kind.DIRECT,
                                            "INTEGER:1",
                                            execution,
                                            "INTEGER:300002",
                                            setup.fixture().freight(),
                                            2,
                                            true,
                                            null)),
                            NONE);
            assertEquals(1, repository.manifests(request(setup.fixture().run()), NONE).blocked());
            assertDisposition(session, setup.fixture().run(), "COMPOSITION_OUTDATED");
        }
    }

    @Test
    void divergentReceiptCannotHideAChangedSource() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var setup = setup(session, false, true);
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            final var request = request(setup.fixture().run());
            repository.manifests(request, NONE);
            final var changed = setup.row().deepCopy();
            changed.put(
                    "operational_comments", "SYNTHETIC changed field outside original legacy hash");
            changed.put("finished_at", "2036-04-02T12:00:00Z");
            capture(session, setup.fixture(), changed);
            assertEquals(
                    53665,
                    assertThrows(SQLException.class, () -> repository.manifests(request, NONE))
                            .getErrorCode());
        }
    }

    static Setup setup(
            final ColetaTemporalLaboratorySession session,
            final boolean withPick,
            final boolean fleet)
            throws Exception {
        final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
        return setup(session, fixture, withPick, fleet);
    }

    static Setup setup(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final boolean withPick,
            final boolean fleet)
            throws Exception {
        final var policy =
                new br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy(
                        DATE,
                        DATE.plusDays(3),
                        DATE,
                        2,
                        100,
                        1000,
                        br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules
                                .FiscalPolicy.SYNTHETIC_CTE);
        new LocalExpansionDependencyRuntime(
                        session, fixture.expansion(), policy, CLOCK, Clock.systemUTC())
                .capture(
                        UUID.randomUUID(),
                        DataExportTemplate.FRETES,
                        DATE,
                        ExecutionMode.BACKFILL,
                        null,
                        ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, 1, 3, 2)
                                .withFinancialBindings(ExpansionDependencyFixtures::financialTerms),
                        NONE);
        new JdbcAnalyticReferences(session, CLOCK)
                .importManifestPackaged(
                        fixture.run(),
                        2,
                        DATE,
                        DATE.plusDays(3),
                        AnalyticLaboratoryReferencesIT.POLICIES);
        new JdbcAnalyticFleetReferences(session, CLOCK)
                .importPackaged(fixture.run(), 2, DATE, DATE.plusDays(3));
        final var row = AnalyticLaboratoryManifestCaptureIT.manifest();
        row.put("manifest_freights_total", "120.00000000");
        row.put("contract_type", "aggregate");
        row.put("mft_mdr_contract_type", "company");
        row.put("calculation_type", "price_table");
        row.put("cargo_type", "fractioned");
        if (withPick) {
            row.put("mft_pfs_pck_sequence_code", 100001);
        } else {
            row.putNull("mft_pfs_pck_sequence_code");
        }
        final var execution = capture(session, fixture, row);
        AnalyticLaboratoryCollectorsIT.activate(session, fixture.run(), execution, 1, true, false);
        new JdbcAnalyticDimensions(session)
                .bindBatch(
                        fixture.run(),
                        List.of(binding(execution, Role.BRANCH, "synthetic-branch-a", 1, null)),
                        NONE);
        if (fleet) {
            bindFleet(session, fixture.run(), execution);
        }
        final var relations = new JdbcAnalyticFreightRelations(session);
        relations.bindBatch(
                fixture.run(),
                List.of(
                        new AnalyticFreightRelationBinding(
                                Kind.DIRECT,
                                "INTEGER:1",
                                execution,
                                "INTEGER:300001",
                                fixture.freight(),
                                1,
                                true,
                                null)),
                NONE);
        relations.sealComposition(fixture.run(), "INTEGER:1", execution, 1, 1);
        return new Setup(fixture, execution, row);
    }

    static UUID capture(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final ObjectNode row)
            throws Exception {
        return AnalyticLaboratoryManifestPreparationIT.runtime(session, fixture)
                .capture(
                        DATE,
                        ExecutionMode.BACKFILL,
                        null,
                        page ->
                                page == 1
                                        ? "[" + row + "," + row + "]"
                                        : page == 2 ? "[" + row + "]" : "[]")
                .source()
                .executionId();
    }

    private static void bindFleet(
            final ColetaTemporalLaboratorySession session, final UUID run, final UUID execution)
            throws SQLException {
        new JdbcAnalyticDimensions(session)
                .bindBatch(
                        run,
                        List.of(
                                binding(execution, Role.TRACTOR, "synthetic-tractor-a", 1, null),
                                binding(execution, Role.TRAILER1, "synthetic-trailer-a", 1, null),
                                binding(execution, Role.TRAILER2, "synthetic-trailer-b", 1, null),
                                binding(execution, Role.DRIVER, "synthetic-driver-a", 1, null)),
                        NONE);
    }

    static AnalyticDimensionBinding binding(
            final UUID execution,
            final Role role,
            final String key,
            final int revision,
            final String previous) {
        return new AnalyticDimensionBinding(
                Entity.MAN,
                "INTEGER:1",
                execution,
                role,
                key,
                revision,
                DATE,
                DATE.plusDays(3),
                true,
                previous,
                "synthetic-mat05-binding");
    }

    static AnalyticMaterializationRequest request(final UUID run) {
        return new AnalyticMaterializationRequest(
                run, UUID.randomUUID(), 2, ExecutionMode.BACKFILL, true, DATE, DATE.plusDays(3));
    }

    private static void assertFact(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final LocalDate date,
            final String branch,
            final String revenue,
            final int direct,
            final int collection,
            final int shared)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT reference_date,branch_key,total_revenue,total_capacity,tractor_capacity,traile"
                                        + "r1_capacity,trailer2_capacity,"
                                        + "direct_freights,collection_freights,shared_freights,unique_freights,ownership_code,co"
                                        + "ntract_code,capacity_unit"
                                        + " FROM pub.analytic_lab_manifests WHERE run_id=?")) {
            sql.setString(1, run.toString());
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery()) {
                assertTrue(row.next());
                assertEquals(date, row.getDate(1).toLocalDate());
                assertEquals(branch, row.getString(2));
                assertEquals(new BigDecimal(revenue), row.getBigDecimal(3));
                assertEquals(new BigDecimal("22000.00000000"), row.getBigDecimal(4));
                assertEquals(new BigDecimal("10000.00000000"), row.getBigDecimal(5));
                assertEquals(new BigDecimal("5000.00000000"), row.getBigDecimal(6));
                assertEquals(new BigDecimal("7000.00000000"), row.getBigDecimal(7));
                assertEquals(direct, row.getInt(8));
                assertEquals(collection, row.getInt(9));
                assertEquals(shared, row.getInt(10));
                assertEquals(1, row.getInt(11));
                assertEquals("FLEET", row.getString(12));
                assertEquals("FLEET", row.getString(13));
                assertEquals("KG", row.getString(14));
                assertFalse(row.next());
            }
        }
    }

    private static void assertQueries(
            final ColetaTemporalLaboratorySession session, final UUID run, final boolean collection)
            throws Exception {
        final com.fasterxml.jackson.databind.JsonNode catalog;
        try (var input =
                java.nio.file.Files.newInputStream(
                        java.nio.file.Path.of(
                                "docs/catalogos/macrobloco-analitico/contratos-colunas-inicial.json"))) {
            catalog = new com.fasterxml.jackson.databind.ObjectMapper().readTree(input);
        }
        for (final var contract : catalog.path("contracts")) {
            final String id = contract.path("id").asText();
            if (!id.equals("SQL-08") && !id.equals("SQL-09")) {
                continue;
            }
            final String sqlText =
                    id.equals("SQL-08")
                            ? "SELECT * FROM pub.analytic_lab_sql_08 WHERE run_id=? ORDER BY business_date,source_key"
                            : "SELECT * FROM pub.analytic_lab_sql_09 WHERE run_id=? ORDER BY business_date,source_key";
            try (var connection = session.getConnection();
                    var sql = connection.prepareStatement(sqlText)) {
                sql.setQueryTimeout(10);
                sql.setString(1, run.toString());
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next(), id);
                    final var metadata = row.getMetaData();
                    int ordinal = 0;
                    for (final var column : contract.path("columns")) {
                        final String name = column.path("name").asText();
                        assertEquals(name, metadata.getColumnLabel(++ordinal));
                        if (!name.equals("Coleta/Número") || collection) {
                            org.junit.jupiter.api.Assertions.assertNotNull(
                                    row.getObject(ordinal), id + ":" + name);
                        }
                    }
                    assertEquals(id.equals("SQL-08") ? 105 : 106, ordinal);
                    assertEquals("encerrado", row.getString("Status"));
                    assertEquals("authorized", row.getString("MDFe/Status"));
                    assertEquals("SYNTHETIC BRANCH A", row.getString("Filial"));
                    assertEquals("Agregado", row.getString("Tipo de contrato do veículo"));
                    assertEquals("Próprio", row.getString("Tipo de contrato do motorista"));
                    assertEquals("Frota", row.getString("Tipo de contrato"));
                    assertEquals("Frota Própria", row.getString("Tipo Motorista"));
                    assertEquals("tabela de preço", row.getString("Tipo de cálculo"));
                    assertEquals("carga fracionada", row.getString("Tipo de carga"));
                    assertEquals("SYNTHETIC A, SYNTHETIC B", row.getString("Entrega/Regiões"));
                    assertEquals(collection ? "100001" : null, row.getString("Coleta/Número"));
                    assertEquals(
                            new BigDecimal("120.00000000"),
                            row.getBigDecimal("Receita Total Transportada"));
                    assertEquals(
                            new BigDecimal("22000.00000000"),
                            row.getBigDecimal("Capacidade Lotação Kg"));
                    assertEquals(
                            new BigDecimal("100.12500000"), row.getBigDecimal("Total peso taxado"));
                    assertEquals("outros", row.getString("classification_bucket"));
                    assertEquals(
                            "2036-04-01T09:00:00.123456700",
                            row.getObject("Data criação", java.time.OffsetDateTime.class)
                                    .toLocalDateTime()
                                    .toString());
                    final var typed =
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readTree(row.getString("Metadata"));
                    assertEquals(3, typed.path("source_rows").asInt());
                    assertEquals("SYN", typed.path("operational_comments").asText());
                    final int money = row.findColumn("Receita Total Transportada");
                    assertEquals(java.sql.Types.DECIMAL, metadata.getColumnType(money));
                    assertEquals(28, metadata.getPrecision(money));
                    assertEquals(8, metadata.getScale(money));
                    assertFalse(row.next());
                }
            }
        }
    }

    private static void assertDisposition(
            final ColetaTemporalLaboratorySession session, final UUID run, final String expected)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT o.disposition FROM mart.analytic_manifest_current c JOIN mart.analytic_manifes"
                                        + "t_observation o ON o.observation_id=c.observation_id WHERE c.run_id=?")) {
            sql.setString(1, run.toString());
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery()) {
                assertTrue(row.next());
                assertEquals(expected, row.getString(1));
                assertFalse(row.next());
            }
        }
    }

    private static long scalar(
            final ColetaTemporalLaboratorySession session, final UUID run, final String sql)
            throws SQLException {
        return AnalyticLaboratoryRasterIT.scalar(session, sql, run);
    }

    record Setup(AnalyticLaboratoryDimensionsIT.Fixture fixture, UUID execution, ObjectNode row) {}
}
