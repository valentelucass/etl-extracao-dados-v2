package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFleetReferences;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryFleetReferencesIT {
    private static final LocalDate DATE = AnalyticLaboratoryRasterIT.DATE;

    @Test
    void sealedReferencesConsumeEightContractCombinationsAndPreserveExactReplay() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var run = start(session);
            final var importer =
                    new JdbcAnalyticFleetReferences(session, AnalyticLaboratoryRasterIT.CLOCK);
            final var receipt = importer.importPackaged(run, 1, DATE, DATE.plusDays(3));
            assertEquals(25, receipt.rows());
            assertEquals(receipt, importer.importPackaged(run, 1, DATE, DATE.plusDays(3)));
            final String[][] cases = {
                {"AGGREGATE", null, "THIRD_PARTY"},
                {"DRIVER", null, "FLEET_PLUS_PX"},
                {"AGGREGATE", "AGGREGATE", "AGGREGATE"},
                {"AGGREGATE", "THIRD_PARTY", "THIRD_PARTY"},
                {"AGGREGATE", "COMPANY", "FLEET"},
                {"DRIVER", "AGGREGATE", "FLEET_PLUS_PX"},
                {"DRIVER", "THIRD_PARTY", "FLEET_PLUS_PX"},
                {"DRIVER", "COMPANY", "FLEET"}
            };
            for (final var item : cases) {
                assertClassification(
                        session,
                        run,
                        DATE,
                        "12.345.678/0001-95",
                        null,
                        item[0],
                        item[1],
                        "FLEET",
                        item[2],
                        "RESOLVED",
                        "DOCUMENT_REFERENCE");
            }
            assertEquals(
                    102,
                    new JdbcAnalyticReferences(session, AnalyticLaboratoryRasterIT.CLOCK)
                            .importManifestPackaged(
                                    run,
                                    1,
                                    DATE,
                                    DATE.plusDays(3),
                                    AnalyticLaboratoryReferencesIT.POLICIES)
                            .rows());
        }
    }

    @Test
    void explicitExceptionsAndUnknownOrExpiredInputsHaveIndependentDispositions() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var run = start(session);
            new JdbcAnalyticFleetReferences(session, AnalyticLaboratoryRasterIT.CLOCK)
                    .importPackaged(run, 1, DATE, DATE.plusDays(3));
            assertClassification(
                    session,
                    run,
                    DATE,
                    null,
                    " synthetic owned owner ",
                    "AGGREGATE",
                    "COMPANY",
                    "FLEET",
                    "FLEET",
                    "RESOLVED",
                    "OWNER_NAME_REFERENCE");
            assertClassification(
                    session,
                    run,
                    DATE,
                    null,
                    "SYNTHETIC PX OWNER",
                    " aggregate ",
                    null,
                    "AGGREGATE",
                    "FLEET_PLUS_PX",
                    "RESOLVED",
                    "CONTRACT_REFERENCE");
            assertClassification(
                    session,
                    run,
                    DATE,
                    null,
                    null,
                    "AGGREGATE",
                    "NEW_UNMAPPED",
                    "AGGREGATE",
                    null,
                    "CONTRACT_UNRESOLVED",
                    "CONTRACT_REFERENCE");
            assertClassification(
                    session,
                    run,
                    DATE,
                    null,
                    null,
                    "DRIVER",
                    "COMPANY",
                    null,
                    "FLEET",
                    "OWNERSHIP_UNRESOLVED",
                    "UNRESOLVED");
            assertClassification(
                    session,
                    run,
                    DATE.plusDays(3),
                    "12345678000195",
                    null,
                    "AGGREGATE",
                    "COMPANY",
                    null,
                    null,
                    "FLEET_REFERENCE_MISSING",
                    "UNRESOLVED");
            assertClassification(
                    session,
                    UUID.randomUUID(),
                    DATE,
                    "12345678000195",
                    null,
                    "AGGREGATE",
                    "COMPANY",
                    null,
                    null,
                    "FLEET_REFERENCE_MISSING",
                    "UNRESOLVED");
        }
    }

    @Test
    void physicalSealRejectsMutationAndDivergentReplayCannotReplaceExistingRelease()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var run = start(session);
            final var receipt =
                    new JdbcAnalyticFleetReferences(session, AnalyticLaboratoryRasterIT.CLOCK)
                            .importPackaged(run, 1, DATE, DATE.plusDays(3));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "UPDATE ref.classificacao_frota_matriz SET classification_code=N'THIRD_PARTY' WHERE re"
                                            + "ference_release_id=?")) {
                sql.setQueryTimeout(10);
                sql.setLong(1, receipt.release());
                assertThrows(SQLException.class, sql::executeUpdate);
            }
        }
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var run = start(session);
            final var importer =
                    new JdbcAnalyticFleetReferences(session, AnalyticLaboratoryRasterIT.CLOCK);
            importer.importPackaged(run, 1, DATE, DATE.plusDays(3));
            try (var input =
                    getClass()
                            .getResourceAsStream(
                                    "/analytic-laboratory/fleet-references.synthetic.json")) {
                final var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(input);
                ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("matrix").get(0))
                        .put("classification", "AGGREGATE");
                final var bytes =
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(tree);
                assertThrows(
                        SQLException.class,
                        () -> importer.importFixture(run, 1, DATE, DATE.plusDays(3), bytes));
            }
        }
    }

    private static UUID start(final ColetaTemporalLaboratorySession session) throws SQLException {
        final var run = UUID.randomUUID();
        AnalyticLaboratoryRasterIT.start(session, run);
        return run;
    }

    private static void assertClassification(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final LocalDate date,
            final String document,
            final String owner,
            final String vehicle,
            final String driver,
            final String ownership,
            final String contract,
            final String disposition,
            final String provenance)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT ownership_code,contract_code,disposition,ownership_provenance,reference_release_id"
                                        + " FROM ref.ufn_analytic_fleet(?,1,?,?,?,?,?)")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setObject(2, date);
            sql.setString(3, document);
            sql.setString(4, owner);
            sql.setString(5, vehicle);
            sql.setString(6, driver);
            try (var row = sql.executeQuery()) {
                assertTrue(row.next());
                assertEquals(ownership, row.getString(1));
                assertEquals(contract, row.getString(2));
                assertEquals(disposition, row.getString(3));
                assertEquals(provenance, row.getString(4));
                if ("FLEET_REFERENCE_MISSING".equals(disposition)) {
                    assertNull(row.getObject(5));
                } else {
                    assertTrue(row.getLong(5) > 0);
                }
                assertFalse(row.next());
            }
        }
    }
}
