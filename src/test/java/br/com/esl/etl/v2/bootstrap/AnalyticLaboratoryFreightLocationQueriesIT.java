package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.bind;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.capture;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.sql.ResultSetMetaData;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryFreightLocationQueriesIT {
    @Test
    void sql02ReturnsAll123ColumnsFromCapturedAttributesAndPreparedPerformance() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            bind(session, fixture);
            capture(
                    session,
                    fixture,
                    1,
                    "120",
                    true,
                    false,
                    "done",
                    "2036-04-02T03:00:00Z",
                    row -> {});
            new JdbcAnalyticMaterializations(session, CLOCK)
                    .freight(request(fixture.run()), CancellationToken.none());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM pub.analytic_lab_sql_02 WHERE run_id=? AND reference_revision=1 ORDER B"
                                            + "Y dependency_id")) {
                sql.setQueryTimeout(10);
                sql.setString(1, fixture.run().toString());
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    metadata(row.getMetaData(), "SQL-02", 123);
                    assertEquals(300001, row.getLong("ID"));
                    assertEquals(600001, row.getLong("Nº Minuta"));
                    assertEquals("CT-e", row.getString("Documento Oficial/Tipo"));
                    assertNull(row.getString("Documento Oficial/XML"));
                    assertEquals("finalizado", row.getString("Status"));
                    assertEquals("NO PRAZO", row.getString("Performance Status"));
                    assertEquals("NO PRAZO", row.getString("Performance Status Dif de Dias"));
                    for (int column = 1; column <= 123; column++) {
                        final String name = row.getMetaData().getColumnName(column);
                        if (!List.of("Documento Oficial/XML", "Criado em", "Data de Finalização")
                                .contains(name)) {
                            assertTrue(row.getObject(column) != null, name);
                        }
                    }
                    assertEquals(
                            28,
                            row.getMetaData()
                                    .getPrecision(row.findColumn("Valor Total do Serviço")));
                    assertEquals(
                            8,
                            row.getMetaData().getScale(row.findColumn("Valor Total do Serviço")));
                    assertTrue(!row.next());
                }
            }
        }
    }

    @Test
    void sql02KeepsNullPerformanceAndExactNineSignedDayBands() throws Exception {
        final String[] bands = {
            "ACIMA DE 3 DIAS ANTES",
            "3 DIAS ANTES",
            "2 DIAS ANTES",
            "1 DIA ANTES",
            "NO PRAZO",
            "1 DIA DE ATRASO",
            "2 DIAS DE ATRASO",
            "3 DIAS DE ATRASO",
            "ACIMA DE 3 DIAS DE ATRASO"
        };
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            bind(session, fixture);
            for (int step = 0; step <= 9; step++) {
                final String completion =
                        step == 0
                                ? null
                                : DATE.plusDays(step - 4)
                                        .atTime(12, 0)
                                        .toInstant(ZoneOffset.UTC)
                                        .toString();
                capture(
                        session,
                        fixture,
                        step + 1,
                        "120",
                        true,
                        false,
                        "done",
                        completion,
                        row -> {});
                new JdbcAnalyticMaterializations(session, CLOCK)
                        .freight(request(fixture.run()), CancellationToken.none());
                try (var connection = session.getConnection();
                        var sql =
                                connection.prepareStatement(
                                        "SELECT [Performance Status],[Performance Status Dif de Dias],[Performance Status Dif "
                                                + "de Dias Oficial]"
                                                + " FROM pub.analytic_lab_sql_02 WHERE run_id=? AND reference_revision=1")) {
                    sql.setString(1, fixture.run().toString());
                    sql.setQueryTimeout(10);
                    try (var row = sql.executeQuery()) {
                        assertTrue(row.next());
                        if (step == 0) {
                            assertNull(row.getString(1));
                            assertNull(row.getString(2));
                            assertNull(row.getString(3));
                        } else {
                            assertEquals(bands[step - 1], row.getString(2));
                            assertEquals(bands[step - 1], row.getString(3));
                        }
                        assertTrue(!row.next());
                    }
                }
            }
        }
    }

    @Test
    void sql07Returns29ColumnsWithGovernedAliasTerminalityAndRawProvenance() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            final var data =
                    ExpansionDependencyFixtures.data(DataExportTemplate.LOCALIZACAO_CARGAS, 1);
            data.put("fit_dpn_delivery_prediction_at", "2036-04-02T12:00:00.123456789Z");
            data.put("service_type", "synthetic-service");
            data.put("fit_crn_psn_nickname", "SYNTHETIC ISSUER");
            data.put("fit_dyn_name", "SYNTHETIC REGION");
            data.put("fit_dyn_drt_nickname", " synthetic destination ");
            data.put("fit_fsn_name", "synthetic-classification");
            data.put("fit_fln_status", "delivered");
            data.put("fit_fln_cln_nickname", "SYNTHETIC CURRENT LOCATION");
            data.put("fit_o_n_name", "SYNTHETIC ORIGIN REGION");
            data.put("fit_o_n_drt_nickname", "SYNTHETIC ORIGIN");
            final var policy =
                    new ExpansionPolicy(
                            DATE, DATE.plusDays(3), DATE, 2, 100, 1000, FiscalPolicy.SYNTHETIC_CTE);
            final var locationCapture =
                    new LocalExpansionDependencyRuntime(
                                    session, fixture.expansion(), policy, CLOCK, Clock.systemUTC())
                            .capture(
                                    DataExportTemplate.LOCALIZACAO_CARGAS,
                                    DATE,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    new ExpansionDependencySource(
                                            page -> page == 1 ? "[" + data + "]" : "[]"),
                                    CancellationToken.none());
            final var json = new ObjectMapper();
            final ObjectNode refs;
            try (var input =
                    getClass()
                            .getResourceAsStream(
                                    "/analytic-laboratory/references.synthetic.json")) {
                refs = (ObjectNode) json.readTree(java.util.Objects.requireNonNull(input));
            }
            ((ArrayNode) refs.path("labels"))
                    .addObject()
                    .put("category", "REGION_ALIAS")
                    .put("raw", "SYNTHETIC DESTINATION")
                    .put("label", "SYN");
            new JdbcAnalyticReferences(session, CLOCK)
                    .importFixture(
                            fixture.run(),
                            2,
                            DATE,
                            DATE.plusDays(3),
                            AnalyticLaboratoryReferencesIT.POLICIES,
                            json.writeValueAsBytes(refs));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM pub.analytic_lab_sql_07 WHERE run_id=? ORDER BY reference_revision")) {
                sql.setString(1, fixture.run().toString());
                sql.setQueryTimeout(10);
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    metadata(row.getMetaData(), "SQL-07", 29);
                    assertEquals("SEM_MAP", row.getString("Sigla Responsável Região Destino"));
                    assertEquals("UNMAPPED", row.getString("region_provenance"));
                    assertEquals("Entregue", row.getString("Status Carga"));
                    assertEquals(1, row.getInt("Status Terminal"));
                    assertEquals(0, row.getInt("Cancelado Flag"));
                    assertNull(row.getString("Filial Atual"));
                    assertEquals("SYNTHETIC CURRENT LOCATION", row.getString("Localização Atual"));
                    assertTrue(row.next());
                    assertEquals("SYN", row.getString("Sigla Responsável Região Destino"));
                    assertEquals("GOVERNED_ALIAS", row.getString("region_provenance"));
                    assertTrue(!row.next());
                }
            }
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    new AnalyticDimensionBinding(
                                            AnalyticDimensionBinding.Entity.LOC,
                                            "INTEGER:600001",
                                            locationCapture.executionId(),
                                            AnalyticDimensionBinding.Role.CURRENT_BRANCH,
                                            "synthetic-branch-b",
                                            1,
                                            DATE,
                                            DATE.plusDays(3),
                                            true,
                                            null,
                                            "synthetic-current-location-branch")),
                            CancellationToken.none());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT [Filial Atual],status_branch_nickname_provenance,current_branch_key"
                                            + " FROM pub.analytic_lab_sql_07 WHERE run_id=? AND reference_revision=2")) {
                sql.setString(1, fixture.run().toString());
                sql.setQueryTimeout(10);
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals("SYNTHETIC BRANCH B", row.getString(1));
                    assertEquals("EXPLICIT_CURRENT_BINDING", row.getString(2));
                    assertEquals("synthetic-branch-b", row.getString(3));
                    assertTrue(!row.next());
                }
            }
        }
    }

    static void metadata(final ResultSetMetaData metadata, final String id, final int count)
            throws Exception {
        final var catalog =
                new ObjectMapper()
                        .readTree(
                                Path.of(
                                                "docs/catalogos/macrobloco-analitico/contratos-colunas-inicial.json")
                                        .toFile());
        boolean found = false;
        for (final var contract : catalog.path("contracts")) {
            if (contract.path("id").asText().equals(id)) {
                assertEquals(count, contract.path("columns").size());
                for (final var column : contract.path("columns")) {
                    assertEquals(
                            column.path("name").asText(),
                            metadata.getColumnName(column.path("ordinal").asInt()));
                }
                found = true;
            }
        }
        assertTrue(found);
        assertTrue(metadata.getColumnCount() > count);
    }
}
