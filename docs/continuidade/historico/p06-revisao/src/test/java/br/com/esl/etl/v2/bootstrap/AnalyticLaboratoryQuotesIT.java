package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryCollectorsIT.binding;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.AnalyticQuotesSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuotePromotion;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuoteTariffs;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryQuotesIT {
    @Test
    void originalRuntimePublishesTypedSnapshotsAndAllFiftyFourColumnsWithPhysicalMetadata()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            final var tariffs = new JdbcAnalyticQuoteTariffs(session);
            final var reference = tariffs.importPackaged(fixture.run(), 1, DATE, DATE.plusDays(3));
            assertEquals(25, reference.rows());
            assertEquals(
                    reference, tariffs.importPackaged(fixture.run(), 1, DATE, DATE.plusDays(3)));
            final UUID first = UUID.randomUUID();
            final var initial =
                    capture(
                            session,
                            fixture.run(),
                            first,
                            null,
                            reference.release(),
                            AnalyticQuotesFixtures.data());
            assertEquals(new JdbcAnalyticQuotePromotion.Receipt(3, 1, 1, 0, 0, 0, 0), initial);
            bind(session, fixture.run(), first);
            try (var input =
                            Files.newInputStream(
                                    Path.of(
                                            "docs/catalogos/macrobloco-analitico/contratos-colunas-inicial.json"));
                    var connection = session.getConnection();
                    var sql = connection.prepareStatement(query())) {
                sql.setString(1, fixture.run().toString());
                sql.setQueryTimeout(10);
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    for (final var contract :
                            new ObjectMapper().readTree(input).path("contracts")) {
                        if (!contract.path("id").asText().equals("SQL-05")) {
                            continue;
                        }
                        int ordinal = 0;
                        for (final var column : contract.path("columns")) {
                            assertEquals(
                                    column.path("name").asText(),
                                    row.getMetaData().getColumnLabel(++ordinal));
                            assertNotNull(row.getObject(ordinal), column.path("name").asText());
                        }
                        assertEquals(54, ordinal);
                    }
                    assertEquals(710001, row.getLong("N° Cotação"));
                    assertEquals("Convertida", row.getString("Status Conversão"));
                    assertEquals("Emitido", row.getString("Status_Sistema_CTe"));
                    assertEquals("Emitida", row.getString("Status_Sistema_NFSe"));
                    assertEquals("Sim", row.getString("Refino_CTe"));
                    assertEquals("10:00:00.1234567", row.getString("Hora (Solicitacao)"));
                    assertEquals("CAMPINAS - SP x RIO DE JANEIRO - RJ", row.getString("Trecho"));
                    assertEquals(new BigDecimal("120.00000000"), row.getBigDecimal("Valor frete"));
                    assertEquals(new BigDecimal("1.21000000"), row.getBigDecimal("Min. Frete/KG"));
                    assertEquals(28, row.getMetaData().getPrecision(row.findColumn("Peso real")));
                    assertEquals(8, row.getMetaData().getScale(row.findColumn("Peso real")));
                    final var metadata = new ObjectMapper().readTree(row.getString("Metadata"));
                    assertEquals(
                            "2036-04-01T10:00:00.123456789-03:00",
                            metadata.path("requested_at").path("raw").asText());
                    assertTrue(!row.next());
                }
            }
            final var replay =
                    capture(
                            session,
                            fixture.run(),
                            UUID.randomUUID(),
                            first,
                            reference.release(),
                            AnalyticQuotesFixtures.data());
            assertEquals(new JdbcAnalyticQuotePromotion.Receipt(3, 1, 0, 0, 1, 0, 0), replay);
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_quote_snapshot WHERE run_id=?",
                            fixture.run()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_05 WHERE run_id=? AND reference_revision=1",
                            fixture.run()));
        }
    }

    @Test
    void correctionPreservesAbsentClearsNullAndStaleObservationCannotRegressEffectiveValues()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            final long release =
                    new JdbcAnalyticQuoteTariffs(session)
                            .importPackaged(fixture.run(), 1, DATE, DATE.plusDays(3))
                            .release();
            final UUID first = UUID.randomUUID();
            capture(session, fixture.run(), first, null, release, AnalyticQuotesFixtures.data());
            bind(session, fixture.run(), first);
            final var correction = AnalyticQuotesFixtures.data();
            correction.put("qoe_qes_fit_nse_issued_at", "2036-04-01T13:00:00-03:00");
            correction.remove("qoe_cor_name");
            correction.putNull("qoe_qes_freight_comments");
            correction.put("qoe_qes_other_fees", "0");
            assertEquals(
                    1,
                    capture(session, fixture.run(), UUID.randomUUID(), null, release, correction)
                            .updates());
            assertEquals(
                    1,
                    capture(
                                    session,
                                    fixture.run(),
                                    UUID.randomUUID(),
                                    null,
                                    release,
                                    AnalyticQuotesFixtures.data())
                            .stale());
            try (var connection = session.getConnection();
                    var sql = connection.prepareStatement(query())) {
                sql.setString(1, fixture.run().toString());
                sql.setQueryTimeout(10);
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals("SYNTHETIC customerName", row.getString("Cliente Pagador"));
                    assertNull(row.getString("Observações para o frete"));
                    assertEquals(
                            new BigDecimal("0.00000000"),
                            row.getBigDecimal("Trechos/Outros valores"));
                    final var metadata = new ObjectMapper().readTree(row.getString("Metadata"));
                    assertEquals("ABSENT", metadata.path("qoe_cor_name").path("presence").asText());
                }
            }
            final var conflict = correction.deepCopy().put("qoe_qes_other_fees", "1");
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    capture(
                                            session,
                                            fixture.run(),
                                            UUID.randomUUID(),
                                            null,
                                            release,
                                            conflict));
            assertTrue(cause(failure, 53715));
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_quote_snapshot WHERE run_id=?",
                            fixture.run()));
        }
    }

    @Test
    void missingRouteAndForeignRunRefuseBeforePromotionWhileIndependentStateSurvives()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            final var tariff =
                    new JdbcAnalyticQuoteTariffs(session)
                            .importPackaged(fixture.run(), 1, DATE, DATE.plusDays(3));
            final var missing = AnalyticQuotesFixtures.data().put("qoe_qes_ony_sae_code", "AC");
            final var refusal =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    capture(
                                            session,
                                            fixture.run(),
                                            UUID.randomUUID(),
                                            null,
                                            tariff.release(),
                                            missing));
            assertTrue(cause(refusal, 53716));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.analytic_lab_run WHERE run_id=?",
                            fixture.run()));
            final UUID first = UUID.randomUUID();
            capture(
                    session,
                    fixture.run(),
                    first,
                    null,
                    tariff.release(),
                    AnalyticQuotesFixtures.data());
            bind(session, fixture.run(), first);
            final var other = AnalyticLaboratoryDimensionsIT.start(session);
            final var otherTariff =
                    new JdbcAnalyticQuoteTariffs(session)
                            .importPackaged(other.run(), 1, DATE, DATE.plusDays(3));
            final var foreign =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    capture(
                                            session,
                                            other.run(),
                                            UUID.randomUUID(),
                                            null,
                                            otherTariff.release(),
                                            AnalyticQuotesFixtures.data()));
            assertTrue(cause(foreign, 53714));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_05 WHERE run_id=? AND reference_revision=1",
                            fixture.run()));
        }
    }

    @Test
    void statusPrecedenceEmissionNullsAndMissingBranchAreConsumedWithoutInventedDefaults()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            final long release =
                    new JdbcAnalyticQuoteTariffs(session)
                            .importPackaged(fixture.run(), 1, DATE, DATE.plusDays(3))
                            .release();
            final UUID first = UUID.randomUUID();
            capture(session, fixture.run(), first, null, release, AnalyticQuotesFixtures.data());
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_05 WHERE run_id=?",
                            fixture.run()));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_quote_projection WHERE run_id=? AND dispos"
                                    + "ition='BRANCH_UNRESOLVED'",
                            fixture.run()));
            bind(session, fixture.run(), first);
            final var rejected =
                    AnalyticQuotesFixtures.data()
                            .put("requested_at", "2036-04-01T13:00:00-03:00")
                            .putNull("qoe_qes_fit_nse_issued_at")
                            .putNull("qoe_qes_fit_fhe_cte_issued_at")
                            .put("qoe_uer_name", "  José Sintético  ")
                            .put("qoe_qes_disapprove_comments", "recusa sintética");
            capture(session, fixture.run(), UUID.randomUUID(), null, release, rejected);
            try (var connection = session.getConnection();
                    var sql = connection.prepareStatement(query())) {
                sql.setString(1, fixture.run().toString());
                sql.setQueryTimeout(10);
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals("Reprovada", row.getString("Status Conversão"));
                    assertEquals("Pendente", row.getString("Status_Sistema_CTe"));
                    assertEquals("Pendente", row.getString("Status_Sistema_NFSe"));
                    assertEquals("Não", row.getString("Refino_CTe"));
                    assertEquals("José Sintético", row.getString("Usuario Key"));
                    assertNull(row.getObject("CT-e/Data de emissão"));
                    assertNull(row.getObject("Nfse/Data de emissão"));
                }
            }
            final var pending =
                    rejected.deepCopy()
                            .put("requested_at", "2036-04-01T14:00:00-03:00")
                            .put("qoe_qes_disapprove_comments", "");
            capture(session, fixture.run(), UUID.randomUUID(), null, release, pending);
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_05 WHERE run_id=? AND [Status Conversão"
                                    + "]=N'Pendente'",
                            fixture.run()));
        }
    }

    @Test
    void directionalTariffRetryIsImmutableAndExpiredOrForeignReleaseDoesNotPublish()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            final var tariffs = new JdbcAnalyticQuoteTariffs(session);
            final var imported = tariffs.importPackaged(fixture.run(), 1, DATE, DATE.plusDays(1));
            try (var input =
                    getClass()
                            .getResourceAsStream(
                                    "/analytic-laboratory/quote-tariffs.synthetic.json")) {
                assertNotNull(input);
                final var changed = new ObjectMapper().readTree(input);
                ((ObjectNode) changed.path("rows").get(0)).put("amount", "9.99");
                final var failure =
                        assertThrows(
                                SQLException.class,
                                () ->
                                        tariffs.importFixture(
                                                fixture.run(),
                                                1,
                                                DATE,
                                                DATE.plusDays(1),
                                                new ObjectMapper().writeValueAsBytes(changed)));
                assertTrue(cause(failure, 53705));
            }
            final var pr = AnalyticQuotesFixtures.data().put("qoe_qes_ony_sae_code", "PR");
            final UUID first = UUID.randomUUID();
            capture(session, fixture.run(), first, null, imported.release(), pr);
            bind(session, fixture.run(), first);
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_05 WHERE run_id=? AND [Min. Frete/KG]=0.88",
                            fixture.run()));
            final var expired =
                    pr.deepCopy().put("qoe_qes_fit_nse_issued_at", "2036-04-02T13:00:00-03:00");
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    capture(
                                            session,
                                            fixture.run(),
                                            UUID.randomUUID(),
                                            null,
                                            imported.release(),
                                            expired));
            assertTrue(cause(failure, 53716));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_05 WHERE run_id=? AND [Min. Frete/KG]=0.88",
                            fixture.run()));
        }
    }

    static JdbcAnalyticQuotePromotion.Receipt capture(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID execution,
            final UUID replay,
            final long release,
            final ObjectNode seed)
            throws Exception {
        return new LocalAnalyticQuotesRuntime(session, Clock.systemUTC())
                .capture(
                        run,
                        execution,
                        DATE,
                        replay == null ? ExecutionMode.BACKFILL : ExecutionMode.REPLAY,
                        replay,
                        1,
                        release,
                        2,
                        new AnalyticQuotesSyntheticSource(
                                page -> {
                                    final var rows = JsonNodeFactory.instance.arrayNode();
                                    for (int i = (page - 1) * 2; i < Math.min(3, page * 2); i++) {
                                        rows.add(seed.deepCopy().put("sequence_code", 710001));
                                    }
                                    return rows.toString();
                                }),
                        CancellationToken.none());
    }

    static void bind(
            final ColetaTemporalLaboratorySession session, final UUID run, final UUID execution)
            throws SQLException {
        new JdbcAnalyticDimensions(session)
                .bindBatch(
                        run,
                        List.of(
                                binding(
                                        Entity.COT,
                                        "INTEGER:710001",
                                        execution,
                                        Role.BRANCH,
                                        "synthetic-branch-a",
                                        1,
                                        null)),
                        CancellationToken.none());
    }

    private static boolean cause(final Throwable failure, final int code) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && sql.getErrorCode() == code) {
                return true;
            }
        }
        return false;
    }

    private static String query() {
        return "SELECT TOP(2) * FROM pub.analytic_lab_sql_05 WHERE run_id=? AND reference_revision=1 "
                + "ORDER BY source_key";
    }
}
