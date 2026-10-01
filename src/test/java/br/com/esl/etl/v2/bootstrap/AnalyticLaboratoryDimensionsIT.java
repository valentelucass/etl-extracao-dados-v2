package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryDimensionsIT {
    private static final LocalDate DATE = AnalyticLaboratoryRasterIT.DATE;
    private static final Clock CLOCK = AnalyticLaboratoryRasterIT.CLOCK;

    @Test
    void changedNamePlateAndBranchRemainVersionedAttributesAndNormalizationNeverMergesKeys()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            new JdbcAnalyticDimensions(session)
                    .bindBatch(fixture.run(), bindings(fixture), CancellationToken.none());
            final var json = referenceFixture();
            for (final var item : json.path("registry")) {
                final var row = (com.fasterxml.jackson.databind.node.ObjectNode) item;
                if (row.path("kind").asText().equals("VEICULO")) {
                    row.put("name", " changed synthetic vehicle ");
                    row.put(
                            "plate",
                            row.path("key").asText().equals("synthetic-tractor-a")
                                    ? " new0001 "
                                    : "NEW0001");
                    row.put("branch", "synthetic-branch-b");
                }
                if (row.path("key").asText().equals("synthetic-driver-a")) {
                    row.put("name", " updated synthetic driver ");
                }
                if (row.path("key").asText().equals("synthetic-driver-b")) {
                    row.put("name", "UPDATED SYNTHETIC DRIVER");
                }
            }
            new JdbcAnalyticReferences(session, CLOCK)
                    .importFixture(
                            fixture.run(),
                            2,
                            DATE,
                            DATE.plusDays(3),
                            AnalyticLaboratoryReferencesIT.POLICIES,
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .writeValueAsBytes(json));
            assertEquals(
                    3,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(DISTINCT entity_key) FROM pub.analytic_lab_sql_16 WHERE run_id=? "
                                    + "AND reference_revision=2 AND valid_from='20360401' AND Placa='NEW0001' "
                                    + "AND branch_key='synthetic-branch-b'"));
            assertEquals(3, count(session, fixture.run(), 16));
            assertEquals(
                    2,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(DISTINCT entity_key) FROM pub.analytic_lab_sql_17 WHERE run_id=? "
                                    + "AND reference_revision=2 AND valid_from='20360401' "
                                    + "AND NomeMotorista='UPDATED SYNTHETIC DRIVER'"));
            new JdbcAnalyticReferences(session, CLOCK)
                    .importPackaged(
                            fixture.run(),
                            3,
                            DATE,
                            DATE.plusDays(3),
                            new JdbcAnalyticReferences.Policies(
                                    JdbcAnalyticReferences.Fiscal.CTE_FIRST_REAL_DOCUMENT,
                                    JdbcAnalyticReferences.Branch.EXPLICIT_ASSIGNMENT,
                                    JdbcAnalyticReferences.Driver.EXCLUDE_GENERIC));
            assertEquals(
                    2,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_17 WHERE run_id=? "
                                    + "AND reference_revision=3 AND valid_from='20360401'"));
            assertEquals(3, count(session, fixture.run(), 17));
        }
    }

    @Test
    void expiredAssignmentCannotUseAStillValidRegistryOrReappearInLaterDates() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    new AnalyticDimensionBinding(
                                            Entity.FRETE,
                                            "INTEGER:300001",
                                            fixture.freight(),
                                            Role.TRACTOR,
                                            "synthetic-tractor-a",
                                            1,
                                            DATE,
                                            DATE.plusDays(1),
                                            true,
                                            null,
                                            "synthetic-one-day-binding")),
                            CancellationToken.none());
            assertEquals(1, count(session, fixture.run(), 16));
            assertEquals(
                    0,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_16 WHERE run_id=? "
                                    + "AND reference_revision=1 AND valid_from>='20360402'"));
            assertEquals(
                    0,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM ref.ufn_analytic_dimension(?,1,'20360402')"));
        }
    }

    @Test
    void twoConflictingRowsOfSameRegistryKeyCannotSealAReferenceRelease() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            final var json = referenceFixture();
            final var registry =
                    (com.fasterxml.jackson.databind.node.ArrayNode) json.path("registry");
            final var changed =
                    ((com.fasterxml.jackson.databind.node.ObjectNode) registry.get(0))
                            .deepCopy()
                            .put("name", "SYNTHETIC CONFLICT");
            registry.add(changed);
            assertThrows(
                    SQLException.class,
                    () ->
                            new JdbcAnalyticReferences(session, CLOCK)
                                    .importFixture(
                                            fixture.run(),
                                            2,
                                            DATE,
                                            DATE.plusDays(3),
                                            AnalyticLaboratoryReferencesIT.POLICIES,
                                            new com.fasterxml.jackson.databind.ObjectMapper()
                                                    .writeValueAsBytes(json)));
            assertEquals(
                    0,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_reference_selection WHERE run_id=? AND revi"
                                    + "sion=2"));
        }
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode referenceFixture()
            throws Exception {
        try (var input =
                AnalyticLaboratoryDimensionsIT.class.getResourceAsStream(
                        "/analytic-laboratory/references.synthetic.json")) {
            return (com.fasterxml.jackson.databind.node.ObjectNode)
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(input.readNBytes(32769));
        }
    }

    @Test
    void capturedAssignmentsConsumeFiveDimensionsWithoutMergingHomonyms() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            assertEquals(0, count(session, fixture.run(), 16));
            final var repository = new JdbcAnalyticDimensions(session);
            final var bindings = bindings(fixture);
            assertEquals(
                    12, repository.bindBatch(fixture.run(), bindings, CancellationToken.none()));
            assertEquals(
                    12, repository.bindBatch(fixture.run(), bindings, CancellationToken.none()));
            for (int index = 0; index < 5; index++) {
                assertEquals(
                        new int[] {2, 2, 3, 3, 2}[index],
                        count(session, fixture.run(), 14 + index));
            }
            assertEquals(
                    12,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM ref.analytic_lab_dimension_binding WHERE run_id=?"));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT [Nome],COUNT_BIG(*),COUNT(DISTINCT entity_key) FROM pub.analytic_lab_sql_15"
                                            + " WHERE run_id=? AND valid_from='20360401' GROUP BY [Nome]")) {
                sql.setString(1, fixture.run().toString());
                sql.setQueryTimeout(10);
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals("SYNTHETIC CLIENT", row.getString(1));
                    assertEquals(2, row.getInt(2));
                    assertEquals(2, row.getInt(3));
                }
            }
            assertEquals(
                    22000,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT SUM(capacity) FROM pub.analytic_lab_sql_16 WHERE run_id=? AND valid_from='20360401'"));
            assertEquals(
                    1,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_18 WHERE run_id=? AND valid_from='20360401'"
                                    + " AND Classificacao=N'OUTROS / NÃO CLASSIFICADO' AND raw_classification IS NULL"));
            verifyMetadata(session, fixture.run());
        }
    }

    @Test
    void explicitRekeyAppliesOnlyAtItsBoundaryAndRetainsPriorEvidence() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            final var repository = new JdbcAnalyticDimensions(session);
            repository.bindBatch(
                    fixture.run(),
                    List.of(binding(fixture.freight(), Role.BRANCH, "synthetic-branch-a", 1)),
                    CancellationToken.none());
            repository.bindBatch(
                    fixture.run(),
                    List.of(
                            new AnalyticDimensionBinding(
                                    Entity.FRETE,
                                    "INTEGER:300001",
                                    fixture.freight(),
                                    Role.BRANCH,
                                    "synthetic-branch-b",
                                    2,
                                    DATE.plusDays(1),
                                    DATE.plusDays(3),
                                    true,
                                    "synthetic-branch-a",
                                    "synthetic-rekey-v1")),
                    CancellationToken.none());
            assertEquals(
                    1,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_14 WHERE run_id=? AND valid_from='20360401'"
                                    + " AND entity_key='synthetic-branch-a'"));
            assertEquals(
                    2,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_14 WHERE run_id=? AND valid_from>='20360402'"
                                    + " AND entity_key='synthetic-branch-b'"));
            assertEquals(
                    0,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_14 WHERE run_id=? AND valid_from>='20360402'"
                                    + " AND entity_key='synthetic-branch-a'"));
        }
    }

    @Test
    void missingCaptureAndWrongCaptureCannotCreateDimensionEvidence() throws Exception {
        for (final String key : List.of("INTEGER:399999", "INTEGER:300001")) {
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                final var fixture = start(session);
                final var binding =
                        new AnalyticDimensionBinding(
                                Entity.FRETE,
                                key,
                                fixture.cap(),
                                Role.BRANCH,
                                "synthetic-branch-a",
                                1,
                                DATE,
                                DATE.plusDays(3),
                                true,
                                null,
                                "synthetic-invalid-capture");
                final var failure =
                        assertThrows(
                                SQLException.class,
                                () ->
                                        new JdbcAnalyticDimensions(session)
                                                .bindBatch(
                                                        fixture.run(),
                                                        List.of(binding),
                                                        CancellationToken.none()));
                assertEquals(53565, failure.getErrorCode());
            }
        }
    }

    @Test
    void sameRevisionConflictOverlapAndUnprovedRekeyAreRefused() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                final var fixture = start(session);
                final var repository = new JdbcAnalyticDimensions(session);
                repository.bindBatch(
                        fixture.run(),
                        List.of(binding(fixture.freight(), Role.BRANCH, "synthetic-branch-a", 1)),
                        CancellationToken.none());
                final var collision =
                        new AnalyticDimensionBinding(
                                Entity.FRETE,
                                "INTEGER:300001",
                                fixture.freight(),
                                Role.BRANCH,
                                "synthetic-branch-b",
                                variant == 2 ? 2 : 1,
                                variant == 0 ? DATE : DATE.plusDays(1),
                                DATE.plusDays(3),
                                true,
                                null,
                                "synthetic-conflict");
                final var failure =
                        assertThrows(
                                SQLException.class,
                                () ->
                                        repository.bindBatch(
                                                fixture.run(),
                                                List.of(collision),
                                                CancellationToken.none()));
                assertEquals(new int[] {53570, 53567, 53568}[variant], failure.getErrorCode());
            }
        }
    }

    @Test
    void missingRegistryAndUnresolvedPolicyHaveAuditDisposition() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    binding(
                                            fixture.freight(),
                                            Role.TRACTOR,
                                            "synthetic-unknown",
                                            1)),
                            CancellationToken.none());
            assertEquals(0, count(session, fixture.run(), 16));
            assertEquals(
                    1,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM ref.ufn_analytic_dimension(?,1,'20360401') WHERE disposition"
                                    + "='MISSING_REGISTRY'"));
        }
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            new JdbcAnalyticReferences(session, CLOCK)
                    .importPackaged(
                            fixture.run(),
                            2,
                            DATE,
                            DATE.plusDays(3),
                            new JdbcAnalyticReferences.Policies(
                                    JdbcAnalyticReferences.Fiscal.UNRESOLVED,
                                    JdbcAnalyticReferences.Branch.UNRESOLVED,
                                    JdbcAnalyticReferences.Driver.UNRESOLVED));
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    binding(
                                            fixture.freight(),
                                            Role.BRANCH,
                                            "synthetic-branch-a",
                                            1)),
                            CancellationToken.none());
            assertEquals(
                    1,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM ref.ufn_analytic_dimension(?,2,'20360401') WHERE disposition"
                                    + "='BRANCH_UNRESOLVED'"));
        }
    }

    static Fixture start(final ColetaTemporalLaboratorySession session) throws Exception {
        return start(session, false);
    }

    static Fixture start(
            final ColetaTemporalLaboratorySession session, final boolean analyticManifest)
            throws Exception {
        return start(
                session,
                analyticManifest
                        ? RelationalSyntheticSource.analyticManifestContracts()
                        : RelationalSyntheticSource.contracts());
    }

    static Fixture start(
            final ColetaTemporalLaboratorySession session,
            final br.com.esl.etl.v2.plataforma.relacional.RelationalCaptureContracts contracts)
            throws Exception {
        return start(session, contracts, UUID.randomUUID());
    }

    static Fixture start(
            final ColetaTemporalLaboratorySession session,
            final br.com.esl.etl.v2.plataforma.relacional.RelationalCaptureContracts contracts,
            final UUID run)
            throws Exception {
        final UUID expansion = UUID.randomUUID(), relational = UUID.randomUUID();
        AnalyticLaboratoryRasterIT.start(session, run);
        final var policy =
                new ExpansionPolicy(
                        DATE, DATE.plusDays(3), DATE, 2, 100, 1000, FiscalPolicy.SYNTHETIC_CTE);
        RuntimePhaseEvidence.sql(
                RuntimePhaseEvidence.Phase.EXPANSION_START,
                () -> {
                    new JdbcExpansionLaboratory(session, CLOCK).start(expansion, policy);
                    return null;
                });
        new JdbcRelationalLaboratory(session, CLOCK)
                .start(
                        relational,
                        new RelationalLaboratoryPolicy(
                                DATE, DATE.plusDays(2), 1000, 10, 3, 60, 2, 0, 2, 100),
                        contracts);
        new JdbcAnalyticDimensions(session).associate(run, expansion, relational);
        RuntimePhaseEvidence.sql(
                RuntimePhaseEvidence.Phase.REFERENCE_IMPORT,
                () ->
                        new JdbcAnalyticReferences(session, CLOCK)
                                .importPackaged(
                                        run,
                                        1,
                                        DATE,
                                        DATE.plusDays(3),
                                        AnalyticLaboratoryReferencesIT.POLICIES));
        final UUID freight = UUID.randomUUID(), cap = UUID.randomUUID();
        new LocalExpansionDependencyRuntime(session, expansion, policy, CLOCK, Clock.systemUTC())
                .capture(
                        freight,
                        DataExportTemplate.FRETES,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, 1, 3, 2),
                        CancellationToken.none());
        new LocalExpansionRuntime(session, expansion, policy, CLOCK, Clock.systemUTC())
                .capture(
                        cap,
                        DataExportTemplate.CONTAS_A_PAGAR,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        ExpansionLaboratoryFixtures.source(DataExportTemplate.CONTAS_A_PAGAR, 2, 2),
                        CancellationToken.none());
        return new Fixture(run, expansion, relational, freight, cap);
    }

    private static List<AnalyticDimensionBinding> bindings(final Fixture fixture) {
        return List.of(
                binding(fixture.freight(), Role.BRANCH, "synthetic-branch-a", 1),
                binding(fixture.freight(), Role.DEST_BRANCH, "synthetic-branch-b", 1),
                binding(fixture.freight(), Role.PAYER, "synthetic-client-a", 1),
                binding(fixture.freight(), Role.SENDER, "synthetic-client-b", 1),
                binding(fixture.freight(), Role.TRACTOR, "synthetic-tractor-a", 1),
                binding(fixture.freight(), Role.TRAILER1, "synthetic-trailer-a", 1),
                binding(fixture.freight(), Role.TRAILER2, "synthetic-trailer-b", 1),
                binding(fixture.freight(), Role.DRIVER, "synthetic-driver-a", 1),
                new AnalyticDimensionBinding(
                        Entity.FRETE,
                        "INTEGER:300002",
                        fixture.freight(),
                        Role.DRIVER,
                        "synthetic-driver-b",
                        1,
                        DATE,
                        DATE.plusDays(3),
                        true,
                        null,
                        "synthetic-dimension-v1"),
                new AnalyticDimensionBinding(
                        Entity.FRETE,
                        "INTEGER:300003",
                        fixture.freight(),
                        Role.DRIVER,
                        "synthetic-generic-driver",
                        1,
                        DATE,
                        DATE.plusDays(3),
                        true,
                        null,
                        "synthetic-dimension-v1"),
                new AnalyticDimensionBinding(
                        Entity.CAP,
                        "STRING:CAP-root-1",
                        fixture.cap(),
                        Role.ACCOUNT,
                        "synthetic-account-a",
                        1,
                        DATE,
                        DATE.plusDays(3),
                        true,
                        null,
                        "synthetic-dimension-v1"),
                new AnalyticDimensionBinding(
                        Entity.CAP,
                        "STRING:CAP-root-2",
                        fixture.cap(),
                        Role.ACCOUNT,
                        "synthetic-account-b",
                        1,
                        DATE,
                        DATE.plusDays(3),
                        true,
                        null,
                        "synthetic-dimension-v1"));
    }

    private static AnalyticDimensionBinding binding(
            final UUID execution, final Role role, final String key, final int revision) {
        return new AnalyticDimensionBinding(
                Entity.FRETE,
                "INTEGER:300001",
                execution,
                role,
                key,
                revision,
                DATE,
                DATE.plusDays(3),
                true,
                null,
                "synthetic-dimension-v1");
    }

    private static long count(
            final ColetaTemporalLaboratorySession session, final UUID run, final int number)
            throws SQLException {
        if (number < 14 || number > 18) {
            throw new IllegalArgumentException("ANA_TEST_VIEW_BOUND");
        }
        return scalar(
                session,
                run,
                "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_"
                        + number
                        + " WHERE run_id=? AND reference_revision=1 AND valid_from='20360401'");
    }

    private static long scalar(
            final ColetaTemporalLaboratorySession session, final UUID run, final String sql)
            throws SQLException {
        return AnalyticLaboratoryRasterIT.scalar(session, sql, run);
    }

    private static void verifyMetadata(
            final ColetaTemporalLaboratorySession session, final UUID run) throws SQLException {
        final String[][] expected = {
            {"NomeFilial", "Hora (Solicitacao)"},
            {"Nome"},
            {"Placa", "TipoVeiculo", "Proprietario", "Filial"},
            {"NomeMotorista", "Filial"},
            {"Descricao", "Classificacao", "Hora (Solicitacao)"}
        };
        try (var connection = session.getConnection()) {
            for (int view = 14; view <= 18; view++) {
                try (var sql =
                        connection.prepareStatement(
                                "SELECT TOP(1) * FROM pub.analytic_lab_sql_"
                                        + view
                                        + " WHERE run_id=? AND reference_revision=1 ORDER BY valid_from,entity_key")) {
                    sql.setString(1, run.toString());
                    sql.setQueryTimeout(10);
                    try (var row = sql.executeQuery()) {
                        assertTrue(row.next());
                        for (int column = 0; column < expected[view - 14].length; column++) {
                            assertEquals(
                                    expected[view - 14][column],
                                    row.getMetaData().getColumnName(column + 1));
                        }
                    }
                }
            }
        }
    }

    record Fixture(UUID run, UUID expansion, UUID relational, UUID freight, UUID cap) {}
}
