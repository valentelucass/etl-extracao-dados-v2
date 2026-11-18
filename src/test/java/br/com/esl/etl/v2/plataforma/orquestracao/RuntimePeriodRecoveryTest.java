package br.com.esl.etl.v2.plataforma.orquestracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuntimePeriodRecoveryTest {
    private static final String NS = "a".repeat(64);
    private static final LocalDate FIRST = LocalDate.of(2024, 2, 20);
    private static final Instant OBSERVED = Instant.parse("2024-03-12T15:00:00Z");

    private static RuntimeTemporalPolicy policy() {
        return policy(ExecutionMode.BACKFILL, Duration.ZERO, List.of());
    }

    private static RuntimeTemporalPolicy policy(
            final ExecutionMode mode,
            final Duration lookback,
            final List<RuntimeTemporalPolicy.Blackout> blackouts) {
        return new RuntimeTemporalPolicy(
                "recovery-test-v1",
                ZoneId.of("America/Sao_Paulo"),
                mode,
                RuntimeWindowStrategy.INTERVAL,
                RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                LocalTime.MIDNIGHT,
                lookback,
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                Duration.ofMinutes(30),
                2,
                4,
                8,
                1,
                blackouts);
    }

    private static RuntimeTemporalPlanner.Window window(final int day) {
        return new RuntimeTemporalPlanner()
                .plan(policy(), FIRST.plusDays(day), OBSERVED)
                .windows()
                .get(0);
    }

    private static RuntimeTemporalStore.Gap gap(
            final String namespace,
            final RuntimeTemporalPlanner.Window window,
            final String state) {
        return new RuntimeTemporalStore.Gap(
                UUID.nameUUIDFromBytes(
                        (namespace + "|" + window.partitionStart() + "|" + window.endExclusive())
                                .getBytes(StandardCharsets.UTF_8)),
                window.partitionStart(),
                window.endExclusive(),
                state);
    }

    @Test
    void recoversTwentyOneDaysAcrossLeapDayInBoundedPassesAfterRestart() {
        final var store = new Store();
        LocalDate cursor = FIRST;
        RuntimePeriodRecovery.Result result;
        int passes = 0;
        do {
            result =
                    new RuntimePeriodRecovery(store)
                            .recover(
                                    NS,
                                    policy(),
                                    cursor,
                                    OBSERVED,
                                    (pending, window) -> store.publish(NS, pending.occurrence()));
            assertTrue(result.dispatched() <= 4);
            cursor = result.contiguousEnd().atZone(policy().zone()).toLocalDate();
            passes++;
            assertTrue(passes < 10);
        } while (result.backlogRemaining());
        assertEquals(LocalDate.of(2024, 3, 12), cursor);
        assertEquals(21, store.persisted.size());
        assertEquals(21, store.extracted.size());
        assertEquals(6, passes);
        assertTrue(
                store.extracted.stream()
                        .anyMatch(
                                g ->
                                        g.start()
                                                .equals(
                                                        policy().resolve(
                                                                        LocalDate.of(
                                                                                2024, 2, 29)))));
    }

    @Test
    void repeatedOriginalPeriodDoesNotExtractOrPersistPublishedDaysAgain() {
        final var store = new Store();
        final RuntimePeriodRecovery.Executor executor = (p, w) -> store.publish(NS, p.occurrence());
        final var first =
                new RuntimePeriodRecovery(store).recover(NS, policy(), FIRST, OBSERVED, executor);
        final var second =
                new RuntimePeriodRecovery(store).recover(NS, policy(), FIRST, OBSERVED, executor);
        assertEquals(first.contiguousEnd(), second.contiguousEnd());
        assertEquals(0, second.dispatched());
        assertEquals(4, store.persisted.size());
        assertEquals(4, store.extracted.size());
        assertTrue(second.backlogRemaining());
    }

    @Test
    void missingDayBeforeLaterPublishedDayIsExtractedAndFrontierNeverJumpsIt() {
        final var store = new Store();
        store.put(NS, gap(NS, window(1), "PUBLISHED"));
        final var result =
                new RuntimePeriodRecovery(store)
                        .recover(NS, policy(), FIRST, OBSERVED, (pending, window) -> {});
        assertEquals(policy().resolve(FIRST), result.contiguousEnd());
        assertEquals(1, result.dispatched());
        assertEquals(1, result.pending().size());
        assertEquals(1, store.persisted.size());
        assertEquals(window(0).partitionStart(), result.pending().get(0).occurrence().start());
        assertTrue(result.backlogRemaining());
    }

    @Test
    void crashAfterPublicationResumesFromDurableSummaryWithoutExtractingThePrefix() {
        final var store = new Store();
        assertThrows(
                IllegalStateException.class,
                () ->
                        new RuntimePeriodRecovery(store)
                                .recover(
                                        NS,
                                        policy(),
                                        FIRST,
                                        OBSERVED,
                                        (pending, window) -> {
                                            store.publish(NS, pending.occurrence());
                                            throw new IllegalStateException(
                                                    "simulated-process-loss");
                                        }));
        final var result =
                new RuntimePeriodRecovery(store)
                        .recover(
                                NS,
                                policy(),
                                FIRST,
                                OBSERVED,
                                (pending, window) -> store.publish(NS, pending.occurrence()));
        assertEquals(window(3).endExclusive(), result.contiguousEnd());
        assertEquals(3, result.dispatched());
        assertEquals(4, store.extracted.size());
        assertEquals(4, store.persisted.size());
    }

    @Test
    void crashAfterPlanCommitIsReconciledBeforeAnyRepeatedPlanWrite() {
        final var store = new Store();
        store.losePlanReceipt = true;
        assertThrows(
                IllegalStateException.class,
                () ->
                        new RuntimePeriodRecovery(store)
                                .recover(NS, policy(), FIRST, OBSERVED, (p, w) -> {}));
        store.losePlanReceipt = false;
        new RuntimePeriodRecovery(store)
                .recover(
                        NS, policy(), FIRST, OBSERVED, (p, w) -> store.publish(NS, p.occurrence()));
        assertEquals(4, store.persisted.size());
        assertEquals(4, store.extracted.size());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"FAILED", "CANCELLED", "BLOCKED", "SKIPPED", "NOT_APPLICABLE", "DEGRADED"})
    void terminalFailuresRequireExplicitReplayAndStopLaterSourceCalls(final String state) {
        final var store = new Store();
        store.put(NS, gap(NS, window(0), state));
        store.put(NS, gap(NS, window(1), "PUBLISHED"));
        final var result =
                new RuntimePeriodRecovery(store)
                        .recover(
                                NS,
                                policy(),
                                FIRST,
                                OBSERVED,
                                (p, w) -> {
                                    throw new AssertionError("must not dispatch");
                                });
        assertEquals(policy().resolve(FIRST), result.contiguousEnd());
        assertEquals(0, result.dispatched());
        assertEquals(
                RuntimeTemporalCoordinator.NextAction.EXPLICIT_REPLAY_REQUIRED,
                result.pending().get(0).action());
        assertTrue(store.persisted.isEmpty());
    }

    @Test
    void inFlightOccurrenceUsesRecoveryAndNeedsPublishedReadback() {
        final var store = new Store();
        store.put(NS, gap(NS, window(0), "STAGED"));
        final var result =
                new RuntimePeriodRecovery(store)
                        .recover(
                                NS,
                                policy(),
                                FIRST,
                                OBSERVED,
                                (p, w) ->
                                        assertEquals(
                                                RuntimeTemporalCoordinator.NextAction
                                                        .RECOVER_ORIGINAL,
                                                p.action()));
        assertEquals(policy().resolve(FIRST), result.contiguousEnd());
        assertTrue(result.backlogRemaining());
        assertEquals("STAGED", result.pending().get(0).occurrence().state());
        assertTrue(store.persisted.isEmpty());
    }

    @Test
    void stopsImmediatelyWhenSourceFailsInTheMiddleAndRetainsPublishedPrefix() {
        final var store = new Store();
        final var result =
                new RuntimePeriodRecovery(store)
                        .recover(
                                NS,
                                policy(),
                                FIRST,
                                OBSERVED,
                                (p, w) -> {
                                    if (w.partitionStart().equals(window(1).partitionStart())) {
                                        store.put(NS, gap(NS, w, "FAILED"));
                                    } else {
                                        store.publish(NS, p.occurrence());
                                    }
                                });
        assertEquals(window(0).endExclusive(), result.contiguousEnd());
        assertEquals(2, result.dispatched());
        assertEquals(2, store.persisted.size());
        assertEquals(
                RuntimeTemporalCoordinator.NextAction.EXPLICIT_REPLAY_REQUIRED,
                result.pending().get(0).action());
    }

    @Test
    void oneEntityCannotCompleteAnotherEntityOrTenantNamespace() {
        final var store = new Store();
        final String other = "b".repeat(64);
        new RuntimePeriodRecovery(store)
                .recover(
                        NS, policy(), FIRST, OBSERVED, (p, w) -> store.publish(NS, p.occurrence()));
        final var result =
                new RuntimePeriodRecovery(store)
                        .recover(other, policy(), FIRST, OBSERVED, (p, w) -> {});
        assertEquals(policy().resolve(FIRST), result.contiguousEnd());
        assertEquals(1, result.dispatched());
        assertEquals(5, store.persisted.size());
    }

    @Test
    void openDayAndStabilizationAreNotReportedAsCompletedHistory() {
        final var store = new Store();
        final Instant boundary = policy().resolve(FIRST.plusDays(1));
        final var before =
                new RuntimePeriodRecovery(store)
                        .recover(
                                NS,
                                policy(),
                                FIRST,
                                boundary.plusSeconds(59),
                                (p, w) -> store.publish(NS, p.occurrence()));
        assertEquals(0, before.dispatched());
        assertFalse(before.backlogRemaining());
        assertEquals(policy().resolve(FIRST), before.contiguousEnd());
        final var due =
                new RuntimePeriodRecovery(store)
                        .recover(
                                NS,
                                policy(),
                                FIRST,
                                boundary.plusSeconds(60),
                                (p, w) -> store.publish(NS, p.occurrence()));
        assertEquals(1, due.dispatched());
        assertEquals(boundary, due.contiguousEnd());
        assertFalse(due.backlogRemaining());
    }

    @Test
    void blackoutStopsCoverageBeforeTheBlockedDayWithoutSkippingIt() {
        final var store = new Store();
        final var blocked =
                policy(
                        ExecutionMode.BACKFILL,
                        Duration.ZERO,
                        List.of(
                                new RuntimeTemporalPolicy.Blackout(
                                        FIRST.plusDays(1), FIRST.plusDays(2))));
        final var result =
                new RuntimePeriodRecovery(store)
                        .recover(
                                NS,
                                blocked,
                                FIRST,
                                OBSERVED,
                                (p, w) -> store.publish(NS, p.occurrence()));
        assertEquals(window(0).endExclusive(), result.contiguousEnd());
        assertEquals(1, result.dispatched());
        assertTrue(result.blockedByBlackout());
        assertTrue(result.backlogRemaining());
    }

    @Test
    void refusesUpdateFilteredRecoveryAndInvalidNamespaceBeforeAnyStoreAccess() {
        final var store = new Store();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimePeriodRecovery(store)
                                .recover(
                                        NS,
                                        policy(ExecutionMode.INCREMENTAL, Duration.ZERO, List.of()),
                                        FIRST,
                                        OBSERVED,
                                        (p, w) -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimePeriodRecovery(store)
                                .recover(
                                        NS,
                                        policy(
                                                ExecutionMode.BACKFILL,
                                                Duration.ofDays(7),
                                                List.of()),
                                        FIRST,
                                        OBSERVED,
                                        (p, w) -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimePeriodRecovery(store)
                                .recover("unsafe", policy(), FIRST, OBSERVED, (p, w) -> {}));
        assertEquals(0, store.reads);
    }

    @Test
    void refusesOverlapForeignExecutionAndMissingPublicationReadback() {
        for (int variant = 0; variant < 3; variant++) {
            final var store = new Store();
            final var original = gap(NS, window(0), "PUBLISHED");
            store.put(
                    NS,
                    new RuntimeTemporalStore.Gap(
                            variant == 0 ? UUID.randomUUID() : original.execution(),
                            original.start(),
                            variant == 1
                                    ? original.endExclusive().plusSeconds(1)
                                    : original.endExclusive(),
                            "PUBLISHED"));
            if (variant == 2) {
                store.overflow = true;
            }
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            new RuntimePeriodRecovery(store)
                                    .recover(NS, policy(), FIRST, OBSERVED, (p, w) -> {}));
            assertTrue(store.persisted.isEmpty());
        }
        final var store = new Store();
        assertThrows(
                IllegalStateException.class,
                () ->
                        new RuntimePeriodRecovery(store)
                                .recover(
                                        NS,
                                        policy(),
                                        FIRST,
                                        OBSERVED,
                                        (p, w) -> store.rows.get(NS).clear()));
    }

    @Test
    void unconfirmedPlanReceiptOrInvisiblePlanNeverDispatchesSource() {
        for (int variant = 0; variant < 2; variant++) {
            final var store = new Store();
            store.badPlanReceipt = variant == 0;
            store.invisiblePlan = variant == 1;
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            new RuntimePeriodRecovery(store)
                                    .recover(
                                            NS,
                                            policy(),
                                            FIRST,
                                            OBSERVED,
                                            (p, w) -> {
                                                throw new AssertionError(
                                                        "unconfirmed plan must not dispatch");
                                            }));
            assertEquals(1, store.persisted.size());
            assertEquals(0, store.extracted.size());
        }
    }

    private static final class Store implements RuntimeTemporalStore {
        final Map<String, Map<Instant, Gap>> rows = new HashMap<>();
        final Map<UUID, RuntimeTemporalPlanner.Window> persisted = new HashMap<>();
        final List<Gap> extracted = new ArrayList<>();
        boolean losePlanReceipt;
        boolean overflow;
        boolean badPlanReceipt;
        boolean invisiblePlan;
        int reads;

        void put(final String ns, final Gap gap) {
            rows.computeIfAbsent(ns, ignored -> new HashMap<>()).put(gap.start(), gap);
        }

        void publish(final String ns, final Gap gap) {
            extracted.add(gap);
            put(ns, new Gap(gap.execution(), gap.start(), gap.endExclusive(), "PUBLISHED"));
        }

        @Override
        public int persist(
                final UUID plan,
                final String ns,
                final RuntimeTemporalPolicy policy,
                final RuntimeTemporalPlanner.Result result) {
            assertEquals(1, result.windows().size());
            final var w = result.windows().get(0);
            assertEquals(null, persisted.putIfAbsent(plan, w));
            if (!invisiblePlan) {
                put(ns, gap(ns, w, "NOT_STARTED"));
            }
            if (losePlanReceipt) {
                throw new IllegalStateException("simulated-lost-commit-ack");
            }
            return badPlanReceipt ? 0 : 1;
        }

        @Override
        public List<Gap> readGapPage(final String ns, final int maximum, final Instant after) {
            reads++;
            if (overflow) {
                return java.util.Collections.nCopies(maximum + 1, gap(ns, window(0), "PUBLISHED"));
            }
            return rows.getOrDefault(ns, Map.of()).values().stream()
                    .filter(g -> g.endExclusive().isAfter(after))
                    .sorted(java.util.Comparator.comparing(Gap::start))
                    .limit(maximum)
                    .toList();
        }
    }
}
