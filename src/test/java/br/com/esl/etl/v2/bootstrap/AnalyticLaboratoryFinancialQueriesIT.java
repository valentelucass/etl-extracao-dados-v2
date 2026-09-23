package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryCollectorsIT.binding;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryInventoryIncidentQueriesIT.capture;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryInventoryIncidentQueriesIT.filled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFiscalAttribute;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFiscalAttributes;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionReferences;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryFinancialQueriesIT {
    @Test
    void seventyThreeColumnsUseCapturedValuesBoundAccountsAndGovernedFinancialPolicies()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = setup(session, false);
            series(session, fixture, 1, "SÉRIE-ÚNICA");
            try (var input =
                    Files.newInputStream(
                            Path.of(
                                    "docs/catalogos/macrobloco-analitico/contratos-colunas-inicial.json"))) {
                for (final var contract : new ObjectMapper().readTree(input).path("contracts")) {
                    final String id = contract.path("id").asText();
                    if (!id.equals("SQL-01") && !id.equals("SQL-06")) {
                        continue;
                    }
                    try (var connection = session.getConnection();
                            var sql =
                                    connection.prepareStatement(
                                            query(id.equals("SQL-01") ? 1 : 6))) {
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
                                if (id.equals("SQL-01")) {
                                    assertEquals(42, ordinal);
                                    assertEquals("7", row.getString("Número do Documento"));
                                    assertEquals("SÉRIE-ÚNICA", row.getString("NFS-e/Série"));
                                    assertEquals("Faturado", row.getString("Status do Processo"));
                                    assertEquals("00000000000000", row.getString("Cliente/CNPJ"));
                                    assertEquals("Autorizado", row.getString("CT-e/Status"));
                                    assertEquals("Normal", row.getString("Tipo"));
                                    assertEquals(
                                            new BigDecimal("100.00000000"),
                                            row.getBigDecimal("Fatura/Valor"));
                                    assertEquals(
                                            new BigDecimal("120.00000000"),
                                            row.getBigDecimal("Frete/Valor dos CT-es"));
                                    assertEquals(
                                            "SYNTHETIC fit_ant_tat_bro_description",
                                            row.getString("Carteira/Descrição"));
                                    assertEquals(
                                            "SYNTHETIC fit_rpt_name",
                                            row.getString("Remetente/Nome"));
                                    assertEquals(
                                            "SYNTHETIC fit_sdr_name",
                                            row.getString("Destinatário/Nome"));
                                } else {
                                    assertEquals(31, ordinal);
                                    assertEquals("Adiantamento", row.getString("Tipo"));
                                    assertEquals("Não", row.getString("Pago"));
                                    assertEquals("ABERTO", row.getString("Status Pagamento"));
                                    assertEquals("Não conciliado", row.getString("Conciliado"));
                                    assertEquals(
                                            "4. Custos Variáveis",
                                            row.getString("Conta Contábil/Classificação"));
                                    assertEquals(
                                            "SYNTHETIC ACCOUNT",
                                            row.getString("Conta Contábil/Descrição"));
                                    assertEquals(
                                            new BigDecimal("100.00000000"),
                                            row.getBigDecimal("Valor"));
                                    assertEquals(
                                            new BigDecimal(
                                                    count == 1 ? "40.00000000" : "60.00000000"),
                                            row.getBigDecimal("Centro de custo/Valor"));
                                    assertEquals(
                                            28,
                                            row.getMetaData()
                                                    .getPrecision(row.findColumn("Valor")));
                                    assertEquals(
                                            8, row.getMetaData().getScale(row.findColumn("Valor")));
                                }
                            }
                            assertEquals(2, count, id);
                        }
                    }
                }
            }
        }
    }

    @Test
    void placeholdersShareTheExistingMat04DecisionAndCapPreservesNullableReconciliation()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = setup(session, true);
            series(session, fixture, 1, "SERIE-1");
            final var relations = new JdbcExpansionRelations(session, CLOCK);
            relations.bind(
                    fixture.expansion(),
                    List.of(
                            ExpansionLaboratoryRelationsIT.binding(
                                    ExpansionRelation.Kind.FAT_DOCUMENT_FREIGHT,
                                    1,
                                    1,
                                    1,
                                    ExpansionRelation.Cardinality.MANY_TO_MANY,
                                    "synthetic-query-fat-1",
                                    1),
                            ExpansionLaboratoryRelationsIT.binding(
                                    ExpansionRelation.Kind.FAT_DOCUMENT_FREIGHT,
                                    1,
                                    2,
                                    1,
                                    ExpansionRelation.Cardinality.MANY_TO_MANY,
                                    "synthetic-query-fat-2",
                                    1)),
                    CancellationToken.none());
            relations.resolve(fixture.expansion());
            assertEquals(
                    1,
                    new JdbcExpansionMaterializations(session, CLOCK)
                            .invoices(
                                    fixture.expansion(),
                                    UUID.randomUUID(),
                                    2,
                                    ExecutionMode.BACKFILL,
                                    true,
                                    DATE,
                                    DATE.plusDays(3))
                            .ready());
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM mart.expansion_lab_invoice WHERE run_id=? AND has_invoice=0 "
                                    + "AND process_state=N'Aguardando Faturamento' AND disposition='READY'",
                            fixture.expansion()));
            assertEquals(
                    2,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_01 WHERE run_id=? AND reference_revisio"
                                    + "n=2 AND [Status do Processo]=N'Aguardando Faturamento' AND has_invoice=0",
                            fixture.run()));
            final var paid = filled(DataExportTemplate.CONTAS_A_PAGAR);
            paid.put("paid", true).put("paid_value", "100.00").putNull("ant_ils_atn_reconciled");
            capture(session, fixture, DataExportTemplate.CONTAS_A_PAGAR, 3, true, paid);
            try (var connection = session.getConnection();
                    var sql = connection.prepareStatement(query(6))) {
                sql.setQueryTimeout(10);
                sql.setString(1, fixture.run().toString());
                try (var row = sql.executeQuery()) {
                    int count = 0;
                    while (row.next()) {
                        count++;
                        assertEquals("Sim", row.getString("Pago"));
                        assertEquals("PAGO", row.getString("Status Pagamento"));
                        assertNull(row.getString("Conciliado"));
                    }
                    assertEquals(2, count);
                }
            }
        }
    }

    @Test
    void missingSeriesBlocksOnlyDependentOutputAndDivergentBindingDoesNotErasePriorWork()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = setup(session, false);
            assertEquals(0, count(session, fixture.run(), 1));
            assertEquals(2, count(session, fixture.run(), 6));
            final var attributes = series(session, fixture, 1, "S1");
            final var repository = new JdbcAnalyticFiscalAttributes(session);
            assertEquals(
                    2, repository.bindBatch(fixture.run(), attributes, CancellationToken.none()));
            final var original = attributes.get(0);
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    repository.bindBatch(
                                            fixture.run(),
                                            List.of(
                                                    new AnalyticFiscalAttribute(
                                                            original.componentId(),
                                                            original.sourceExecution(),
                                                            1,
                                                            "DIVERGENT")),
                                            CancellationToken.none()));
            assertEquals(53684, failure.getErrorCode());
            assertEquals(2, count(session, fixture.run(), 1));
            assertEquals(2, count(session, fixture.run(), 6));
            series(session, fixture, 2, null);
            assertEquals(2, count(session, fixture.run(), 1));
            capture(session, fixture, DataExportTemplate.FATURAS_POR_CLIENTE, 2, true);
            assertEquals(0, count(session, fixture.run(), 1));
            assertEquals(2, count(session, fixture.run(), 6));
        }
    }

    private static AnalyticLaboratoryDimensionsIT.Fixture setup(
            final ColetaTemporalLaboratorySession session, final boolean placeholder)
            throws Exception {
        final var fixture = AnalyticLaboratoryInventoryIncidentQueriesIT.start(session);
        new JdbcExpansionReferences(session, CLOCK)
                .importPackaged(fixture.expansion(), 2, DATE, DATE.plusDays(3));
        final var seed = filled(DataExportTemplate.FATURAS_POR_CLIENTE);
        if (placeholder) {
            seed.put("fit_ant_document", " Faturado ");
        }
        final UUID fat =
                capture(session, fixture, DataExportTemplate.FATURAS_POR_CLIENTE, 1, true, seed);
        final UUID cap = capture(session, fixture, DataExportTemplate.CONTAS_A_PAGAR, 2, true);
        new JdbcAnalyticDimensions(session)
                .bindBatch(
                        fixture.run(),
                        List.of(
                                binding(
                                        Entity.FAT,
                                        "STRING:FAT-root-1",
                                        fat,
                                        Role.BRANCH,
                                        "synthetic-branch-a",
                                        1,
                                        null),
                                binding(
                                        Entity.CAP,
                                        "STRING:CAP-root-1",
                                        cap,
                                        Role.BRANCH,
                                        "synthetic-branch-b",
                                        1,
                                        null),
                                binding(
                                        Entity.CAP,
                                        "STRING:CAP-root-1",
                                        cap,
                                        Role.ACCOUNT,
                                        "synthetic-account-a",
                                        1,
                                        null)),
                        CancellationToken.none());
        return fixture;
    }

    static List<AnalyticFiscalAttribute> series(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final int revision,
            final String value)
            throws Exception {
        final var batch = new ArrayList<AnalyticFiscalAttribute>();
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT TOP(3) component_id,execution_id FROM core.expansion_lab_current_input WHERE r"
                                        + "un_id=? AND vertical='FAT' ORDER BY component_id")) {
            sql.setQueryTimeout(10);
            sql.setString(1, fixture.expansion().toString());
            try (var row = sql.executeQuery()) {
                while (row.next()) {
                    batch.add(
                            new AnalyticFiscalAttribute(
                                    row.getLong(1),
                                    UUID.fromString(row.getString(2)),
                                    revision,
                                    value));
                }
            }
        }
        assertEquals(2, batch.size());
        assertEquals(
                2,
                new JdbcAnalyticFiscalAttributes(session)
                        .bindBatch(fixture.run(), batch, CancellationToken.none()));
        return List.copyOf(batch);
    }

    private static String query(final int number) {
        if (number != 1 && number != 6) {
            throw new IllegalArgumentException("QUERY_BOUND");
        }
        return "SELECT * FROM pub.analytic_lab_sql_0"
                + number
                + " WHERE run_id=? AND reference_revision=2 ORDER BY business_date,source_key,component_id";
    }

    private static long count(
            final ColetaTemporalLaboratorySession session, final UUID run, final int number)
            throws Exception {
        if (number != 1 && number != 6) {
            throw new IllegalArgumentException("QUERY_BOUND");
        }
        return AnalyticLaboratoryRasterIT.scalar(
                session,
                "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_0"
                        + number
                        + " WHERE run_id=? AND reference_revision=2",
                run);
    }
}
