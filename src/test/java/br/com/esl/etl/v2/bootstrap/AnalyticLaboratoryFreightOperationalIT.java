package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreightAnalyticAttributesMapper;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticMaterializationRequest;
import br.com.esl.etl.v2.plataforma.analitico.FreightSupplementObservation;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreightTerms;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcFreightAnalyticAttributes;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryFreightOperationalIT {
    static final LocalDate DATE = LocalDate.of(2036, 4, 1);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2036-04-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void canonicalIndicatorsConsumeCapturesAndReplayWithoutMultiplyingRoots() throws Exception {
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
                    "2036-04-02T02:59:59.999999999Z",
                    row -> {});
            final var request = request(fixture.run());
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            final var receipt = repository.freight(request, CancellationToken.none());
            assertEquals(new JdbcAnalyticMaterializations.Receipt(6, 6, 0, 0, 2, 4), receipt);
            assertEquals(receipt, repository.freight(request, CancellationToken.none()));
            assertEquals(
                    new JdbcAnalyticMaterializations.Receipt(6, 0, 0, 6, 2, 4),
                    repository.freight(request(fixture.run()), CancellationToken.none()));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM pub.analytic_lab_freight_operational WHERE run_id=? ORDER BY indicator")) {
                sql.setString(1, fixture.run().toString());
                sql.setQueryTimeout(10);
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals("CB", row.getString("indicator"));
                    assertEquals(DATE, row.getObject("reference_date", LocalDate.class));
                    assertEquals(DATE, row.getObject("completion_date", LocalDate.class));
                    assertEquals(-1, row.getInt("performance_days"));
                    assertEquals("NO PRAZO", row.getString("performance_status"));
                    assertEquals(1, row.getInt("performance_code"));
                    assertEquals("OFFICIAL_6389", row.getString("completion_provenance"));
                    assertEquals("CT-E", row.getString("document_type"));
                    assertEquals(new BigDecimal("120.00000000"), row.getBigDecimal("total_value"));
                    assertEquals(12, row.getInt("volumes"));
                    assertTrue(row.getBoolean("is_cubed"));
                    assertEquals("synthetic-branch-b", row.getString("performance_branch_key"));
                    assertTrue(row.next());
                    assertEquals("PE", row.getString("indicator"));
                    assertEquals(
                            DATE.plusDays(1), row.getObject("reference_date", LocalDate.class));
                    assertTrue(!row.next());
                }
            }
        }
    }

    @Test
    void dateCorrectionReplacesSameGrainAndRetainsOldPartitionAndHistory() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            bind(session, fixture);
            capture(session, fixture, 1, "120", true, false, "done", null, row -> {});
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            repository.freight(request(fixture.run()), CancellationToken.none());
            assertEquals(
                    2,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_freight_operational WHERE run_id=? AND perf"
                                    + "ormance_code=0 AND performance_status=N'EM ABERTO'"));
            capture(
                    session,
                    fixture,
                    2,
                    "120",
                    true,
                    false,
                    "done",
                    null,
                    row -> row.put("data_previsao_entrega", "2036-04-03"));
            final var result =
                    repository.freight(
                            new AnalyticMaterializationRequest(
                                    fixture.run(),
                                    UUID.randomUUID(),
                                    1,
                                    ExecutionMode.INCREMENTAL,
                                    false,
                                    DATE,
                                    DATE.plusDays(1)),
                            CancellationToken.none());
            assertEquals(2, result.updates());
            assertEquals(2, result.ready());
            assertEquals(
                    1,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_freight_operational WHERE run_id=? AND indi"
                                    + "cator='PE' AND reference_date='20360403' AND old_reference_date='20360402'"));
            assertEquals(
                    6,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM mart.analytic_freight_operational WHERE run_id=?"));
            assertEquals(
                    0,
                    scalar(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_freight_operational WHERE run_id=? AND indi"
                                    + "cator='PE' AND reference_date='20360402'"));
        }
    }

    @Test
    void financialAndDocumentGatesHaveIndependentPeCbExpectations() throws Exception {
        final List<Gate> cases =
                List.of(
                        new Gate(
                                "120", true, false, "done", "Normal", true, false, false, true, 2,
                                "READY"),
                        new Gate(
                                "0",
                                true,
                                false,
                                "done",
                                "Normal",
                                true,
                                false,
                                false,
                                true,
                                1,
                                "VALUE_THRESHOLD"),
                        new Gate(
                                "-2",
                                true,
                                false,
                                "done",
                                "Normal",
                                true,
                                false,
                                false,
                                true,
                                1,
                                "VALUE_THRESHOLD"),
                        new Gate(
                                "0.01",
                                true,
                                false,
                                "done",
                                "Normal",
                                true,
                                false,
                                false,
                                true,
                                1,
                                "VALUE_THRESHOLD"),
                        new Gate(
                                "0.01000001",
                                true,
                                false,
                                "done",
                                "Normal",
                                true,
                                false,
                                false,
                                true,
                                2,
                                "READY"),
                        new Gate(
                                null,
                                true,
                                false,
                                "done",
                                "Normal",
                                true,
                                false,
                                false,
                                true,
                                1,
                                "VALUE_THRESHOLD"),
                        new Gate(
                                "0.01",
                                true,
                                false,
                                "pending",
                                "Normal",
                                false,
                                false,
                                false,
                                true,
                                0,
                                "INELIGIBLE"),
                        new Gate(
                                "120",
                                true,
                                false,
                                "pending",
                                "Substitute",
                                false,
                                false,
                                false,
                                true,
                                0,
                                "INELIGIBLE"),
                        new Gate(
                                "120",
                                true,
                                false,
                                "done",
                                "Normal",
                                false,
                                true,
                                false,
                                true,
                                0,
                                "INELIGIBLE"),
                        new Gate(
                                "120", true, false, "done", "Normal", true, true, false, true, 2,
                                "READY"),
                        new Gate(
                                "120",
                                true,
                                false,
                                "done",
                                "Normal",
                                true,
                                false,
                                true,
                                true,
                                1,
                                "PAYER_EXCLUDED"),
                        new Gate(
                                "120",
                                true,
                                true,
                                "done",
                                "Normal",
                                true,
                                false,
                                false,
                                true,
                                0,
                                "COURTESY"),
                        new Gate(
                                "120",
                                false,
                                false,
                                "done",
                                "Normal",
                                true,
                                false,
                                false,
                                true,
                                0,
                                "INACTIVE"),
                        new Gate(
                                "120",
                                true,
                                false,
                                "cancelled",
                                "Normal",
                                true,
                                false,
                                false,
                                true,
                                0,
                                "CANCELLED"),
                        new Gate(
                                "120",
                                true,
                                false,
                                "done",
                                "Complementar",
                                true,
                                false,
                                false,
                                true,
                                0,
                                "COMPLEMENTARY"),
                        new Gate(
                                "120", true, false, "done", "Normal", true, false, false, false, 1,
                                "READY"));
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            bind(session, fixture);
            int revision = 0;
            for (final var gate : cases) {
                capture(
                        session,
                        fixture,
                        ++revision,
                        gate.total(),
                        gate.active(),
                        gate.courtesy(),
                        gate.status(),
                        null,
                        row -> {
                            row.put("tipo_frete", "Freight::" + gate.type());
                            if (!gate.document()) {
                                clearDocuments(row);
                            }
                            if (gate.branchDocument()) {
                                row.put("pagador_documento", "SYNTHETIC-BRANCH-DOCUMENT-A");
                            }
                            if (gate.excludedPayer()) {
                                row.put("pagador_documento", "synthetic-excluded-payer");
                            }
                            if (!gate.forecast()) {
                                row.putNull("data_previsao_entrega");
                            }
                        });
                final var receipt =
                        new JdbcAnalyticMaterializations(session, CLOCK)
                                .freight(request(fixture.run()), CancellationToken.none());
                assertEquals(gate.ready(), receipt.ready(), gate.toString());
                try (var connection = session.getConnection();
                        var sql =
                                connection.prepareStatement(
                                        "SELECT o.disposition FROM mart.analytic_freight_operational c JOIN mart.analytic_frei"
                                                + "ght_operational_observation o ON o.observation_id=c.observation_id WHERE c.run_id=? A"
                                                + "ND o.source_key=N'INTEGER:300001' AND c.indicator='CB'")) {
                    sql.setString(1, fixture.run().toString());
                    sql.setQueryTimeout(10);
                    try (var row = sql.executeQuery()) {
                        assertTrue(row.next());
                        assertEquals(gate.cbDisposition(), row.getString(1), gate.toString());
                    }
                }
            }
        }
    }

    @Test
    void sameReceiptWithChangedInputIsRejectedAndDoesNotCreateAnObservation() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            bind(session, fixture);
            capture(session, fixture, 1, "120", true, false, "done", null, row -> {});
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            final var request = request(fixture.run());
            repository.freight(request, CancellationToken.none());
            capture(session, fixture, 2, "121", true, false, "done", null, row -> {});
            assertEquals(
                    53585,
                    assertThrows(
                                    SQLException.class,
                                    () -> repository.freight(request, CancellationToken.none()))
                            .getErrorCode());
        }
    }

    static void capture(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final int revision,
            final String total,
            final boolean active,
            final boolean courtesy,
            final String status,
            final String completion,
            final Consumer<ObjectNode> customize)
            throws Exception {
        final var freight = ExpansionDependencyFixtures.data(DataExportTemplate.FRETES, 1);
        new br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticSourceContracts(session)
                .freightPerformance(fixture.run());
        freight.put(
                "cte_created_at",
                Instant.parse("2036-04-01T14:00:00Z").plusSeconds(revision).toString());
        if (total == null) {
            freight.putNull("total");
        } else {
            freight.put("total", total);
        }
        freight.put("status", status);
        if (completion != null) {
            freight.put("fit_dpn_performance_finished_at", completion);
        }
        final var original = ExpansionDependencyFixtures.financialTerms("INTEGER:300001");
        final var terms =
                new ExpansionFreightTerms(
                        original.sourceKey(),
                        revision,
                        DATE,
                        original.classification(),
                        courtesy,
                        true,
                        original.fallbackVolumes(),
                        original.payerToken(),
                        "BRL",
                        "MAJOR",
                        active,
                        original.evidence());
        final var source =
                new ExpansionDependencySource(page -> page == 1 ? "[" + freight + "]" : "[]")
                        .withFinancialBindings(key -> terms)
                        .withAnalyticFreightPerformance();
        final var policy =
                new ExpansionPolicy(
                        DATE, DATE.plusDays(3), DATE, 2, 100, 1000, FiscalPolicy.SYNTHETIC_CTE);
        final var execution = UUID.randomUUID();
        new LocalExpansionDependencyRuntime(
                        session, fixture.expansion(), policy, CLOCK, Clock.systemUTC())
                .capture(
                        execution,
                        DataExportTemplate.FRETES,
                        DATE,
                        ExecutionMode.BACKFILL,
                        null,
                        source,
                        CancellationToken.none());
        final var envelope = AnalyticLaboratoryFreightAttributesIT.data();
        customize.accept((ObjectNode) envelope.path("attributes"));
        new JdbcFreightAnalyticAttributes(session)
                .captureBatch(
                        fixture.run(),
                        execution,
                        List.of(
                                new FreightSupplementObservation(
                                        "INTEGER:300001",
                                        revision,
                                        new FreightAnalyticAttributesMapper().map(envelope),
                                        "synthetic-mat01-attributes")),
                        CancellationToken.none());
    }

    static void bind(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture)
            throws SQLException {
        new JdbcAnalyticDimensions(session)
                .bindBatch(
                        fixture.run(),
                        List.of(
                                new AnalyticDimensionBinding(
                                        Entity.FRETE,
                                        "INTEGER:300001",
                                        fixture.freight(),
                                        Role.BRANCH,
                                        "synthetic-branch-a",
                                        1,
                                        DATE,
                                        DATE.plusDays(3),
                                        true,
                                        null,
                                        "synthetic-mat01-branch"),
                                new AnalyticDimensionBinding(
                                        Entity.FRETE,
                                        "INTEGER:300001",
                                        fixture.freight(),
                                        Role.DEST_BRANCH,
                                        "synthetic-branch-b",
                                        1,
                                        DATE,
                                        DATE.plusDays(3),
                                        true,
                                        null,
                                        "synthetic-mat01-branch")),
                        CancellationToken.none());
    }

    static AnalyticMaterializationRequest request(final UUID run) {
        return new AnalyticMaterializationRequest(
                run, UUID.randomUUID(), 1, ExecutionMode.BACKFILL, true, DATE, DATE.plusDays(3));
    }

    private static long scalar(
            final ColetaTemporalLaboratorySession session, final UUID run, final String sql)
            throws SQLException {
        return AnalyticLaboratoryRasterIT.scalar(session, sql, run);
    }

    private static void clearDocuments(final ObjectNode row) {
        for (final var field :
                List.of(
                        "cte_id",
                        "chave_cte",
                        "numero_cte",
                        "serie_cte",
                        "nfse_number",
                        "nfse_series",
                        "nfse_xml_document",
                        "nfse_integration_id")) {
            row.putNull(field);
        }
    }

    record Gate(
            String total,
            boolean active,
            boolean courtesy,
            String status,
            String type,
            boolean document,
            boolean branchDocument,
            boolean excludedPayer,
            boolean forecast,
            int ready,
            String cbDisposition) {}
}
