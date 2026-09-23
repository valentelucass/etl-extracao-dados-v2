package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticManifestPreparation;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryManifestPreparationIT {
    @Test
    void allNinetyTwoFieldsAreTypedAuditedAndConsumedAfterTheRealCapture() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            final var row = AnalyticLaboratoryManifestCaptureIT.manifest();
            final var result =
                    runtime(session, fixture)
                            .capture(
                                    DATE,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    page -> page == 1 ? "[" + row + "," + row + "]" : "[]");
            assertEquals(
                    new JdbcAnalyticManifestPreparation.Receipt(2, 1, 0), result.preparation());
            assertEquals(
                    result.preparation(),
                    new JdbcAnalyticManifestPreparation(session, CLOCK)
                            .prepare(
                                    fixture.run(),
                                    result.source().executionId(),
                                    CancellationToken.none()));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM core.analytic_lab_manifest_projection WHERE run_id=?")) {
                sql.setString(1, fixture.run().toString());
                sql.setQueryTimeout(10);
                try (var rows = sql.executeQuery();
                        var input =
                                getClass()
                                        .getResourceAsStream(
                                                "/analytic-laboratory/manifest-fields.synthetic.json")) {
                    assertTrue(rows.next());
                    final var fields = new ObjectMapper().readTree(input).path("fields");
                    assertEquals(92, fields.size());
                    for (final var field : fields) {
                        final String name = field.path("name").asText();
                        assertNotNull(rows.getObject(name), name);
                        final int column = rows.findColumn(name);
                        assertEquals(name, rows.getMetaData().getColumnName(column));
                        if (rows.getMetaData().getColumnType(column) == java.sql.Types.DECIMAL) {
                            assertEquals(28, rows.getMetaData().getPrecision(column));
                            assertEquals(8, rows.getMetaData().getScale(column));
                            assertEquals(new BigDecimal("100.12500000"), rows.getBigDecimal(name));
                        }
                    }
                    assertEquals("READY", rows.getString("disposition"));
                    assertEquals(2, rows.getLong("source_rows"));
                    assertTrue(rows.getString("created_at").contains("12:00:00.1234567"));
                    assertFalse(rows.next());
                }
            }
            assertEquals(
                    184,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM stg.analytic_manifest_field_audit a JOIN stg.manifesto_obser"
                                    + "vation o ON o.manifesto_observation_id=a.source_observation_id JOIN ctl.analytic_mani"
                                    + "fest_preparation p ON p.execution_id=o.execution_id WHERE p.run_id=?"));
            assertEquals(
                    2,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM stg.analytic_manifest_field_audit a JOIN stg.manifesto_obser"
                                    + "vation o ON o.manifesto_observation_id=a.source_observation_id JOIN ctl.analytic_mani"
                                    + "fest_preparation p ON p.execution_id=o.execution_id WHERE p.run_id=? AND a.field_name"
                                    + "='created_at' AND a.raw_value='2036-04-01T12:00:00.123456789Z' AND a.presence='VALUE'"
                                    + " AND a.wire_type=1"));
        }
    }

    @Test
    void sameClockAcrossCapturesConflictsAndLaterCohortResolvesWithoutMixingVersions()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            final var runtime = runtime(session, fixture);
            final var first = AnalyticLaboratoryManifestCaptureIT.manifest();
            runtime.capture(DATE, ExecutionMode.BOOTSTRAP, null, source(first));
            final var second = first.deepCopy().put("invoices_count", 2);
            final var conflict =
                    runtime.capture(DATE, ExecutionMode.BACKFILL, null, source(second));
            assertEquals(1, conflict.preparation().blockedRoots());
            assertEquals(
                    1,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_manifest_projection WHERE run_id=? AND dis"
                                    + "position='ATTRIBUTE_CONFLICT' AND source_rows=2"));
            final var third =
                    second.deepCopy()
                            .put("finished_at", "2036-04-01T13:00:00Z")
                            .putNull("invoices_count");
            final var corrected =
                    runtime.capture(DATE, ExecutionMode.BACKFILL, null, source(third));
            assertEquals(0, corrected.preparation().blockedRoots());
            assertEquals(
                    1,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_manifest_projection WHERE run_id=? AND dis"
                                    + "position='READY' AND source_rows=1 AND invoices_count IS NULL"));
            final var replay =
                    runtime.capture(
                            DATE,
                            ExecutionMode.REPLAY,
                            corrected.source().executionId(),
                            source(third));
            assertEquals(0, replay.preparation().blockedRoots());
            assertEquals(
                    4,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM core.analytic_manifest_snapshot WHERE run_id=?"));
        }
    }

    @Test
    void metricsComplementNullChildrenRemainDistinctAndScalarCardinalityIsExplicit()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            final var first = AnalyticLaboratoryManifestCaptureIT.manifest();
            final var second =
                    first.deepCopy()
                            .putNull("total_cost")
                            .put("mft_mfs_number", 2)
                            .put("mft_mfs_key", "2".repeat(44))
                            .put("mft_pfs_pck_sequence_code", 2);
            final var result =
                    runtime(session, fixture)
                            .capture(
                                    DATE,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    page ->
                                            page == 1
                                                    ? "[" + first + "]"
                                                    : page == 2 ? "[" + second + "]" : "[]");
            assertEquals(0, result.preparation().blockedRoots());
            assertEquals(
                    1,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_manifest_projection WHERE run_id=? AND tot"
                                    + "al_cost=100.125 AND mft_mfs_number IS NULL AND mft_mfs_key IS NULL AND mft_pfs_pck_se"
                                    + "quence_code IS NULL AND source_rows=2"));
        }
    }

    @Test
    void invalidAdditionalDecimalAndArrayAreAuditedAndMissingAssociationRollsBackCapture()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            final var row =
                    AnalyticLaboratoryManifestCaptureIT.manifest()
                            .put("invoices_value", "1.000000001");
            row.withArray("mft_mte_unloading_recipient_names").removeAll();
            for (int item = 0; item < 33; item++) {
                row.withArray("mft_mte_unloading_recipient_names").add("SYNTHETIC " + item);
            }
            final var result =
                    runtime(session, fixture)
                            .capture(DATE, ExecutionMode.BOOTSTRAP, null, source(row));
            assertEquals(1, result.preparation().blockedRoots());
            assertEquals(
                    2,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM stg.analytic_manifest_field_audit a JOIN stg.manifesto_obser"
                                    + "vation o ON o.manifesto_observation_id=a.source_observation_id JOIN ctl.analytic_mani"
                                    + "fest_preparation p ON p.execution_id=o.execution_id WHERE p.run_id=? AND a.issue IN('"
                                    + "DECIMAL_EXACT','ARRAY_BOUND')"));
            final var unrelated =
                    new LocalAnalyticManifestRuntime(
                            session,
                            UUID.randomUUID(),
                            fixture.relational(),
                            policy(),
                            CLOCK,
                            Clock.systemUTC());
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    unrelated.capture(
                                            DATE,
                                            ExecutionMode.BACKFILL,
                                            null,
                                            new RelationalSyntheticSource(
                                                    source(
                                                            AnalyticLaboratoryManifestCaptureIT
                                                                    .manifest())),
                                            CancellationToken.none()));
            assertEquals(53601, failure.getErrorCode());
            assertEquals(
                    1,
                    count(
                            session,
                            fixture.relational(),
                            "SELECT COUNT_BIG(*) FROM ctl.relational_lab_capture WHERE run_id=? AND entity_name='m"
                                    + "anifestos'"));
        }
    }

    static RelationalLaboratoryPolicy policy() {
        return new RelationalLaboratoryPolicy(
                DATE, DATE.plusDays(2), 1000, 10, 3, 60, 2, 0, 2, 100);
    }

    @Test
    void oneNanosecondChangesTheWinningCohortInCapturePreparationAndStaleReplay() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            final var older =
                    AnalyticLaboratoryManifestCaptureIT.manifest()
                            .put("finished_at", "2036-04-01T12:00:00.000000001Z")
                            .put("mft_uer_name", "SYNTHETIC OLD")
                            .put("invoices_count", 1);
            final var newer =
                    older.deepCopy()
                            .put("finished_at", "2036-04-01T09:00:00.000000002-03:00")
                            .put("mft_uer_name", "SYNTHETIC NEW")
                            .put("invoices_count", 2);
            final var runtime = runtime(session, fixture);
            final var capture =
                    runtime.capture(
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            page ->
                                    page == 1
                                            ? "[" + older + "]"
                                            : page == 2 ? "[" + newer + "]" : "[]");
            assertEquals(0, capture.preparation().blockedRoots());
            assertEquals(
                    1,
                    count(
                            session,
                            fixture.relational(),
                            "SELECT COUNT_BIG(*) FROM core.relational_lab_root WHERE run_id=? AND entity_name='man"
                                    + "ifestos' AND nano=2"));
            assertEquals(
                    1,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_manifest_projection WHERE run_id=? AND dis"
                                    + "position='READY' AND fresh_nano=2 AND source_rows=1 AND invoices_count=2 AND mft_uer_"
                                    + "name='SYNTHETIC NEW'"));
            final var stale = runtime.capture(DATE, ExecutionMode.BACKFILL, null, source(older));
            assertEquals(0, stale.preparation().blockedRoots());
            assertEquals(
                    1,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_manifest_projection s JOIN core.analytic_m"
                                    + "anifest_snapshot_lineage l ON l.snapshot_id=s.snapshot_id JOIN stg.analytic_manifest_"
                                    + "attributes a ON a.source_observation_id=l.source_observation_id WHERE s.run_id=? AND "
                                    + "s.fresh_nano=2 AND a.fresh_nano=2 AND s.invoices_count=2 AND s.source_rows=1"));
        }
    }

    @Test
    void mdfeStatusRemainsRootScalarAcrossEqualClockCaptures() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            final var runtime = runtime(session, fixture);
            final var first = AnalyticLaboratoryManifestCaptureIT.manifest();
            runtime.capture(DATE, ExecutionMode.BOOTSTRAP, null, source(first));
            final var changed = first.deepCopy().put("mdfe_status", "rejected");
            assertEquals(
                    1,
                    runtime.capture(DATE, ExecutionMode.BACKFILL, null, source(changed))
                            .preparation()
                            .blockedRoots());
            assertEquals(
                    1,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_manifest_projection WHERE run_id=? AND dis"
                                    + "position='ATTRIBUTE_CONFLICT'"));
        }
    }

    @Test
    void equivalentOffsetsAreCoherentAcrossSevenFieldsButOneNanosecondIsNot() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
            final var first = AnalyticLaboratoryManifestCaptureIT.manifest();
            final var offset = first.deepCopy();
            for (final var field :
                    java.util.List.of(
                            "created_at",
                            "departured_at",
                            "closed_at",
                            "finished_at",
                            "mobile_read_at",
                            "mft_s_n_starting_at",
                            "mft_s_n_ending_at")) {
                offset.put(field, "2036-04-01T09:00:00.123456789-03:00");
            }
            final var runtime = runtime(session, fixture);
            assertEquals(
                    0,
                    runtime.capture(
                                    DATE,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    page ->
                                            page == 1
                                                    ? "[" + first + "]"
                                                    : page == 2 ? "[" + offset + "]" : "[]")
                            .preparation()
                            .blockedRoots());
            assertEquals(
                    1,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_manifest_projection WHERE run_id=? AND dis"
                                    + "position='READY' AND source_rows=2 AND fresh_nano=123456789 AND DATEPART(TZOFFSET,cre"
                                    + "ated_at)=0"));
            assertEquals(
                    7,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_manifest_projection s JOIN core.analytic_m"
                                    + "anifest_snapshot_lineage l ON l.snapshot_id=s.snapshot_id JOIN stg.analytic_manifest_"
                                    + "field_audit a ON a.source_observation_id=l.source_observation_id WHERE s.run_id=? AND"
                                    + " a.raw_value=N'2036-04-01T09:00:00.123456789-03:00'"));
            final var changed =
                    offset.deepCopy().put("mobile_read_at", "2036-04-01T09:00:00.123456790-03:00");
            assertEquals(
                    1,
                    runtime.capture(DATE, ExecutionMode.BACKFILL, null, source(changed))
                            .preparation()
                            .blockedRoots());
            assertEquals(
                    1,
                    count(
                            session,
                            fixture.run(),
                            "SELECT COUNT_BIG(*) FROM core.analytic_lab_manifest_projection WHERE run_id=? AND dis"
                                    + "position='ATTRIBUTE_CONFLICT'"));
        }
    }

    static TestRuntime runtime(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture) {
        return new TestRuntime(
                new LocalAnalyticManifestRuntime(
                        session,
                        fixture.run(),
                        fixture.relational(),
                        policy(),
                        CLOCK,
                        Clock.systemUTC()));
    }

    static java.util.function.IntFunction<String> source(final ObjectNode row) {
        return page -> page == 1 ? "[" + row + "]" : "[]";
    }

    private static long count(
            final ColetaTemporalLaboratorySession session, final UUID run, final String query)
            throws SQLException {
        return AnalyticLaboratoryRasterIT.scalar(session, query, run);
    }

    record TestRuntime(LocalAnalyticManifestRuntime runtime) {
        LocalAnalyticManifestRuntime.Capture capture(
                final java.time.LocalDate date,
                final ExecutionMode mode,
                final UUID replay,
                final java.util.function.IntFunction<String> source)
                throws SQLException {
            return runtime.capture(
                    date,
                    mode,
                    replay,
                    new RelationalSyntheticSource(source),
                    CancellationToken.none());
        }
    }
}
