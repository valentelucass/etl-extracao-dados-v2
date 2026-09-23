package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchBinding;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatcher;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionResult;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryDispatchResult;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryExpectation;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoverySnapshot.Reason;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RuntimeDurableRecoveryIntegrationTest {
    @TempDir Path temporary;

    static RuntimeRecoveryDispatchResult recover(
            final LocalRuntimeIntegrationTest.Fixture fixture,
            final CancellationToken cancellation,
            final LocalRuntimeIntegrationTest.Work... works) {
        return new RuntimeDispatcher(
                        fixture.control,
                        fixture.clock,
                        fixture.recoveryPort(),
                        Arrays.stream(works)
                                .map(w -> new RuntimeDispatchBinding(w.id, w.handler))
                                .toArray(RuntimeDispatchBinding[]::new))
                .recover(
                        fixture.plan(works),
                        cancellation,
                        Arrays.stream(works)
                                .map(
                                        w ->
                                                new RuntimeRecoveryExpectation(
                                                        w.id,
                                                        w.binding,
                                                        LocalRuntimeIntegrationTest.POLICY))
                                .toArray(RuntimeRecoveryExpectation[]::new));
    }

    private LocalRuntimeIntegrationTest.Fixture reloaded(
            final LocalRuntimeIntegrationTest.Fixture original) throws Exception {
        final Path state = temporary.resolve(UUID.randomUUID() + ".properties");
        original.jdbc.recovery.save(state);
        return new LocalRuntimeIntegrationTest.Fixture(RuntimeRecoverySyntheticState.load(state));
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void freshObjectsConfirmLostCommitWithoutRepeatingAnyEffect(final DataExportTemplate template)
            throws Exception {
        final var old = new LocalRuntimeIntegrationTest.Fixture();
        old.jdbc.loseApplyResponse = true;
        final var work = old.work(template, "aa-work");
        assertEquals(
                RuntimeExecutionResult.Status.RECOVERY_REQUIRED,
                old.dispatch(work).result(work.id).status());
        final var fresh = reloaded(old);
        final var reconstructed =
                new LocalRuntimeIntegrationTest.Work(fresh, template, "aa-work", work.execution);
        fresh.cancellation.cancel();
        fresh.jdbc.recovery.obsoletePolicy = true;
        final var result = recover(fresh, fresh.cancellation, reconstructed);
        assertEquals(Reason.PUBLISHED, result.snapshot(reconstructed.id).reason());
        assertEquals(1, result.dispatch().outcome().publishedEntities());
        assertEquals(0, reconstructed.fetches.get());
        assertEquals(1, fresh.jdbc.attempts.get(work.execution.toString()).applies);
        assertEquals(0, fresh.jdbc.batches);
        assertEquals(0, fresh.jdbc.heartbeatCount);
        assertEquals(1, fresh.jdbc.operations.size());
        assertEquals(0, fresh.jdbc.openConnections);
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void resumesSealedTraversalAcrossEveryLostAcknowledgement(final DataExportTemplate template)
            throws Exception {
        for (final String phase : Arrays.asList("seal", "transition", "prepare")) {
            final var old = new LocalRuntimeIntegrationTest.Fixture();
            old.jdbc.recovery.loseSealResponse = phase.equals("seal");
            old.jdbc.loseTransitionResponse = phase.equals("transition");
            old.jdbc.losePrepareResponse = phase.equals("prepare");
            final var work = old.work(template, "aa-work");
            assertEquals(
                    RuntimeExecutionResult.Status.RECOVERY_REQUIRED,
                    old.dispatch(work).result(work.id).status(),
                    phase);
            final var fresh = reloaded(old);
            final var next =
                    new LocalRuntimeIntegrationTest.Work(
                            fresh, template, "aa-work", work.execution);
            final var result = recover(fresh, CancellationToken.none(), next);
            assertEquals(Reason.PUBLISHED, result.snapshot(next.id).reason(), phase);
            assertEquals(0, next.fetches.get());
            assertEquals(1, fresh.jdbc.attempts.get(work.execution.toString()).applies);
            assertFalse(
                    fresh.jdbc.operations.contains(
                            "ctl.usp_control_plane_recover_stale_executions"));
            assertFalse(fresh.jdbc.operations.contains("ctl.usp_control_plane_start_execution"));
            assertEquals(0, fresh.jdbc.openConnections);
        }
    }

    @Test
    void lostStartIsKnownInProgressAndNeverRestartsSource() throws Exception {
        final var old = new LocalRuntimeIntegrationTest.Fixture();
        old.jdbc.loseStartResponse = true;
        final var work = old.work(DataExportTemplate.COLETAS, "aa-work");
        old.dispatch(work);
        final var fresh = reloaded(old);
        final var next =
                new LocalRuntimeIntegrationTest.Work(
                        fresh, DataExportTemplate.COLETAS, "aa-work", work.execution);
        assertEquals(
                Reason.IN_PROGRESS,
                recover(fresh, CancellationToken.none(), next).snapshot(next.id).reason());
        assertEquals(0, next.fetches.get());
    }

    @Test
    void absenceAndUnavailableAreDifferent() {
        final var fresh = new LocalRuntimeIntegrationTest.Fixture();
        final var work = fresh.work(DataExportTemplate.FRETES, "aa-work");
        assertEquals(
                Reason.NOT_FOUND,
                recover(fresh, CancellationToken.none(), work).snapshot(work.id).reason());
        fresh.jdbc.recovery.failRead = true;
        final var result = recover(fresh, CancellationToken.none(), work);
        assertEquals(Reason.UNAVAILABLE, result.snapshot(work.id).reason());
        assertTrue(
                result.failureCause(work.id).orElseThrow().getCause()
                        instanceof java.sql.SQLException);
        assertFalse(result.toString().contains("synthetic"));
        assertEquals(0, fresh.jdbc.openConnections);
    }

    @Test
    void sealCannotBeBornFromFailedAuditDriftOrIncompleteStaging() {
        for (final String failure : Arrays.asList("audit", "drift", "batch", "seal")) {
            final var fixture = new LocalRuntimeIntegrationTest.Fixture();
            final var work = fixture.work(DataExportTemplate.COLETAS, "aa-work");
            if (failure.equals("audit")) {
                fixture.jdbc.failOperation = "ctl.usp_control_plane_record_page";
            }
            work.drift = failure.equals("drift");
            fixture.jdbc.failBatch = failure.equals("batch") ? 2 : 0;
            fixture.jdbc.recovery.failSeal = failure.equals("seal");
            fixture.dispatch(work);
            assertNull(fixture.jdbc.attempts.get(work.execution.toString()).seal, failure);
            assertEquals(0, fixture.jdbc.attempts.get(work.execution.toString()).applies);
        }
    }

    @Test
    void rejectsTamperingOfEveryImmutableBindingAndReceipt() throws Exception {
        for (final int field : new int[] {1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15}) {
            final var old = new LocalRuntimeIntegrationTest.Fixture();
            old.jdbc.losePrepareResponse = true;
            final var work = old.work(DataExportTemplate.FRETES, "aa-work");
            old.dispatch(work);
            final var fresh = reloaded(old);
            final var attempt = fresh.jdbc.attempts.get(work.execution.toString());
            final Object previous = attempt.start.get(field);
            final Object changed =
                    previous instanceof Timestamp t
                            ? Timestamp.from(t.toInstant().plusSeconds(1))
                            : field == 1 || field == 2
                                    ? UUID.randomUUID().toString()
                                    : field == 12 || field == 14
                                            ? "f".repeat(64)
                                            : previous + "-changed";
            attempt.start.put(field, changed);
            final var next =
                    new LocalRuntimeIntegrationTest.Work(
                            fresh, DataExportTemplate.FRETES, "aa-work", work.execution);
            final var result = recover(fresh, CancellationToken.none(), next);
            assertNotEquals(Reason.PUBLISHED, result.snapshot(next.id).reason(), "field " + field);
            assertEquals(0, attempt.applies);
            assertEquals(0, next.fetches.get());
        }
        for (final String field :
                Arrays.asList("candidate_rows", "execution_id", "published_at_utc")) {
            final var old = new LocalRuntimeIntegrationTest.Fixture();
            final var work = old.work(DataExportTemplate.COLETAS, "aa-work");
            old.dispatch(work);
            final var fresh = reloaded(old);
            final var a = fresh.jdbc.attempts.get(work.execution.toString());
            a.publication.put(
                    field,
                    field.equals("candidate_rows")
                            ? -1L
                            : field.equals("execution_id")
                                    ? UUID.randomUUID().toString()
                                    : Timestamp.from(
                                            LocalRuntimeIntegrationTest.NOW.minusSeconds(1)));
            final var next =
                    new LocalRuntimeIntegrationTest.Work(
                            fresh, DataExportTemplate.COLETAS, "aa-work", work.execution);
            assertNotEquals(
                    Reason.PUBLISHED,
                    recover(fresh, CancellationToken.none(), next).snapshot(next.id).reason());
            assertEquals(1, a.applies);
        }
    }

    @Test
    void matrixRefusesPartialTerminalLeaseDqAndContradiction() throws Exception {
        for (final String variation :
                Arrays.asList(
                        "partial",
                        "missing",
                        "lease",
                        "failed",
                        "cancelled",
                        "blocked",
                        "dq-failed",
                        "dq-obsolete",
                        "contradiction",
                        "seal-tamper",
                        "policy-tamper")) {
            final var old = new LocalRuntimeIntegrationTest.Fixture();
            old.jdbc.losePrepareResponse = true;
            final var work = old.work(DataExportTemplate.COLETAS, "aa-work");
            old.dispatch(work);
            final var fresh = reloaded(old);
            final var a = fresh.jdbc.attempts.get(work.execution.toString());
            final Reason expected =
                    switch (variation) {
                        case "partial" -> {
                            a.seal = null;
                            a.state = ExecutionState.EXTRACTING;
                            yield Reason.PARTIAL_EXTRACTION;
                        }
                        case "missing" -> {
                            a.seal = null;
                            yield Reason.EVIDENCE_MISSING;
                        }
                        case "lease" -> {
                            fresh.jdbc.loseLease = true;
                            yield Reason.LEASE_LOST;
                        }
                        case "failed" -> {
                            a.state = ExecutionState.FAILED;
                            yield Reason.TERMINAL;
                        }
                        case "cancelled" -> {
                            a.state = ExecutionState.CANCELLED;
                            yield Reason.TERMINAL;
                        }
                        case "blocked" -> {
                            a.state = ExecutionState.BLOCKED;
                            yield Reason.TERMINAL;
                        }
                        case "dq-failed" -> {
                            fresh.jdbc.failQuality = true;
                            fresh.jdbc.execute(
                                    "recon.usp_evaluate_execution_data_quality",
                                    java.util.Map.of(
                                            1,
                                            work.execution.toString(),
                                            2,
                                            LocalRuntimeIntegrationTest.POLICY.version(),
                                            3,
                                            LocalRuntimeIntegrationTest.POLICY.sha256()));
                            yield Reason.DQ_FAILED;
                        }
                        case "dq-obsolete" -> {
                            fresh.jdbc.recovery.obsoletePolicy = true;
                            yield Reason.DQ_OBSOLETE;
                        }
                        case "seal-tamper" -> {
                            a.sealHash = "0".repeat(64);
                            yield Reason.INCONSISTENT;
                        }
                        case "policy-tamper" -> {
                            a.seal.put(5, "another-policy");
                            a.sealHash = "0".repeat(64);
                            yield Reason.INCONSISTENT;
                        }
                        default -> {
                            a.state = ExecutionState.RECONCILED;
                            yield Reason.INCONSISTENT;
                        }
                    };
            final var next =
                    new LocalRuntimeIntegrationTest.Work(
                            fresh, DataExportTemplate.COLETAS, "aa-work", work.execution);
            final var result = recover(fresh, CancellationToken.none(), next);
            assertEquals(expected, result.snapshot(next.id).reason(), variation);
            assertEquals(0, a.applies);
            assertEquals(0, next.fetches.get());
        }
    }

    @Test
    void changeAfterReadIsRevalidatedAndNeverTakesLease() throws Exception {
        final var old = new LocalRuntimeIntegrationTest.Fixture();
        old.jdbc.losePrepareResponse = true;
        final var work = old.work(DataExportTemplate.COLETAS, "aa-work");
        old.dispatch(work);
        final var fresh = reloaded(old);
        fresh.jdbc.recovery.beforeResume = () -> fresh.jdbc.loseLease = true;
        final var next =
                new LocalRuntimeIntegrationTest.Work(
                        fresh, DataExportTemplate.COLETAS, "aa-work", work.execution);
        final var result = recover(fresh, CancellationToken.none(), next);
        assertEquals(Reason.LEASE_LOST, result.snapshot(next.id).reason());
        assertTrue(result.failureCause(next.id).isPresent());
        assertEquals(0, fresh.jdbc.attempts.get(work.execution.toString()).applies);
        assertEquals(0, fresh.jdbc.heartbeatCount);
    }

    @Test
    void cancellationDuringSqlCallsStatementCancelAndPreservesUncertainty() throws Exception {
        final var old = new LocalRuntimeIntegrationTest.Fixture();
        old.jdbc.losePrepareResponse = true;
        final var work = old.work(DataExportTemplate.FRETES, "aa-work");
        old.dispatch(work);
        final var fresh = reloaded(old);
        fresh.jdbc.recovery.delayResume = true;
        fresh.jdbc.recovery.beforeResume = fresh.cancellation::cancel;
        final var next =
                new LocalRuntimeIntegrationTest.Work(
                        fresh, DataExportTemplate.FRETES, "aa-work", work.execution);
        final var result = recover(fresh, fresh.cancellation, next);
        assertTrue(fresh.jdbc.recovery.cancelCalls > 0);
        assertNotEquals(Reason.PUBLISHED, result.snapshot(next.id).reason());
        assertEquals(0, fresh.jdbc.attempts.get(work.execution.toString()).applies);
        assertEquals(0, fresh.jdbc.openConnections);
        assertTrue(result.failureCause(next.id).isPresent());
    }

    @Test
    void preservesIndependentPublicationAndTerminalBlockedDependency() throws Exception {
        final var old = new LocalRuntimeIntegrationTest.Fixture();
        final var independent = old.work(DataExportTemplate.FRETES, "aa-independent");
        final var uncertain = old.work(DataExportTemplate.COLETAS, "bb-uncertain");
        final var blocked = old.work(DataExportTemplate.FRETES, "cc-dependent", uncertain.id);
        old.jdbc.afterApply = () -> old.jdbc.losePrepareResponse = true;
        old.dispatch(independent, uncertain, blocked);
        final var fresh = reloaded(old);
        final var first =
                new LocalRuntimeIntegrationTest.Work(
                        fresh, DataExportTemplate.FRETES, "aa-independent", independent.execution);
        final var second =
                new LocalRuntimeIntegrationTest.Work(
                        fresh, DataExportTemplate.COLETAS, "bb-uncertain", uncertain.execution);
        final var third =
                new LocalRuntimeIntegrationTest.Work(
                        fresh,
                        DataExportTemplate.FRETES,
                        "cc-dependent",
                        blocked.execution,
                        second.id);
        final var result = recover(fresh, CancellationToken.none(), first, second, third);
        assertEquals(2, result.dispatch().outcome().publishedEntities());
        assertEquals(1, result.dispatch().outcome().blockedEntities());
        assertEquals(Reason.TERMINAL, result.snapshot(third.id).reason());
        assertEquals(0, first.fetches.get() + second.fetches.get() + third.fetches.get());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES"})
    void distinctJavaProcessesRecoverPersistedSummaries(final DataExportTemplate template)
            throws Exception {
        for (final String phase : Arrays.asList("apply", "prepare")) {
            final Path state = temporary.resolve(template + "-" + phase + ".properties");
            runProcess("write", template.name(), phase, state.toString());
            runProcess("read", template.name(), phase, state.toString());
            final var persisted = RuntimeRecoverySyntheticState.load(state);
            assertEquals(1, persisted.attempts.values().iterator().next().applies);
            assertEquals(
                    ExecutionState.PUBLISHED, persisted.attempts.values().iterator().next().state);
        }
    }

    @Test
    void jdbcDeadlinesAreExplicitAndUnsupportedLimitsFailClosed() {
        final var fixture = new LocalRuntimeIntegrationTest.Fixture();
        final var work = fixture.work(DataExportTemplate.COLETAS, "aa-work");
        assertEquals(
                Reason.NOT_FOUND,
                recover(fixture, CancellationToken.none(), work).snapshot(work.id).reason());
        assertEquals(5, fixture.jdbc.recovery.queryTimeout);
        assertEquals(10000, fixture.jdbc.recovery.networkTimeout);
        assertTrue(fixture.jdbc.recovery.statementCloses > 0);
        fixture.jdbc.recovery.networkUnsupported = true;
        assertEquals(
                Reason.UNAVAILABLE,
                recover(fixture, CancellationToken.none(), work).snapshot(work.id).reason());
        assertEquals(0, fixture.jdbc.openConnections);
        fixture.jdbc.recovery.networkUnsupported = false;
        fixture.jdbc.recovery.missingRow = true;
        assertEquals(
                Reason.INCONSISTENT,
                recover(fixture, CancellationToken.none(), work).snapshot(work.id).reason());
        fixture.jdbc.recovery.loginTimeout = 0;
        assertThrows(IllegalArgumentException.class, fixture::recoveryPort);
        fixture.jdbc.recovery.loginTimeout = 31;
        assertThrows(IllegalArgumentException.class, fixture::recoveryPort);
        fixture.jdbc.recovery.loginTimeout = 5;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new br.com.esl.etl.v2.plataforma.persistencia.controle
                                .JdbcSqlServerRuntimeRecovery(
                                fixture.jdbc.dataSource(),
                                java.time.Duration.ZERO,
                                java.time.Duration.ofSeconds(10)));
    }

    @Test
    void historicalReceiptNeedsNoRetrospectiveContractSeal() throws Exception {
        final var old = new LocalRuntimeIntegrationTest.Fixture();
        final var work = old.work(DataExportTemplate.COLETAS, "aa-work");
        old.jdbc.loseApplyResponse = true;
        new RuntimeDispatcher(
                        old.control, old.clock, new RuntimeDispatchBinding(work.id, work.handler))
                .dispatch(old.plan(work), CancellationToken.none());
        assertNull(old.jdbc.attempts.get(work.execution.toString()).seal);
        final var fresh = reloaded(old);
        final var next =
                new LocalRuntimeIntegrationTest.Work(
                        fresh, DataExportTemplate.COLETAS, "aa-work", work.execution);
        assertEquals(
                Reason.PUBLISHED,
                recover(fresh, CancellationToken.none(), next).snapshot(next.id).reason());
        assertEquals(1, fresh.jdbc.operations.size());
    }

    @Test
    void cancellationBeforeContinuationDoesNotWriteTerminalOrEraseSeal() throws Exception {
        final var old = new LocalRuntimeIntegrationTest.Fixture();
        old.jdbc.losePrepareResponse = true;
        final var work = old.work(DataExportTemplate.FRETES, "aa-work");
        old.dispatch(work);
        final var fresh = reloaded(old);
        final var next =
                new LocalRuntimeIntegrationTest.Work(
                        fresh, DataExportTemplate.FRETES, "aa-work", work.execution);
        fresh.cancellation.cancel();
        assertEquals(
                Reason.CANCELLED,
                recover(fresh, fresh.cancellation, next).snapshot(next.id).reason());
        assertEquals(
                ExecutionState.PROMOTED, fresh.jdbc.attempts.get(work.execution.toString()).state);
        assertEquals(1, fresh.jdbc.operations.size());
    }

    @Test
    void lostResumeAckAndCancellationAfterCommitStillConfirmPublication() throws Exception {
        final var old = new LocalRuntimeIntegrationTest.Fixture();
        old.jdbc.losePrepareResponse = true;
        final var work = old.work(DataExportTemplate.COLETAS, "aa-work");
        old.dispatch(work);
        final var fresh = reloaded(old);
        final var next =
                new LocalRuntimeIntegrationTest.Work(
                        fresh, DataExportTemplate.COLETAS, "aa-work", work.execution);
        fresh.jdbc.loseApplyResponse = true;
        fresh.jdbc.afterApply = fresh.cancellation::cancel;
        final var result = recover(fresh, fresh.cancellation, next);
        assertEquals(Reason.PUBLISHED, result.snapshot(next.id).reason());
        assertTrue(result.failureCause(next.id).isPresent());
        assertEquals(1, fresh.jdbc.attempts.get(work.execution.toString()).applies);
        assertEquals(0, next.fetches.get());
    }

    @Test
    void unknownCommitWithUnavailableReadRemainsUncertain() throws Exception {
        final var old = new LocalRuntimeIntegrationTest.Fixture();
        old.jdbc.losePrepareResponse = true;
        final var work = old.work(DataExportTemplate.COLETAS, "aa-work");
        old.dispatch(work);
        final var fresh = reloaded(old);
        final var next =
                new LocalRuntimeIntegrationTest.Work(
                        fresh, DataExportTemplate.COLETAS, "aa-work", work.execution);
        fresh.jdbc.loseApplyResponse = true;
        fresh.jdbc.afterApply = () -> fresh.jdbc.recovery.failRead = true;
        final var result = recover(fresh, CancellationToken.none(), next);
        assertEquals(Reason.UNAVAILABLE, result.snapshot(next.id).reason());
        assertEquals(
                RuntimeExecutionResult.Status.RECOVERY_REQUIRED,
                result.dispatch().result(next.id).status());
        assertEquals(1, result.failureCause(next.id).orElseThrow().getSuppressed().length);
        assertEquals(
                ExecutionState.PUBLISHED, fresh.jdbc.attempts.get(work.execution.toString()).state);
    }

    @Test
    void newAttemptAndExplicitReplayKeepIndependentReceiptsAndFrontiers() throws Exception {
        final var old = new LocalRuntimeIntegrationTest.Fixture();
        final var failed = old.work(DataExportTemplate.COLETAS, "aa-work");
        old.jdbc.failBatch = 2;
        old.dispatch(failed);
        final var success = old.work(DataExportTemplate.COLETAS, "aa-work");
        old.jdbc.loseApplyResponse = true;
        old.dispatch(success);
        final var fresh = reloaded(old);
        final var next =
                new LocalRuntimeIntegrationTest.Work(
                        fresh, DataExportTemplate.COLETAS, "aa-work", success.execution);
        assertEquals(
                Reason.PUBLISHED,
                recover(fresh, CancellationToken.none(), next).snapshot(next.id).reason());
        assertEquals(
                ExecutionState.FAILED, fresh.jdbc.attempts.get(failed.execution.toString()).state);
        fresh.mode = br.com.esl.etl.v2.plataforma.controle.ExecutionMode.REPLAY;
        fresh.replay = java.util.Optional.of(success.execution);
        final var replay = fresh.work(DataExportTemplate.COLETAS, "aa-replay");
        fresh.jdbc.loseApplyResponse = true;
        fresh.dispatch(replay);
        final var replayFresh = reloaded(fresh);
        replayFresh.mode = fresh.mode;
        replayFresh.replay = fresh.replay;
        final var replayWork =
                new LocalRuntimeIntegrationTest.Work(
                        replayFresh, DataExportTemplate.COLETAS, "aa-replay", replay.execution);
        final var result = recover(replayFresh, CancellationToken.none(), replayWork);
        assertEquals(Reason.PUBLISHED, result.snapshot(replayWork.id).reason());
        assertTrue(
                result.snapshot(replayWork.id)
                        .publication()
                        .orElseThrow()
                        .incrementalFrontierAfter()
                        .isEmpty());
        replayFresh
                .jdbc
                .attempts
                .get(replay.execution.toString())
                .start
                .put(16, UUID.randomUUID().toString());
        assertEquals(
                Reason.INCONSISTENT,
                recover(replayFresh, CancellationToken.none(), replayWork)
                        .snapshot(replayWork.id)
                        .reason());
        assertEquals(1, replayFresh.jdbc.attempts.get(success.execution.toString()).applies);
    }

    @Test
    void returnsOriginalTypedColetaCountsInsteadOfGenericOrRecomputedCounts() throws Exception {
        final var old = new LocalRuntimeIntegrationTest.Fixture();
        old.jdbc.recovery.coletaTypedNoop = true;
        old.jdbc.loseApplyResponse = true;
        final var work = old.work(DataExportTemplate.COLETAS, "aa-work");
        old.dispatch(work);
        final var fresh = reloaded(old);
        final var next =
                new LocalRuntimeIntegrationTest.Work(
                        fresh, DataExportTemplate.COLETAS, "aa-work", work.execution);
        final var result = recover(fresh, CancellationToken.none(), next);
        final var receipt = result.snapshot(next.id).publication().orElseThrow();
        assertEquals(0, receipt.insertedRows());
        assertEquals(251, receipt.noopRows());
        assertEquals("UTC", fresh.jdbc.recovery.timestampZone);
        final var persisted = fresh.jdbc.attempts.get(work.execution.toString());
        assertEquals(251L, persisted.genericPublication.get("inserted_rows"));
        assertEquals(1, persisted.applies);
        assertEquals(1, fresh.jdbc.operations.size());
        persisted.publication.put("inserted_rows", 1L);
        persisted.publication.put("noop_rows", 250L);
        assertEquals(
                Reason.INCONSISTENT,
                recover(fresh, CancellationToken.none(), next).snapshot(next.id).reason());
    }

    @Test
    void additiveColetaWrapperPreservesBusinessBodyAndPersistsReceiptBeforeCommit()
            throws Exception {
        final String old =
                java.nio.file.Files.readString(
                        Path.of("database/migrations/V010__create_coletas_shadow_vertical.sql"));
        final String added =
                java.nio.file.Files.readString(
                        Path.of("database/migrations/V015__create_runtime_durable_recovery.sql"));
        final String header = "CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_coletas";
        final String oldBody =
                old.substring(old.indexOf(header), old.indexOf("\nGO", old.indexOf(header)));
        final String newBody =
                added.substring(
                        added.indexOf(header), added.indexOf("\nGO", added.indexOf(header)));
        final String businessBody =
                newBody.replaceAll(
                        "(?s)\\s*-- P02R BEGIN (?:LOCK|READBACK|RECEIPT).*?-- P02R END (?:LOCK|READBACK|RECEIPT)\\s*",
                        "\n");
        assertEquals(oldBody.replaceAll("\\s+", ""), businessBody.replaceAll("\\s+", ""));
        assertTrue(
                newBody.indexOf("INSERT ctl.runtime_coleta_publication_receipt")
                        < newBody.lastIndexOf("COMMIT TRANSACTION"));
        assertTrue(
                newBody.indexOf("-- P02R BEGIN READBACK") < newBody.indexOf("DECLARE @plan TABLE"));
        assertTrue(added.contains("COALESCE(typed.inserted_rows,result.inserted_rows)"));
        assertTrue(
                added.contains(
                        "r.receipt_hash=ctl.fn_runtime_coleta_publication_hash(@execution_id)"));
    }

    private static void runProcess(final String... arguments) throws Exception {
        final var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
        command.add("-cp");
        command.add(
                System.getProperty(
                        "surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(RuntimeRecoveryProcessProbe.class.getName());
        command.addAll(Arrays.asList(arguments));
        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "owned child deadline");
            assertEquals(
                    0,
                    process.exitValue(),
                    new String(
                            process.getInputStream().readNBytes(8192),
                            java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }
}
