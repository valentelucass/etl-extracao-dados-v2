package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticMaterializationRequest;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFleetReferences;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryManifestGatesIT {
    private static final LocalDate DATE = AnalyticLaboratoryRasterIT.DATE;
    private static final Clock CLOCK = AnalyticLaboratoryRasterIT.CLOCK;
    private static final CancellationToken NONE = CancellationToken.none();

    @Test
    void zeroCapacityIsAValueAndNullCapacityIsAnUnresolvedDependency() throws Exception {
        for (final boolean missing : List.of(false, true)) {
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                final var setup = AnalyticLaboratoryManifestsIT.setup(session, false, true);
                final var fixture = referenceFixture();
                for (final var record : fixture.path("registry")) {
                    if (record.path("kind").asText().equals("VEICULO")) {
                        final var row = (ObjectNode) record;
                        row.put("capacity", "0.00000000");
                        if (missing && row.path("key").asText().equals("synthetic-tractor-a")) {
                            row.putNull("capacity");
                            row.putNull("unit");
                        }
                    }
                }
                references(session, setup.fixture().run(), fixture);
                final var request =
                        new AnalyticMaterializationRequest(
                                setup.fixture().run(),
                                UUID.randomUUID(),
                                3,
                                ExecutionMode.BACKFILL,
                                true,
                                DATE,
                                DATE.plusDays(3));
                final var receipt =
                        new JdbcAnalyticMaterializations(session, CLOCK).manifests(request, NONE);
                assertEquals(missing ? 0 : 1, receipt.ready());
                assertEquals(
                        missing ? "CAPACITY_UNRESOLVED" : "READY",
                        disposition(session, setup.fixture().run()));
                if (!missing) {
                    assertCapacity(
                            session,
                            setup.fixture().run(),
                            "0.00000000",
                            "0.00000000",
                            "0.00000000");
                }
            }
        }
    }

    @Test
    void swappingTrailerRolesKeepsTheSumAndOverlappingCanonicalVehicleIsBlocked() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var setup = AnalyticLaboratoryManifestsIT.setup(session, false, true);
            final var fixture = setup.fixture();
            final var repository = new JdbcAnalyticMaterializations(session, CLOCK);
            repository.manifests(AnalyticLaboratoryManifestsIT.request(fixture.run()), NONE);
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    AnalyticLaboratoryManifestsIT.binding(
                                            setup.execution(),
                                            Role.TRAILER1,
                                            "synthetic-trailer-b",
                                            2,
                                            "synthetic-trailer-a"),
                                    AnalyticLaboratoryManifestsIT.binding(
                                            setup.execution(),
                                            Role.TRAILER2,
                                            "synthetic-trailer-a",
                                            2,
                                            "synthetic-trailer-b")),
                            NONE);
            assertEquals(
                    1,
                    repository
                            .manifests(AnalyticLaboratoryManifestsIT.request(fixture.run()), NONE)
                            .updates());
            assertCapacity(
                    session, fixture.run(), "22000.00000000", "7000.00000000", "5000.00000000");
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    AnalyticLaboratoryManifestsIT.binding(
                                            setup.execution(),
                                            Role.TRAILER2,
                                            "synthetic-trailer-b",
                                            3,
                                            "synthetic-trailer-a")),
                            NONE);
            assertEquals(
                    1,
                    repository
                            .manifests(AnalyticLaboratoryManifestsIT.request(fixture.run()), NONE)
                            .blocked());
            assertEquals("FLEET_ROLE_CONFLICT", disposition(session, fixture.run()));
            final var emptyTrailers = setup.row().deepCopy();
            emptyTrailers.putNull("mft_tl1_license_plate");
            emptyTrailers.putNull("mft_tl2_license_plate");
            emptyTrailers.putNull("mft_tl1_weight_capacity");
            emptyTrailers.putNull("mft_tl2_weight_capacity");
            emptyTrailers.put("finished_at", "2036-04-02T12:00:00Z");
            final var current =
                    AnalyticLaboratoryManifestsIT.capture(session, fixture, emptyTrailers);
            new JdbcAnalyticDimensions(session)
                    .bindBatch(
                            fixture.run(),
                            List.of(
                                    inactive(current, Role.TRAILER1, 3),
                                    inactive(current, Role.TRAILER2, 4)),
                            NONE);
            assertEquals(
                    1,
                    repository
                            .manifests(AnalyticLaboratoryManifestsIT.request(fixture.run()), NONE)
                            .ready());
            assertCapacity(session, fixture.run(), "10000.00000000", null, null);
        }
    }

    @Test
    void excludedPlateAndMissingFleetReleaseCannotPublishEvenWithValidFreight() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var setup = AnalyticLaboratoryManifestsIT.setup(session, false, true);
            final var corrected = setup.row().deepCopy();
            corrected.put("mft_vie_license_plate", "ACM0000");
            corrected.put("finished_at", "2036-04-02T12:00:00Z");
            AnalyticLaboratoryManifestsIT.capture(session, setup.fixture(), corrected);
            assertEquals(
                    1,
                    new JdbcAnalyticMaterializations(session, CLOCK)
                            .manifests(
                                    AnalyticLaboratoryManifestsIT.request(setup.fixture().run()),
                                    NONE)
                            .blocked());
            assertEquals("EXCLUDED_VEHICLE", disposition(session, setup.fixture().run()));
        }
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var setup = AnalyticLaboratoryManifestsIT.setup(session, false, true);
            new JdbcAnalyticReferences(session, CLOCK)
                    .importManifestPackaged(
                            setup.fixture().run(),
                            3,
                            DATE,
                            DATE.plusDays(3),
                            AnalyticLaboratoryReferencesIT.POLICIES);
            assertEquals(
                    1,
                    new JdbcAnalyticMaterializations(session, CLOCK)
                            .manifests(
                                    new AnalyticMaterializationRequest(
                                            setup.fixture().run(),
                                            UUID.randomUUID(),
                                            3,
                                            ExecutionMode.BACKFILL,
                                            true,
                                            DATE,
                                            DATE.plusDays(3)),
                                    NONE)
                            .blocked());
            assertEquals("FLEET_REFERENCE_MISSING", disposition(session, setup.fixture().run()));
        }
    }

    @Test
    void aWindowSliceCannotClaimFullScopeAndMissingManifestCaptureCannotSeal() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var setup = AnalyticLaboratoryManifestsIT.setup(session, false, true);
            final var request =
                    new AnalyticMaterializationRequest(
                            setup.fixture().run(),
                            UUID.randomUUID(),
                            2,
                            ExecutionMode.BACKFILL,
                            true,
                            DATE.plusDays(1),
                            DATE.plusDays(3));
            assertEquals(
                    53661,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            new JdbcAnalyticMaterializations(session, CLOCK)
                                                    .manifests(request, NONE))
                            .getErrorCode());
        }
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            assertEquals(
                    53662,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            new JdbcAnalyticMaterializations(session, CLOCK)
                                                    .manifests(
                                                            AnalyticLaboratoryManifestsIT.request(
                                                                    fixture.run()),
                                                            NONE))
                            .getErrorCode());
        }
    }

    private static AnalyticDimensionBinding inactive(
            final UUID execution, final Role role, final int revision) {
        return new AnalyticDimensionBinding(
                Entity.MAN,
                "INTEGER:1",
                execution,
                role,
                "synthetic-trailer-b",
                revision,
                DATE,
                DATE.plusDays(3),
                false,
                null,
                "synthetic-mat05-inactive-role");
    }

    private static ObjectNode referenceFixture() throws Exception {
        try (var input =
                AnalyticLaboratoryManifestGatesIT.class.getResourceAsStream(
                        "/analytic-laboratory/manifest-references.synthetic.json")) {
            return (ObjectNode) new ObjectMapper().readTree(input);
        }
    }

    private static void references(
            final ColetaTemporalLaboratorySession session, final UUID run, final ObjectNode fixture)
            throws Exception {
        new JdbcAnalyticReferences(session, CLOCK)
                .importFixture(
                        run,
                        3,
                        DATE,
                        DATE.plusDays(3),
                        AnalyticLaboratoryReferencesIT.POLICIES,
                        new ObjectMapper().writeValueAsBytes(fixture));
        new JdbcAnalyticFleetReferences(session, CLOCK)
                .importPackaged(run, 3, DATE, DATE.plusDays(3));
    }

    private static String disposition(final ColetaTemporalLaboratorySession session, final UUID run)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT o.disposition FROM mart.analytic_manifest_current c JOIN mart.analytic_manifes"
                                        + "t_observation o ON o.observation_id=c.observation_id WHERE c.run_id=?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            try (var row = sql.executeQuery()) {
                assertTrue(row.next());
                final var result = row.getString(1);
                assertFalse(row.next());
                return result;
            }
        }
    }

    private static void assertCapacity(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final String total,
            final String first,
            final String second)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT total_capacity,trailer1_capacity,trailer2_capacity FROM pub.analytic_lab_manif"
                                        + "ests WHERE run_id=?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            try (var row = sql.executeQuery()) {
                assertTrue(row.next());
                assertEquals(new BigDecimal(total), row.getBigDecimal(1));
                assertEquals(first == null ? null : new BigDecimal(first), row.getBigDecimal(2));
                assertEquals(second == null ? null : new BigDecimal(second), row.getBigDecimal(3));
                assertFalse(row.next());
            }
        }
    }
}
