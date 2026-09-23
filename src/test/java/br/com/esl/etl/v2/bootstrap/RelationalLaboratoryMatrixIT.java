package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.RelationalLaboratoryLocalIntegrationIT.counts;
import static br.com.esl.etl.v2.bootstrap.RelationalLaboratoryLocalIntegrationIT.policy;
import static br.com.esl.etl.v2.bootstrap.RelationalLaboratoryLocalIntegrationIT.scalar;
import static br.com.esl.etl.v2.bootstrap.RelationalLaboratoryLocalIntegrationIT.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding.Cardinality;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding.Key;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding.Relation;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding.WireType;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Counterproofs use actual SQL constraints/procedures and independent rollback owners. */
class RelationalLaboratoryMatrixIT {
    private static final LocalDate DATE = LocalDate.of(2036, 4, 1);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2036-04-02T12:00:00Z"), ZoneOffset.UTC);
    private static final CancellationToken NONE = CancellationToken.none();

    @Test
    void explicitMcSetRetiresReplacedComponentButPartialBindingAndOtherOriginsSurvive()
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            capture(session, run, DataExportTemplate.MANIFESTOS, 1, 2);
            capture(session, run, DataExportTemplate.COLETAS, 1, 3);
            final var old =
                    binding(
                            "synthetic-set-old",
                            Relation.MC,
                            1,
                            Key.integer(100001),
                            200001,
                            Key.root(),
                            Cardinality.ONE_TO_ONE,
                            1,
                            DATE);
            final var other =
                    binding(
                            "synthetic-set-other",
                            Relation.MC,
                            2,
                            Key.integer(100002),
                            200002,
                            Key.root(),
                            Cardinality.ONE_TO_ONE,
                            1,
                            DATE);
            lab.bindBatch(run, List.of(old, other), NONE);
            lab.declareCompleteMcSets(run, 1, NONE);
            lab.resolve(run, UUID.randomUUID(), NONE);
            assertEquals(
                    "2",
                    scalar(
                            session,
                            "SELECT COUNT(*) FROM core.relational_lab_link WHERE active=1"));
            runtime(session, run)
                    .capture(
                            DataExportTemplate.MANIFESTOS,
                            DATE,
                            ExecutionMode.BACKFILL,
                            null,
                            source(
                                    "{\"sequence_code\":1,\"mft_pfs_pck_sequence_code\":100003,"
                                            + "\"created_at\":\"2036-04-01T11:00:00Z\",\"status\":\"pending\","
                                            + "\"km\":\"0\",\"synthetic_fixture\":true}"),
                            NONE);
            lab.bindBatch(
                    run,
                    List.of(
                            binding(
                                    "synthetic-set-new",
                                    Relation.MC,
                                    1,
                                    Key.integer(100003),
                                    200003,
                                    Key.root(),
                                    Cardinality.ONE_TO_ONE,
                                    2,
                                    DATE)),
                    NONE);
            lab.resolve(run, UUID.randomUUID(), NONE);
            assertEquals(
                    "3",
                    scalar(
                            session,
                            "SELECT COUNT(*) FROM core.relational_lab_link WHERE active=1"));
            lab.declareCompleteMcSets(run, 2, NONE);
            lab.resolve(run, UUID.randomUUID(), NONE);
            assertEquals(
                    "2",
                    scalar(
                            session,
                            "SELECT COUNT(*) FROM core.relational_lab_link WHERE active=1"));
            assertEquals("3", scalar(session, "SELECT COUNT(*) FROM core.relational_lab_link"));
            lab.declareCompleteMcSets(run, 2, NONE);
            assertEquals(0, lab.resolve(run, UUID.randomUUID(), NONE).inserted());
            lab.bindBatch(
                    run,
                    List.of(
                            binding(
                                    "synthetic-set-conflict",
                                    Relation.MC,
                                    1,
                                    Key.integer(100003),
                                    200001,
                                    Key.root(),
                                    Cardinality.ONE_TO_ONE,
                                    2,
                                    DATE)),
                    NONE);
            lab.resolve(run, UUID.randomUUID(), NONE);
            assertEquals(
                    "1",
                    scalar(
                            session,
                            "SELECT COUNT(*) FROM core.relational_lab_link WHERE active=1"));
        }
    }

    @Test
    void mdfePairsHaveTheirOwnCohortsAndPreserveArbitraryPrecisionNumbers() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            runtime(session, run)
                    .capture(
                            DataExportTemplate.MANIFESTOS,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            source(
                                    mdfe("1", "9".repeat(80), "10"),
                                    mdfe("2", "8", "11"),
                                    mdfe("1", "9".repeat(80), "10")),
                            NONE);
            assertEquals(1, lab.status(run).manifestos());
            assertEquals("2", scalar(session, "SELECT COUNT_BIG(*) FROM stg.relational_lab_mdfe"));
            assertEquals(
                    "1",
                    scalar(
                            session,
                            """
                    SELECT COUNT_BIG(*) FROM stg.relational_lab_mdfe
                    WHERE mdfe_key=REPLICATE('1',44) AND mdfe_number=REPLICATE(N'9',80)
                    """));
            assertEquals(
                    "1",
                    scalar(
                            session,
                            """
                    SELECT COUNT_BIG(*) FROM stg.relational_lab_mdfe
                    WHERE mdfe_key=REPLICATE('2',44) AND mdfe_number=N'8'
                    """));
        }
    }

    @Test
    void divergentMdfePairAtSameChildFreshnessIsRejected() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            start(session, run);
            assertEquals(
                    53252,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            runtime(session, run)
                                                    .capture(
                                                            DataExportTemplate.MANIFESTOS,
                                                            DATE,
                                                            ExecutionMode.BOOTSTRAP,
                                                            null,
                                                            source(
                                                                    mdfe("1", "7", "10"),
                                                                    mdfe("1", "8", "10")),
                                                            NONE))
                            .getErrorCode());
        }
    }

    @Test
    void sealedCaptureCannotBeConsumedByAnotherRun() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID first = UUID.randomUUID();
            start(session, first);
            final var capture =
                    runtime(session, first)
                            .capture(
                                    DataExportTemplate.COLETAS,
                                    DATE,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    source(),
                                    NONE);
            final UUID second = UUID.randomUUID();
            final var other = start(session, second);
            assertEquals(
                    53210,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            other.capture(
                                                    second,
                                                    capture.executionId(),
                                                    UUID.randomUUID(),
                                                    NONE))
                            .getErrorCode());
        }
    }

    private static String mdfe(final String keyDigit, final String number, final String hour) {
        return "{\"sequence_code\":1,\"created_at\":\"2036-04-01T"
                + hour
                + ":00:00Z\","
                + "\"mft_pfs_pck_sequence_code\":7,\"synthetic_fixture\":true,\"mft_mfs_key\":\""
                + keyDigit.repeat(44)
                + "\",\"mft_mfs_number\":"
                + number
                + "}";
    }

    @Test
    void coletaItemCanBindTwoFretesUnderDeclaredOneToManyContract() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            capture(session, run, DataExportTemplate.MANIFESTOS, 1, 1);
            capture(session, run, DataExportTemplate.COLETAS, 1, 1);
            capture(session, run, DataExportTemplate.FRETES, 1, 2);
            final var bindings = new ArrayList<RelationalBinding>();
            bindings.add(RelationalLaboratoryFixtures.bindingBatch(DATE, 1, 1).get(0));
            for (int target = 1; target <= 2; target++) {
                bindings.add(
                        binding(
                                "synthetic-cf-many-" + target,
                                Relation.CF,
                                200001,
                                Key.integer(1),
                                300000 + target,
                                new Key(WireType.STRING, "item-" + target),
                                Cardinality.ONE_TO_MANY,
                                1,
                                DATE));
            }
            lab.bindBatch(run, bindings, NONE);
            assertEquals(3, lab.resolve(run, UUID.randomUUID(), NONE).resolved());
            assertEquals(1, lab.status(run).coletas());
            assertEquals(2, lab.status(run).coletaFretes());
            assertTrue(lab.status(run).complete());
        }
    }

    @Test
    void paddedSqlStringsRemainInvalidCandidatesInsteadOfCollapsingIdentity() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            runtime(session, run)
                    .capture(
                            DataExportTemplate.COLETAS,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            source(
                                    "{\"id\":1,\"request_date\":\"2036-04-01\",\"synthetic_fixture\":true,"
                                            + "\"synthetic_item_key\":\"item \"}"),
                            NONE);
            assertEquals(
                    "INVALID",
                    scalar(session, "SELECT validity FROM stg.relational_lab_component"));
            assertEquals(
                    "24",
                    scalar(
                            session,
                            "SELECT DATALENGTH(component_key) FROM stg.relational_lab_component"));
            assertEquals(
                    "0",
                    scalar(session, "SELECT stg.fn_relational_lab_key_valid(N'STRING:item ')"));
            assertEquals(
                    "1", scalar(session, "SELECT stg.fn_relational_lab_key_valid(N'STRING:item')"));
            assertEquals(1, lab.status(run).unboundCandidates());
        }
    }

    @Test
    void absenceOfCaptureDiffersFromThreeCompleteEmptyCaptures() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            assertFalse(lab.status(run).complete());
            for (final var template :
                    List.of(
                            DataExportTemplate.MANIFESTOS,
                            DataExportTemplate.COLETAS,
                            DataExportTemplate.FRETES)) {
                runtime(session, run)
                        .capture(template, DATE, ExecutionMode.BOOTSTRAP, null, source(), NONE);
            }
            assertTrue(lab.status(run).complete());
            assertEquals(0, lab.status(run).physicalRows());
            assertEquals(3, lab.status(run).capturedEntities());
        }
    }

    @Test
    void hydrationEmptyResultDefersInsteadOfReportingSuccess() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            seedOrphan(session, run);
            final var result =
                    new RelationalLaboratoryExecutor(session, run, CLOCK, Clock.systemUTC())
                            .hydrate(
                                    1,
                                    NONE,
                                    MonotonicTicker.systemTicker(),
                                    (claim, size) -> source());
            assertEquals(0, result.succeeded());
            assertEquals(1, result.failed());
            assertFalse(result.status().complete());
            assertEquals(
                    "1",
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.relational_lab_attempt WHERE result=N'TEMPORARY_FAILURE'"));
        }
    }

    @Test
    void cancellationAfterClaimAbandonsOwnedAttempt() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            seedOrphan(session, run);
            final var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
            final var executor =
                    new RelationalLaboratoryExecutor(session, run, CLOCK, Clock.systemUTC());
            assertThrows(
                    ResilienceCancelledException.class,
                    () ->
                            executor.hydrate(
                                    1,
                                    cancelled::get,
                                    MonotonicTicker.systemTicker(),
                                    (claim, size) -> {
                                        cancelled.set(true);
                                        return source();
                                    }));
            assertEquals(
                    "1",
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.relational_lab_attempt WHERE result=N'ABANDONED'"));
        }
    }

    @Test
    void monotonicTimeoutAfterClaimIsFiniteWithoutSleep() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            seedOrphan(session, run);
            final var ticks = new java.util.concurrent.atomic.AtomicLong();
            final var result =
                    new RelationalLaboratoryExecutor(session, run, CLOCK, Clock.systemUTC())
                            .hydrate(
                                    1,
                                    NONE,
                                    () -> ticks.getAndAdd(Duration.ofSeconds(21).toNanos()),
                                    RelationalLaboratoryFixtures::hydration);
            assertEquals(1, result.failed());
            assertEquals(0, result.succeeded());
            assertEquals(2, result.status().captures());
        }
    }

    @Test
    void oneNanosecondOffsetsAndOlderTerminalUseExactColetaProjection() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            start(session, run);
            final var runtime = runtime(session, run);
            final String prefix =
                    "{\"id\":1,\"request_date\":\"2036-04-01\",\"synthetic_item_key\":0,"
                            + "\"synthetic_fixture\":true,\"status\":\"pending\",\"status_updated_at\":\"";
            assertEquals(
                    1,
                    runtime.capture(
                                    DataExportTemplate.COLETAS,
                                    DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    source(prefix + "2036-04-01T10:00:00.123456788Z\"}"),
                                    NONE)
                            .receipt()
                            .inserted());
            assertEquals(
                    1,
                    runtime.capture(
                                    DataExportTemplate.COLETAS,
                                    DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    source(prefix + "2036-04-01T10:00:00.123456789Z\"}"),
                                    NONE)
                            .receipt()
                            .updated());
            assertEquals(
                    1,
                    runtime.capture(
                                    DataExportTemplate.COLETAS,
                                    DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    source(prefix + "2036-04-01T07:00:00.123456789-03:00\"}"),
                                    NONE)
                            .receipt()
                            .noop());
            assertEquals(
                    1,
                    runtime.capture(
                                    DataExportTemplate.COLETAS,
                                    DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    source(prefix + "2036-04-01T09:00:00Z\"}"),
                                    NONE)
                            .receipt()
                            .noop());
            assertEquals(
                    1,
                    runtime.capture(
                                    DataExportTemplate.COLETAS,
                                    DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    source(
                                            prefix.replace("pending", "finished")
                                                    + "2036-04-01T09:00:00Z\"}"),
                                    NONE)
                            .receipt()
                            .updated());
            assertEquals(
                    1,
                    runtime.capture(
                                    DataExportTemplate.COLETAS,
                                    DATE,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    source(prefix + "2036-04-01T11:00:00Z\"}"),
                                    NONE)
                            .receipt()
                            .noop());
            assertEquals(
                    "1",
                    scalar(
                            session,
                            "SELECT terminal FROM core.relational_lab_root WHERE entity_name=N'coletas'"));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COLETAS", "FRETES"})
    void partialTraversalIsNeverCapturedForAnyVertical(final DataExportTemplate template)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            final var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
            final var observed =
                    RelationalLaboratoryFixtures.source(template, DATE, 1, 2, 1, true)
                            .observed(
                                    new RelationalSyntheticSource.Observer() {
                                        @Override
                                        public void batchStaged(final int rows) {
                                            cancelled.set(true);
                                        }
                                    });
            assertThrows(
                    ResilienceCancelledException.class,
                    () ->
                            runtime(session, run)
                                    .capture(
                                            template,
                                            DATE,
                                            ExecutionMode.BOOTSTRAP,
                                            null,
                                            observed,
                                            cancelled::get));
            assertEquals(0, lab.status(run).captures());
        }
    }

    @Test
    void contractMismatchIsRejectedAtSqlCaptureBoundary() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var contracts = RelationalSyntheticSource.contracts();
            new JdbcRelationalLaboratory(session, CLOCK)
                    .start(
                            run,
                            policy(),
                            new br.com.esl.etl.v2.plataforma.relacional.RelationalCaptureContracts(
                                    contracts.manifestos(), "0".repeat(64), contracts.fretes()));
            assertEquals(
                    53210,
                    assertThrows(
                                    SQLException.class,
                                    () -> capture(session, run, DataExportTemplate.COLETAS, 1, 1))
                            .getErrorCode());
        }
    }

    @ParameterizedTest
    @EnumSource(Cardinality.class)
    void cardinalityIsExplicitAndDoesNotMultiplyRoots(final Cardinality cardinality)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            capture(session, run, DataExportTemplate.MANIFESTOS, 1, 1);
            capture(session, run, DataExportTemplate.COLETAS, 1, 2);
            capture(session, run, DataExportTemplate.FRETES, 1, 2);
            final var bindings = new ArrayList<RelationalBinding>();
            for (int target = 1; target <= 2; target++) {
                bindings.add(
                        binding(
                                "synthetic-mc-" + target,
                                Relation.MC,
                                1,
                                Key.integer(100001),
                                200000 + target,
                                Key.root(),
                                cardinality,
                                1,
                                DATE));
                for (int origin = 1; origin <= 2; origin++) {
                    bindings.add(
                            binding(
                                    "synthetic-cf-" + origin + "-" + target,
                                    Relation.CF,
                                    200000 + origin,
                                    Key.integer(origin),
                                    300000 + target,
                                    new Key(WireType.STRING, "item-" + target),
                                    Cardinality.MANY_TO_MANY,
                                    1,
                                    DATE));
                }
            }
            lab.bindBatch(run, bindings, NONE);
            final var receipt = lab.resolve(run, UUID.randomUUID(), NONE);
            assertEquals(cardinality == Cardinality.ONE_TO_ONE ? 0 : 6, receipt.resolved());
            assertEquals(1, lab.status(run).manifestos());
            assertEquals(2, lab.status(run).coletas());
            assertEquals(cardinality != Cardinality.ONE_TO_ONE, lab.status(run).complete());
            assertEquals(
                    cardinality == Cardinality.ONE_TO_ONE ? 0 : 4, lab.status(run).coletaFretes());
        }
    }

    @Test
    void equivalentEvidenceAndRevisionHaveDeterministicHistory() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            capture(session, run, DataExportTemplate.MANIFESTOS, 1, 1);
            capture(session, run, DataExportTemplate.COLETAS, 1, 2);
            final var first =
                    binding(
                            "synthetic-evidence-a",
                            Relation.MC,
                            1,
                            Key.integer(100001),
                            200001,
                            Key.root(),
                            Cardinality.ONE_TO_ONE,
                            1,
                            DATE);
            final var equivalent =
                    binding(
                            "synthetic-evidence-b",
                            Relation.MC,
                            1,
                            Key.integer(100001),
                            200001,
                            Key.root(),
                            Cardinality.ONE_TO_ONE,
                            1,
                            DATE);
            lab.bindBatch(run, List.of(first, equivalent), NONE);
            lab.bindBatch(run, List.of(equivalent, first), NONE);
            assertEquals(1, lab.resolve(run, UUID.randomUUID(), NONE).inserted());
            final var revised =
                    binding(
                            "synthetic-evidence-c",
                            Relation.MC,
                            1,
                            Key.integer(100001),
                            200002,
                            Key.root(),
                            Cardinality.ONE_TO_ONE,
                            2,
                            DATE);
            lab.bindBatch(run, List.of(revised), NONE);
            final var receipt = lab.resolve(run, UUID.randomUUID(), NONE);
            assertEquals(1, receipt.updated());
            assertEquals(1, receipt.inserted());
            assertEquals(1, lab.status(run).manifestoColetas());
            assertEquals("2", scalar(session, "SELECT COUNT_BIG(*) FROM core.relational_lab_link"));
            assertEquals(1, lab.resolve(run, UUID.randomUUID(), NONE).noop());
        }
    }

    @Test
    void equalAliasesNeverCreateBindingsAndCaptureDateMustBeDemonstrated() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            capture(session, run, DataExportTemplate.MANIFESTOS, 1, 1);
            capture(session, run, DataExportTemplate.COLETAS, 1, 1);
            assertEquals(0, lab.resolve(run, UUID.randomUUID(), NONE).resolved());
            assertEquals(0, lab.status(run).manifestoColetas());
            lab.bindBatch(
                    run,
                    List.of(
                            binding(
                                    "synthetic-wrong-day",
                                    Relation.MC,
                                    1,
                                    Key.integer(100001),
                                    200001,
                                    Key.root(),
                                    Cardinality.ONE_TO_ONE,
                                    1,
                                    DATE.plusDays(1))),
                    NONE);
            assertEquals(1, lab.resolve(run, UUID.randomUUID(), NONE).orphaned());
            assertEquals(1, lab.claimBatch(run, UUID.randomUUID(), 1, NONE).size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABSENT", "NULL", "BOOLEAN", "STRING", "INTEGER"})
    void preservesCandidatePresenceAndWireType(final String kind) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            final String field =
                    switch (kind) {
                        case "ABSENT" -> "";
                        case "NULL" -> ",\"synthetic_item_key\":null";
                        case "BOOLEAN" -> ",\"synthetic_item_key\":true";
                        case "STRING" -> ",\"synthetic_item_key\":\"0\"";
                        default -> ",\"synthetic_item_key\":0";
                    };
            runtime(session, run)
                    .capture(
                            DataExportTemplate.COLETAS,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            source(
                                    "{\"id\":200001,\"request_date\":\"2036-04-01\",\"synthetic_fixture\":true"
                                            + field
                                            + "}"),
                            NONE);
            assertEquals(
                    kind.equals("ABSENT") || kind.equals("NULL") ? kind : "VALUE",
                    scalar(session, "SELECT presence FROM stg.relational_lab_component"));
            if (kind.equals("INTEGER") || kind.equals("STRING")) {
                assertEquals(
                        kind + ":0",
                        scalar(session, "SELECT component_key FROM stg.relational_lab_component"));
            } else {
                assertEquals(
                        "INVALID",
                        scalar(session, "SELECT validity FROM stg.relational_lab_component"));
            }
            assertEquals(1, lab.status(run).unboundCandidates());
            assertFalse(lab.status(run).complete());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MALFORMED", "MARKER", "WRONG_TYPE", "PARTIAL"})
    void rejectedSourceCannotSealCapture(final String failure) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            final var source =
                    new RelationalSyntheticSource(
                            page ->
                                    switch (failure) {
                                        case "MALFORMED" -> "[{";
                                        case "MARKER" -> "[{\"id\":1}]";
                                        case "WRONG_TYPE" ->
                                                "[{\"id\":{},\"synthetic_fixture\":true}]";
                                        default -> {
                                            if (page > 1) {
                                                throw new IllegalStateException(
                                                        "SYNTHETIC_PARTIAL");
                                            }
                                            yield "[{\"id\":1,\"request_date\":\"2036-04-01\",\"synthetic_fixture\":true}]";
                                        }
                                    });
            assertThrows(
                    RuntimeException.class,
                    () ->
                            runtime(session, run)
                                    .capture(
                                            DataExportTemplate.COLETAS,
                                            DATE,
                                            ExecutionMode.BOOTSTRAP,
                                            null,
                                            source,
                                            NONE));
            assertEquals(0, lab.status(run).captures());
            assertEquals(0, lab.status(run).coletas());
        }
    }

    @Test
    void exactConflictIsRejectedBeforeTerminalPrecedenceWithinCapture() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            start(session, run);
            final String prefix =
                    "{\"id\":1,\"request_date\":\"2036-04-01\","
                            + "\"status_updated_at\":\"2036-04-01T10:00:00.123456789Z\",\"synthetic_fixture\":true,\"status\":";
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    runtime(session, run)
                                            .capture(
                                                    DataExportTemplate.COLETAS,
                                                    DATE,
                                                    ExecutionMode.BOOTSTRAP,
                                                    null,
                                                    source(
                                                            prefix + "\"pending\"}",
                                                            prefix + "\"finished\"}"),
                                                    NONE));
            assertEquals(51428, failure.getErrorCode());
        }
    }

    @Test
    void manifestoChildrenSurviveOlderRootCohortAndMetricsAreNotSummed() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            runtime(session, run)
                    .capture(
                            DataExportTemplate.MANIFESTOS,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            source(
                                    "{\"sequence_code\":1,\"created_at\":\"2036-04-01T10:00:00Z\","
                                            + "\"mft_pfs_pck_sequence_code\":7,\"km\":\"9\",\"synthetic_fixture\":true}",
                                    "{\"sequence_code\":1,\"created_at\":\"2036-04-01T11:00:00Z\","
                                            + "\"mft_pfs_pck_sequence_code\":2,\"km\":\"10\",\"synthetic_fixture\":true}"),
                            NONE);
            assertEquals(1, lab.status(run).manifestos());
            assertEquals(
                    "2",
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM stg.relational_lab_component WHERE component_kind=N'PICK'"));
            assertEquals(
                    "1",
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM stg.relational_lab_component WHERE component_key=N'INTEGER:7'"));
        }
    }

    @Test
    void divergentEvidenceRetryIsRejectedPhysically() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = start(session, run);
            final var first = RelationalLaboratoryFixtures.bindingBatch(DATE, 1, 1).get(0);
            lab.bindBatch(run, List.of(first), NONE);
            final var changed =
                    binding(
                            first.evidenceId(),
                            Relation.MC,
                            1,
                            Key.integer(100001),
                            200002,
                            Key.root(),
                            Cardinality.ONE_TO_ONE,
                            1,
                            DATE);
            final var failure =
                    assertThrows(
                            SQLException.class, () -> lab.bindBatch(run, List.of(changed), NONE));
            assertEquals(53204, failure.getErrorCode());
        }
    }

    @Test
    void divergentPlanRetryIsRejectedPhysically() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            start(session, run);
            final var executor =
                    new RelationalLaboratoryExecutor(session, run, CLOCK, Clock.systemUTC());
            executor.plan(ExecutionMode.BOOTSTRAP, 1, 1);
            executor.plan(ExecutionMode.BOOTSTRAP, 1, 1);
            assertEquals(
                    53242,
                    assertThrows(
                                    SQLException.class,
                                    () -> executor.plan(ExecutionMode.BOOTSTRAP, 2, 1))
                            .getErrorCode());
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = JdbcRelationalLaboratory.AttemptResult.class,
            names = {"TEMPORARY_FAILURE", "ABANDONED", "CONTRACT_FAILURE", "CONFLICT"})
    void attemptsHaveDifferentDispositionAndFiniteEligibility(
            final JdbcRelationalLaboratory.AttemptResult result) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = seedOrphan(session, run);
            final UUID owner = UUID.randomUUID();
            final var claim = lab.claimBatch(run, owner, 1, NONE).get(0);
            lab.finish(run, claim.backlogId(), owner, result, null);
            assertTrue(lab.claimBatch(run, owner, 1, NONE).isEmpty());
            final var later =
                    new JdbcRelationalLaboratory(
                            session, Clock.offset(CLOCK, Duration.ofSeconds(2)));
            final var claims = later.claimBatch(run, owner, 1, NONE);
            assertEquals(
                    result == JdbcRelationalLaboratory.AttemptResult.TEMPORARY_FAILURE
                                    || result == JdbcRelationalLaboratory.AttemptResult.ABANDONED
                            ? 1
                            : 0,
                    claims.size());
        }
    }

    @Test
    void leaseExpiresWithoutSleepAndStopsAtAttemptCeiling() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            seedOrphan(session, run);
            for (int attempt = 0; attempt <= 3; attempt++) {
                final var lab =
                        new JdbcRelationalLaboratory(
                                session, Clock.offset(CLOCK, Duration.ofSeconds(attempt * 20L)));
                final var claims = lab.claimBatch(run, UUID.randomUUID(), 1, NONE);
                assertEquals(attempt < 3 ? 1 : 0, claims.size());
                if (attempt < 3) {
                    assertEquals(attempt + 1, claims.get(0).attempt());
                }
            }
            assertEquals(
                    "3",
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.relational_lab_attempt WHERE result=N'LEASE_EXPIRED'"));
            assertEquals(
                    "1",
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.relational_lab_backlog WHERE cause=N'LEASE_EXPIRED' AND state=N'QUARANTINED'"));
        }
    }

    @Test
    void hydrationUsesOnlyClaimedTargetAndContractFailureRemainsVisible() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            seedOrphan(session, run);
            final var executor =
                    new RelationalLaboratoryExecutor(session, run, CLOCK, Clock.systemUTC());
            final var hydration =
                    executor.hydrate(
                            1,
                            NONE,
                            MonotonicTicker.systemTicker(),
                            (claim, size) -> {
                                throw new IllegalArgumentException("SYNTHETIC_CONTRACT_FAILURE");
                            });
            assertEquals(1, hydration.failed());
            assertEquals(0, hydration.succeeded());
            assertFalse(hydration.status().complete());
            assertEquals(
                    "1",
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.relational_lab_attempt WHERE result=N'CONTRACT_FAILURE'"));
        }
    }

    @Test
    void cancellationBeforeClaimDoesNotSpendAttempt() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var lab = seedOrphan(session, run);
            assertThrows(
                    ResilienceCancelledException.class,
                    () -> lab.claimBatch(run, UUID.randomUUID(), 1, () -> true));
            assertEquals(
                    "0", scalar(session, "SELECT COUNT_BIG(*) FROM ctl.relational_lab_attempt"));
        }
    }

    @Test
    void twoPhysicalSessionsContendOnRealClaimThenSecondResolvesAndConsumesAfterRollback()
            throws Exception {
        final var worker = Executors.newSingleThreadExecutor();
        try (var first = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(first);
            final UUID run = UUID.randomUUID();
            seedOrphan(first, run).claimBatch(run, UUID.randomUUID(), 1, NONE);
            final var blocked =
                    worker.submit(
                            () -> {
                                try (var second =
                                        ColetaTemporalLaboratorySession.openFromEnvironment()) {
                                    return assertThrows(
                                                    SQLException.class,
                                                    () ->
                                                            new JdbcRelationalLaboratory(
                                                                            second, CLOCK)
                                                                    .claimBatch(
                                                                            run,
                                                                            UUID.randomUUID(),
                                                                            1,
                                                                            NONE))
                                            .getErrorCode();
                                }
                            });
            assertEquals(53201, blocked.get(10, TimeUnit.SECONDS));
            first.rollback();
            final var resumed =
                    worker.submit(
                            () -> {
                                try (var second =
                                        ColetaTemporalLaboratorySession.openFromEnvironment()) {
                                    seedOrphan(second, run);
                                    final var result =
                                            new RelationalLaboratoryExecutor(
                                                            second, run, CLOCK, Clock.systemUTC())
                                                    .hydrate(
                                                            1,
                                                            NONE,
                                                            MonotonicTicker.systemTicker(),
                                                            RelationalLaboratoryFixtures
                                                                    ::hydration);
                                    assertTrue(result.status().complete());
                                    assertEquals(1, result.status().coletaFretes());
                                    return result.succeeded();
                                }
                            });
            assertEquals(1, resumed.get(30, TimeUnit.SECONDS));
            assertEquals(before, counts(first));
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static RelationalBinding binding(
            final String evidence,
            final Relation relation,
            final long origin,
            final Key component,
            final long target,
            final Key targetComponent,
            final Cardinality cardinality,
            final int revision,
            final LocalDate date) {
        return new RelationalBinding(
                evidence,
                relation,
                Key.integer(origin),
                component,
                Key.integer(target),
                targetComponent,
                date,
                revision,
                cardinality);
    }

    private static JdbcRelationalLaboratory start(
            final ColetaTemporalLaboratorySession session, final UUID run) throws SQLException {
        final var lab = new JdbcRelationalLaboratory(session, CLOCK);
        lab.start(run, policy(), RelationalSyntheticSource.contracts());
        return lab;
    }

    private static LocalRelationalRuntime runtime(
            final ColetaTemporalLaboratorySession session, final UUID run) {
        return new LocalRelationalRuntime(session, run, policy(), CLOCK, Clock.systemUTC());
    }

    private static void capture(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final DataExportTemplate template,
            final int first,
            final int count)
            throws SQLException {
        runtime(session, run)
                .capture(
                        template,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        RelationalLaboratoryFixtures.source(template, DATE, first, count, 3, true),
                        NONE);
    }

    private static JdbcRelationalLaboratory seedOrphan(
            final ColetaTemporalLaboratorySession session, final UUID run) throws SQLException {
        final var lab = start(session, run);
        capture(session, run, DataExportTemplate.MANIFESTOS, 1, 1);
        capture(session, run, DataExportTemplate.FRETES, 1, 1);
        lab.bindBatch(run, RelationalLaboratoryFixtures.bindingBatch(DATE, 1, 1), NONE);
        lab.resolve(run, UUID.randomUUID(), NONE);
        return lab;
    }
}
