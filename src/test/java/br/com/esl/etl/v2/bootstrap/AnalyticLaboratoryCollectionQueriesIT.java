package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.fonte.graphql.AnalyticCollectionSupplementMapper;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionRegions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSupplements;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSupplements.Binding;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryCollectionQueriesIT {
    @Test
    void allFortyOneColumnsAreFedByRealCaptureSupplementAndCanonicalManifestRelation()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            final var capture = prepare(session, f);
            final var manifest =
                    AnalyticLaboratoryManifestCaptureIT.manifest()
                            .put("mft_pfs_pck_sequence_code", 100001);
            final var execution = AnalyticLaboratoryManifestsIT.capture(session, f, manifest);
            AnalyticLaboratoryCollectorsIT.activate(session, f.run(), execution, 1, true, false);
            final var relations = new JdbcRelationalLaboratory(session, CLOCK);
            relations.bindBatch(
                    f.relational(),
                    List.of(RelationalLaboratoryFixtures.bindingBatch(DATE, 1, 1).get(0)),
                    CancellationToken.none());
            assertEquals(
                    1,
                    relations
                            .resolve(f.relational(), UUID.randomUUID(), CancellationToken.none())
                            .resolved());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM pub.analytic_lab_sql_03 WHERE run_id=? AND reference_revision=1")) {
                sql.setQueryTimeout(20);
                sql.setString(1, f.run().toString());
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    for (int column = 1; column <= 41; column++) {
                        assertNotNull(
                                row.getObject(column), row.getMetaData().getColumnLabel(column));
                    }
                    assertEquals(200001, row.getLong("ID"));
                    assertEquals(100001, row.getLong("Coleta"));
                    assertEquals(1, row.getLong("Numero Manifesto"));
                    assertEquals("REGIAO_CEP_SINTETICA", row.getString("Região Logística"));
                    assertEquals("CAMPINAS - SP", row.getString("Região da Coleta"));
                    assertEquals("CLIENTE SINTÉTICO", row.getString("Cliente"));
                    assertEquals("7", row.getString("Usuario Cancel. Nome"));
                    assertFalse(row.getBoolean("Excluída na Origem"));
                    final var metadata = new ObjectMapper().readTree(row.getString("Metadata"));
                    assertEquals(31, metadata.path("dataExport").size());
                    assertEquals(12, metadata.path("graphqlLateral").size());
                    assertEquals(3, metadata.path("lineage").size());
                    assertEquals(
                            "VALUE",
                            metadata.path("graphqlLateral")
                                    .path("customer_name")
                                    .path("presence")
                                    .asText());
                    assertFalse(row.next());
                }
            }
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_04 WHERE run_id=?",
                            f.run()));
            assertNotNull(capture.source().executionId());
        }
    }

    @Test
    void regionUsesCepThenCityThenTextAndMissingReleaseBlocksReadiness() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            final var first =
                    AnalyticLaboratoryCollectionsIT.capture(
                            session, f, AnalyticCollectionsFixtures.data(), null);
            bindBranch(session, f, first.source().executionId());
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_03 WHERE run_id=?",
                            f.run()));
            final var regions = new JdbcAnalyticCollectionRegions(session);
            final var receipt = regions.importPackaged(f.run(), 1, DATE, DATE.plusDays(3));
            assertEquals(2, receipt.rows());
            assertEquals(receipt, regions.importPackaged(f.run(), 1, DATE, DATE.plusDays(3)));
            assertRegion(session, f, "REGIAO_CEP_SINTETICA");
            final var city =
                    AnalyticCollectionsFixtures.data()
                            .put("pck_pds_postal_code", "00000000")
                            .put("status_updated_at", "2036-04-01T10:00:01.123456789Z");
            AnalyticLaboratoryCollectionsIT.capture(session, f, city, null);
            assertRegion(session, f, "REGIAO_CIDADE_SINTETICA");
            city.put("pck_pds_cty_name", "CIDADE SINTÉTICA")
                    .put("status_updated_at", "2036-04-01T10:00:02.123456789Z");
            AnalyticLaboratoryCollectionsIT.capture(session, f, city, null);
            assertRegion(session, f, "CIDADE SINTÉTICA - SP");
        }
    }

    @Test
    void lateralAbsentPreservesWhileExplicitNullClearsWithoutRewritingObservedFields()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            final var first = prepare(session, f);
            final var envelope =
                    new ObjectMapper()
                            .createObjectNode()
                            .put("version", "synthetic-analytic-collection-supplement-v1")
                            .put("provenance", "FIXTURE_SINTETICA_EXPLICITA");
            envelope.putObject("data").putNull("customer");
            new JdbcAnalyticCollectionSupplements(session)
                    .bind(
                            f.run(),
                            List.of(
                                    new Binding(
                                            "INTEGER:200001",
                                            first.source().executionId(),
                                            2,
                                            new AnalyticCollectionSupplementMapper().map(envelope),
                                            null,
                                            null)),
                            CancellationToken.none());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_03 WHERE run_id=? AND [Cliente] IS NULL"
                                    + " AND [Local da Coleta]=N'ENDEREÇO SINTÉTICO'",
                            f.run()));
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM stg.analytic_collection_supplement WHERE run_id=?",
                            f.run()));
        }
    }

    static LocalAnalyticCollectionRuntime.Capture prepare(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture f)
            throws Exception {
        final var capture =
                AnalyticLaboratoryCollectionsIT.capture(
                        session, f, AnalyticCollectionsFixtures.data(), null);
        new JdbcAnalyticCollectionRegions(session)
                .importPackaged(f.run(), 1, DATE, DATE.plusDays(3));
        bindBranch(session, f, capture.source().executionId());
        new JdbcAnalyticCollectionSupplements(session)
                .bind(
                        f.run(),
                        List.of(
                                new Binding(
                                        "INTEGER:200001",
                                        capture.source().executionId(),
                                        1,
                                        AnalyticCollectionsFixtures.supplement(),
                                        null,
                                        null)),
                        CancellationToken.none());
        return capture;
    }

    private static void bindBranch(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture f,
            final UUID execution)
            throws Exception {
        new JdbcAnalyticDimensions(session)
                .bindBatch(
                        f.run(),
                        List.of(
                                AnalyticLaboratoryCollectorsIT.binding(
                                        Entity.COL,
                                        "INTEGER:200001",
                                        execution,
                                        Role.BRANCH,
                                        "synthetic-branch-a",
                                        1,
                                        null)),
                        CancellationToken.none());
    }

    private static void assertRegion(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture f,
            final String expected)
            throws Exception {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT [Região Logística] FROM pub.analytic_lab_sql_03 WHERE run_id=? AND reference_r"
                                        + "evision=1")) {
            sql.setQueryTimeout(20);
            sql.setString(1, f.run().toString());
            try (var row = sql.executeQuery()) {
                assertTrue(row.next());
                assertEquals(expected, row.getString(1));
                assertFalse(row.next());
            }
        }
    }
}
