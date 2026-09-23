package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryCollectorsIT.binding;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryInventoryIncidentQueriesIT {
    @Test
    void allSixtyEightColumnsHavePhysicalValuesAndPreserveCurrentComponentGrain() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            final UUID inv = capture(session, fixture, DataExportTemplate.INVENTARIO, 1, true);
            final UUID sin = capture(session, fixture, DataExportTemplate.SINISTROS, 1, true);
            bind(session, fixture, inv, sin, true);
            final JsonNode catalog =
                    json("docs/catalogos/macrobloco-analitico/contratos-colunas-inicial.json");
            for (final var contract : catalog.path("contracts")) {
                final String id = contract.path("id").asText();
                if (!id.equals("SQL-11") && !id.equals("SQL-12")) {
                    continue;
                }
                try (var connection = session.getConnection();
                        var sql =
                                connection.prepareStatement(query(id.equals("SQL-11") ? 11 : 12))) {
                    sql.setQueryTimeout(10);
                    sql.setString(1, fixture.run().toString());
                    try (var row = sql.executeQuery()) {
                        int count = 0;
                        while (row.next()) {
                            count++;
                            int ordinal = 0;
                            for (final var column : contract.path("columns")) {
                                final String name = column.path("name").asText();
                                assertEquals(name, row.getMetaData().getColumnLabel(++ordinal));
                                assertNotNull(row.getObject(ordinal), id + ":" + name);
                            }
                            assertEquals(34, ordinal);
                            if (id.equals("SQL-11")) {
                                assertEquals("Carregamento", row.getString("Tipo"));
                                assertEquals("finalizado", row.getString("Status"));
                                assertEquals("Sim", row.getString("Comprovante Anexado"));
                                assertEquals("SYNTHETIC BRANCH A", row.getString("Filial"));
                                assertEquals(
                                        "SYNTHETIC-NOTE-A, SYNTHETIC-NOTE-B",
                                        row.getString("Notas Fiscais"));
                                assertEquals(
                                        new BigDecimal("100.00000000"),
                                        row.getBigDecimal("Valor de NF"));
                                assertEquals(
                                        new BigDecimal("12.50000000"),
                                        row.getBigDecimal("Peso Real"));
                                assertEquals(
                                        28,
                                        row.getMetaData()
                                                .getPrecision(row.findColumn("Valor de NF")));
                                assertEquals(
                                        8,
                                        row.getMetaData().getScale(row.findColumn("Valor de NF")));
                                assertEquals("08:00:00.0000000", row.getString("Hora (Início)"));
                                final var meta =
                                        new ObjectMapper().readTree(row.getString("Metadata"));
                                assertTrue(
                                        meta.path("cnr_c_s_fit_dpn_performance_finished_at")
                                                .path("raw")
                                                .asText()
                                                .contains("123456789"));
                            } else {
                                assertEquals("SYN0001", row.getString("Veículo/Placa"));
                                assertEquals(
                                        "SYNTHETIC BRANCH B",
                                        row.getString("Pessoa/Nome fantasia"));
                                assertEquals(
                                        "enviado para tratativa de avarias",
                                        row.getString("Tratativa/Tipo tratativa"));
                                assertEquals(
                                        "redespacho sem cobrança de frete",
                                        row.getString("Tratativa/Solução"));
                                assertEquals("10:01:02.1234567", row.getString("Hora ocorrência"));
                                assertEquals(
                                        new BigDecimal("100.00000000"),
                                        row.getBigDecimal("Resultado final"));
                            }
                        }
                        assertEquals(2, count, id);
                    }
                }
            }
            capture(session, fixture, DataExportTemplate.INVENTARIO, 1, true);
            capture(session, fixture, DataExportTemplate.SINISTROS, 1, true);
            assertEquals(2, count(session, fixture.run(), 11));
            assertEquals(2, count(session, fixture.run(), 12));
        }
    }

    @Test
    void explicitInventoryFreightBranchOverridesDocumentedFallbackWithoutFanout() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            final UUID inv = capture(session, fixture, DataExportTemplate.INVENTARIO, 1, true);
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    binding(
                                            Entity.INV,
                                            "STRING:INV-root-1",
                                            inv,
                                            Role.BRANCH,
                                            "synthetic-branch-a",
                                            1,
                                            null),
                                    binding(
                                            Entity.FRETE,
                                            "INTEGER:300001",
                                            fixture.freight(),
                                            Role.BRANCH,
                                            "synthetic-branch-b",
                                            1,
                                            null)),
                            CancellationToken.none());
            assertEquals(
                    2,
                    branchCount(
                            session,
                            fixture.run(),
                            "SYNTHETIC BRANCH A",
                            "INVENTORY_BRANCH_FALLBACK"));
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
                                    "synthetic-query-inv-link",
                                    1)),
                    CancellationToken.none());
            relations.resolve(fixture.expansion());
            assertEquals(
                    1,
                    branchCount(
                            session,
                            fixture.run(),
                            "SYNTHETIC BRANCH B",
                            "EXPLICIT_INV_FREIGHT_BRANCH"));
            assertEquals(
                    1,
                    branchCount(
                            session,
                            fixture.run(),
                            "SYNTHETIC BRANCH A",
                            "INVENTORY_BRANCH_FALLBACK"));
            assertEquals(2, count(session, fixture.run(), 11));
        }
    }

    @Test
    void missingVehicleBlocksOnlyIncidentOutputAndInactiveComponentsLeavePublication()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            final UUID inv = capture(session, fixture, DataExportTemplate.INVENTARIO, 1, true);
            final UUID sin = capture(session, fixture, DataExportTemplate.SINISTROS, 1, true);
            bind(session, fixture, inv, sin, false);
            assertEquals(2, count(session, fixture.run(), 11));
            assertEquals(0, count(session, fixture.run(), 12));
            assertEquals(
                    2,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_sinistros_projection WHERE run_id=? AND re"
                                    + "ference_revision=2 AND disposition='VEHICLE_UNRESOLVED'",
                            fixture.run()));
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    binding(
                                            Entity.SIN,
                                            "STRING:SIN-root-1",
                                            sin,
                                            Role.TRACTOR,
                                            "synthetic-tractor-a",
                                            1,
                                            null)),
                            CancellationToken.none());
            assertEquals(2, count(session, fixture.run(), 12));
            capture(session, fixture, DataExportTemplate.INVENTARIO, 2, false);
            capture(session, fixture, DataExportTemplate.SINISTROS, 2, false);
            assertEquals(0, count(session, fixture.run(), 11));
            assertEquals(0, count(session, fixture.run(), 12));
        }
    }

    static AnalyticLaboratoryDimensionsIT.Fixture start(
            final ColetaTemporalLaboratorySession session) throws Exception {
        final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
        new JdbcAnalyticReferences(session, CLOCK)
                .importManifestPackaged(
                        fixture.run(),
                        2,
                        DATE,
                        DATE.plusDays(3),
                        AnalyticLaboratoryReferencesIT.POLICIES);
        return fixture;
    }

    static UUID capture(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final DataExportTemplate template,
            final int revision,
            final boolean active)
            throws Exception {
        return capture(session, fixture, template, revision, active, filled(template));
    }

    static UUID capture(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final DataExportTemplate template,
            final int revision,
            final boolean active,
            final ObjectNode seed)
            throws Exception {
        final UUID execution = UUID.randomUUID();
        final var source =
                new ExpansionSyntheticSource(
                        page -> {
                            final var rows = JsonNodeFactory.instance.arrayNode();
                            final int start = (page - 1) * 2;
                            for (int index = start; index < Math.min(3, start + 2); index++) {
                                final var row =
                                        ExpansionLaboratoryFixtures.envelope(
                                                template,
                                                seed.deepCopy(),
                                                index + 1,
                                                1,
                                                index == 1 ? 2 : 1);
                                ((ObjectNode) row.path("binding"))
                                        .put("revision", revision)
                                        .put("active", active);
                                rows.add(row);
                            }
                            return rows.toString();
                        });
        new LocalExpansionRuntime(
                        session,
                        fixture.expansion(),
                        new ExpansionPolicy(
                                DATE,
                                DATE.plusDays(3),
                                DATE,
                                2,
                                100,
                                1000,
                                FiscalPolicy.SYNTHETIC_CTE),
                        CLOCK,
                        Clock.systemUTC())
                .capture(
                        execution,
                        template,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        source,
                        CancellationToken.none());
        return execution;
    }

    static ObjectNode filled(final DataExportTemplate template) throws Exception {
        final var seed = ExpansionLaboratoryFixtures.data(template);
        final String code =
                br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory.vertical(
                        template);
        for (final var spec :
                json("docs/catalogos/macrobloco-expansao/campos-tipados.json").path("specs")) {
            if (!spec.path("code").asText().equals(code)) {
                continue;
            }
            for (final var field : spec.path("fields")) {
                final String name = field.path("name").asText();
                if (seed.hasNonNull(name)) {
                    continue;
                }
                switch (field.path("kind").asText()) {
                    case "INTEGER" -> seed.put(name, 7);
                    case "DECIMAL" -> seed.put(name, "7.12500000");
                    case "BOOLEAN" -> seed.put(name, false);
                    case "INSTANT" -> seed.put(name, "2036-04-01T11:00:00.123456789-03:00");
                    case "DATE" -> seed.put(name, "2036-04-01");
                    case "TIME" -> seed.put(name, "11:22:33.123456789");
                    case "STRINGS" -> seed.putArray(name).add("SYNTHETIC A").add("SYNTHETIC B");
                    case "TEXT", "IDENTIFIER" -> seed.put(name, "SYNTHETIC " + name);
                    default -> throw new IllegalArgumentException("UNEXPECTED_FIXTURE_KIND");
                }
            }
        }
        if (code.equals("INV")) {
            seed.put("type", "CheckIn::Order::Loading");
        }
        if (code.equals("FAT")) {
            seed.put("fit_nse_number", 7).put("nfse_number", "7");
        }
        return seed;
    }

    private static void bind(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final UUID inv,
            final UUID sin,
            final boolean vehicle)
            throws Exception {
        final var bindings =
                new java.util.ArrayList<>(
                        List.of(
                                binding(
                                        Entity.INV,
                                        "STRING:INV-root-1",
                                        inv,
                                        Role.BRANCH,
                                        "synthetic-branch-a",
                                        1,
                                        null),
                                binding(
                                        Entity.SIN,
                                        "STRING:SIN-root-1",
                                        sin,
                                        Role.BRANCH,
                                        "synthetic-branch-b",
                                        1,
                                        null)));
        if (vehicle) {
            bindings.add(
                    binding(
                            Entity.SIN,
                            "STRING:SIN-root-1",
                            sin,
                            Role.TRACTOR,
                            "synthetic-tractor-a",
                            1,
                            null));
        }
        new JdbcAnalyticDimensions(session)
                .bindBatch(fixture.run(), bindings, CancellationToken.none());
    }

    private static String query(final int number) {
        if (number != 11 && number != 12) {
            throw new IllegalArgumentException("QUERY_BOUND");
        }
        return "SELECT * FROM pub.analytic_lab_sql_"
                + number
                + " WHERE run_id=? AND reference_revision=2 ORDER BY business_date,source_key,component_id";
    }

    private static long count(
            final ColetaTemporalLaboratorySession session, final UUID run, final int number)
            throws Exception {
        if (number != 11 && number != 12) {
            throw new IllegalArgumentException("QUERY_BOUND");
        }
        return AnalyticLaboratoryRasterIT.scalar(
                session,
                "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_"
                        + number
                        + " WHERE run_id=? AND reference_revision=2",
                run);
    }

    private static long branchCount(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final String branch,
            final String provenance)
            throws Exception {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_11 WHERE run_id=? AND reference_revisio"
                                        + "n=2 AND [Filial Emissora do Frete]=? AND issuer_branch_provenance=?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, branch);
            sql.setString(3, provenance);
            try (var row = sql.executeQuery()) {
                assertTrue(row.next());
                return row.getLong(1);
            }
        }
    }

    private static JsonNode json(final String path) throws Exception {
        try (var input = Files.newInputStream(Path.of(path))) {
            return new ObjectMapper().readTree(input);
        }
    }
}
