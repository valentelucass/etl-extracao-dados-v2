package br.com.esl.etl.v2.plataforma.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ControlPlaneValueObjectsTest {

    private static final Instant START = Instant.parse("2026-08-30T00:00:00Z");
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void normalizesTheSemanticKeyAndRejectsAnInvalidHalfOpenInterval() {
        final ExecutionPartitionKey key =
                new ExecutionPartitionKey(
                        " LOCAL_SHADOW ",
                        " SOURCE ",
                        " TENANT ",
                        " ENTITY ",
                        ExecutionMode.INCREMENTAL,
                        START,
                        START.plusSeconds(60));

        assertEquals("LOCAL_SHADOW", key.environment());
        assertEquals("SOURCE", key.sourceInstance());
        assertEquals("TENANT", key.tenantScope());
        assertEquals("ENTITY", key.entity());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExecutionPartitionKey(
                                "env",
                                "source",
                                "tenant",
                                "entity",
                                ExecutionMode.INCREMENTAL,
                                START,
                                START));
    }

    @Test
    void requiresEachSemanticKeySegment() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExecutionPartitionKey(
                                " ",
                                "source",
                                "tenant",
                                "entity",
                                ExecutionMode.INCREMENTAL,
                                START,
                                START.plusSeconds(1)));
        assertThrows(
                NullPointerException.class,
                () ->
                        new ExecutionPartitionKey(
                                "env",
                                "source",
                                "tenant",
                                "entity",
                                null,
                                START,
                                START.plusSeconds(1)));
    }

    @Test
    void normalizesFingerprintsAndRejectsMalformedValues() {
        final ImmutableFingerprint fingerprint =
                new ImmutableFingerprint(" v1 ", FINGERPRINT.toUpperCase());

        assertEquals("v1", fingerprint.version());
        assertEquals(FINGERPRINT, fingerprint.sha256());
        assertThrows(
                IllegalArgumentException.class, () -> new ImmutableFingerprint("v1", "not-a-hash"));
        assertThrows(
                IllegalArgumentException.class, () -> new ImmutableFingerprint(" ", FINGERPRINT));
    }

    @Test
    void keepsTheSourceCatalogAndCycleSeparatedFromExecutionOccurrences() {
        final ControlPlaneSource source =
                new ControlPlaneSource(" SOURCE ", " DATA_EXPORT ", START);
        final ControlPlaneCycle cycle =
                new ControlPlaneCycle(
                        UUID.fromString("00000000-0000-0000-0000-000000000023"),
                        fingerprint(),
                        START);

        assertEquals("SOURCE", source.sourceInstance());
        assertEquals("DATA_EXPORT", source.sourceKind());
        assertEquals(FINGERPRINT, cycle.plan().sha256());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ControlPlaneSource(" ", "DATA_EXPORT", START));
        assertThrows(
                NullPointerException.class,
                () -> new ControlPlaneCycle(UUID.randomUUID(), null, START));
    }

    @Test
    void preservesCaseAndAccentInOpaqueSemanticLabelsAndRejectsAnOversizedSharedPrefix() {
        final ExecutionPartitionKey caseAndAccentKey =
                new ExecutionPartitionKey(
                        "Local",
                        "Source-KeyA",
                        "tenant-ação",
                        "Entity",
                        ExecutionMode.BACKFILL,
                        START,
                        START.plusSeconds(60));
        final ImmutableFingerprint version = new ImmutableFingerprint("Plan-Versão", FINGERPRINT);

        assertEquals("Local", caseAndAccentKey.environment());
        assertEquals("Source-KeyA", caseAndAccentKey.sourceInstance());
        assertEquals("tenant-ação", caseAndAccentKey.tenantScope());
        assertEquals("Entity", caseAndAccentKey.entity());
        assertEquals("Plan-Versão", version.version());

        final String maximumPrefix = "K".repeat(128);
        assertEquals(
                maximumPrefix,
                new ControlPlaneSource(maximumPrefix, "Kind", START).sourceInstance());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ControlPlaneSource(maximumPrefix + "X", "Kind", START));
    }

    @Test
    void trimsOnlyAsciiSpaceAndPreservesTabsInOpaqueSqlLabels() {
        final ExecutionPartitionKey tabbedPartition =
                partition("\tEnvironment\t", "\tSource\t", "\tTenant\t", "\tEntity\t");
        final ControlPlaneSource tabbedSource =
                new ControlPlaneSource("\tSource\t", "\tKind\t", START);
        final ImmutableFingerprint tabbedVersion =
                new ImmutableFingerprint("\tVersion\t", FINGERPRINT);
        final ControlPlaneStart tabbedStart = start("\tWindow\t", "\tIdempotency\t");
        final ControlPlaneCounts tabbedCounts =
                new ControlPlaneCounts(UUID.randomUUID(), "\tPhase\t", 0, 0, 0, 0, 0, 0, START);

        assertEquals("\tEnvironment\t", tabbedPartition.environment());
        assertEquals("\tSource\t", tabbedSource.sourceInstance());
        assertEquals("\tKind\t", tabbedSource.sourceKind());
        assertEquals("\tVersion\t", tabbedVersion.version());
        assertEquals("\tWindow\t", tabbedStart.windowStrategy());
        assertEquals("\tIdempotency\t", tabbedStart.idempotencyKey());
        assertEquals("\tPhase\t", tabbedCounts.phase());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneTransition(
                                UUID.randomUUID(),
                                ExecutionState.EXTRACTING,
                                ExecutionState.EXTRACTED,
                                "\tSTAGE_OK\t",
                                START));
    }

    @Test
    void validatesEveryBoundedControlPlaneStringBeforeWhitespaceNormalization() {
        final String maximumEnvironment = "E".repeat(32);
        final String maximumSource = "S".repeat(128);
        final String maximumTenant = "T".repeat(128);
        final String maximumEntity = "N".repeat(128);
        final String maximumKind = "K".repeat(64);
        final String maximumWindowStrategy = "W".repeat(64);
        final String maximumIdempotencyKey = "I".repeat(128);
        final String maximumPhase = "P".repeat(64);
        final String maximumReason = "R".repeat(64);
        final String maximumVersion = "V".repeat(128);

        final ExecutionPartitionKey maximumPartition =
                partition(maximumEnvironment, maximumSource, maximumTenant, maximumEntity);
        final ControlPlaneSource maximumCatalogSource =
                new ControlPlaneSource(maximumSource, maximumKind, START);
        final ImmutableFingerprint maximumFingerprint =
                new ImmutableFingerprint(maximumVersion, FINGERPRINT.toUpperCase());
        final ControlPlaneStart maximumStart = start(maximumWindowStrategy, maximumIdempotencyKey);
        final ControlPlaneCounts maximumCounts =
                new ControlPlaneCounts(UUID.randomUUID(), maximumPhase, 0, 0, 0, 0, 0, 0, START);
        final ControlPlaneTransition maximumTransition =
                new ControlPlaneTransition(
                        UUID.randomUUID(),
                        ExecutionState.EXTRACTING,
                        ExecutionState.EXTRACTED,
                        maximumReason,
                        START);

        assertEquals(maximumEnvironment, maximumPartition.environment());
        assertEquals(maximumSource, maximumCatalogSource.sourceInstance());
        assertEquals(maximumVersion, maximumFingerprint.version());
        assertEquals(maximumWindowStrategy, maximumStart.windowStrategy());
        assertEquals(maximumPhase, maximumCounts.phase());
        assertEquals(maximumReason, maximumTransition.reasonCode());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        partition(
                                maximumEnvironment + " ",
                                maximumSource,
                                maximumTenant,
                                maximumEntity));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        partition(
                                maximumEnvironment,
                                maximumSource + " ",
                                maximumTenant,
                                maximumEntity));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        partition(
                                maximumEnvironment,
                                maximumSource,
                                maximumTenant + " ",
                                maximumEntity));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        partition(
                                maximumEnvironment,
                                maximumSource,
                                maximumTenant,
                                maximumEntity + " "));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ControlPlaneSource(maximumSource + " ", maximumKind, START));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ControlPlaneSource(maximumSource, maximumKind + " ", START));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ImmutableFingerprint(maximumVersion + " ", FINGERPRINT));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ImmutableFingerprint(maximumVersion, FINGERPRINT + " "));
        assertThrows(
                IllegalArgumentException.class,
                () -> start(maximumWindowStrategy + " ", maximumIdempotencyKey));
        assertThrows(
                IllegalArgumentException.class,
                () -> start(maximumWindowStrategy, maximumIdempotencyKey + " "));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneCounts(
                                UUID.randomUUID(), maximumPhase + " ", 0, 0, 0, 0, 0, 0, START));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneTransition(
                                UUID.randomUUID(),
                                ExecutionState.EXTRACTING,
                                ExecutionState.EXTRACTED,
                                maximumReason + " ",
                                START));
    }

    @Test
    void definesOnlyForwardNonPublicationTransitionsAndTerminalStates() {
        assertTrue(ExecutionState.PLANNED.canTransitionTo(ExecutionState.EXTRACTING));
        assertTrue(ExecutionState.EXTRACTING.canTransitionTo(ExecutionState.EXTRACTED));
        assertTrue(ExecutionState.EXTRACTED.canTransitionTo(ExecutionState.STAGED));
        assertFalse(ExecutionState.STAGED.canTransitionTo(ExecutionState.PROMOTED));
        assertFalse(ExecutionState.PROMOTED.canTransitionTo(ExecutionState.RECONCILED));
        assertTrue(ExecutionState.PROMOTED.canTransitionTo(ExecutionState.FAILED));
        assertFalse(ExecutionState.PROMOTED.canTransitionTo(ExecutionState.PUBLISHED));
        assertFalse(ExecutionState.PUBLISHED.canTransitionTo(ExecutionState.FAILED));
        assertTrue(ExecutionState.BLOCKED.isTerminal());
        assertFalse(ExecutionState.EXTRACTING.isTerminal());
    }

    @Test
    void acceptsOnlyAReplayThatReferencesAnOriginalExecution() {
        final UUID original = UUID.fromString("00000000-0000-0000-0000-000000000020");
        final ControlPlaneStart replay =
                new ControlPlaneStart(
                        UUID.fromString("00000000-0000-0000-0000-000000000021"),
                        UUID.fromString("00000000-0000-0000-0000-000000000022"),
                        key(ExecutionMode.REPLAY),
                        "interval",
                        fingerprint(),
                        fingerprint(),
                        "replay-key",
                        Optional.of(original),
                        Duration.ofMinutes(1),
                        START);

        assertEquals(Optional.of(original), replay.replayOfExecutionId());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneStart(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                key(ExecutionMode.REPLAY),
                                "interval",
                                fingerprint(),
                                fingerprint(),
                                "missing-original",
                                Optional.empty(),
                                Duration.ofMinutes(1),
                                START));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneStart(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                key(ExecutionMode.INCREMENTAL),
                                "interval",
                                fingerprint(),
                                fingerprint(),
                                "wrong-mode",
                                Optional.of(original),
                                Duration.ofMinutes(1),
                                START));
        final UUID self = UUID.fromString("00000000-0000-0000-0000-000000000023");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneStart(
                                self,
                                UUID.randomUUID(),
                                key(ExecutionMode.REPLAY),
                                "interval",
                                fingerprint(),
                                fingerprint(),
                                "self-reference",
                                Optional.of(self),
                                Duration.ofMinutes(1),
                                START));
    }

    @Test
    void rejectsLeaseDurationsThatCannotBeStoredAsWholeBoundedSeconds() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneStart(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                key(ExecutionMode.INCREMENTAL),
                                "interval",
                                fingerprint(),
                                fingerprint(),
                                "small-lease",
                                Optional.empty(),
                                Duration.ofMillis(500),
                                START));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneStart(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                key(ExecutionMode.INCREMENTAL),
                                "interval",
                                fingerprint(),
                                fingerprint(),
                                "long-lease",
                                Optional.empty(),
                                Duration.ofHours(25),
                                START));
    }

    @Test
    void enforcesSanitizedPageAndClosedCountEquations() {
        final UUID executionId = UUID.randomUUID();
        final ControlPlanePage page =
                new ControlPlanePage(executionId, 1, 1, 10, 4, 3, 100, false, START);
        final ControlPlaneCounts counts =
                new ControlPlaneCounts(executionId, "stage", 5, 3, 1, 2, 1, 1, START);

        assertEquals(3, page.distinctRootKeys());
        assertEquals(5, counts.physicalRows());
        assertEquals(1, counts.unidentifiedQuarantineRows());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ControlPlanePage(executionId, 1, 1, 10, 1, 2, 0, false, START));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ControlPlanePage(executionId, 1, 1, 10, 1, 1, 0, true, START));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ControlPlaneCounts(executionId, "stage", 5, 3, 0, 3, 0, 1, START));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneCounts(
                                executionId, " STAGING_KERNEL ", 0, 0, 0, 0, 0, 0, START));
        assertEquals(
                "staging_kernel",
                new ControlPlaneCounts(executionId, "staging_kernel", 0, 0, 0, 0, 0, 0, START)
                        .phase());
    }

    @Test
    void acceptsAValidTransitionAndRejectsPublicationOrUnsafeReasonCodes() {
        final ControlPlaneTransition transition =
                new ControlPlaneTransition(
                        UUID.randomUUID(),
                        ExecutionState.EXTRACTING,
                        ExecutionState.EXTRACTED,
                        " STAGE_OK ",
                        START);

        assertEquals("STAGE_OK", transition.reasonCode());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneTransition(
                                UUID.randomUUID(),
                                ExecutionState.PROMOTED,
                                ExecutionState.PUBLISHED,
                                "PUBLICATION",
                                START));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneTransition(
                                UUID.randomUUID(),
                                ExecutionState.EXTRACTING,
                                ExecutionState.EXTRACTED,
                                "not safe",
                                START));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneTransition(
                                UUID.randomUUID(),
                                ExecutionState.EXTRACTING,
                                ExecutionState.EXTRACTED,
                                "stage_ok",
                                START));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneTransition(
                                UUID.randomUUID(),
                                ExecutionState.EXTRACTING,
                                ExecutionState.EXTRACTED,
                                "ß",
                                START));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneTransition(
                                UUID.randomUUID(),
                                ExecutionState.EXTRACTING,
                                ExecutionState.EXTRACTED,
                                "_X",
                                START));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ControlPlaneTransition(
                                UUID.randomUUID(),
                                ExecutionState.EXTRACTING,
                                ExecutionState.EXTRACTED,
                                "1X",
                                START));
    }

    @Test
    void acceptsOnlyANonNegativeAggregateRecoveryResult() {
        assertEquals(0, new ControlPlaneRecoveryResult(0).recoveredExecutions());
        assertEquals(3, new ControlPlaneRecoveryResult(3).recoveredExecutions());
        assertThrows(IllegalArgumentException.class, () -> new ControlPlaneRecoveryResult(-1));
    }

    private static ExecutionPartitionKey key(final ExecutionMode mode) {
        return new ExecutionPartitionKey(
                "LOCAL_SHADOW",
                "SYNTHETIC_SOURCE",
                "SYNTHETIC_TENANT",
                "SYNTHETIC_ENTITY",
                mode,
                START,
                START.plusSeconds(60));
    }

    private static ExecutionPartitionKey partition(
            final String environment,
            final String source,
            final String tenant,
            final String entity) {
        return new ExecutionPartitionKey(
                environment,
                source,
                tenant,
                entity,
                ExecutionMode.INCREMENTAL,
                START,
                START.plusSeconds(60));
    }

    private static ControlPlaneStart start(
            final String windowStrategy, final String idempotencyKey) {
        return new ControlPlaneStart(
                UUID.randomUUID(),
                UUID.randomUUID(),
                key(ExecutionMode.INCREMENTAL),
                windowStrategy,
                fingerprint(),
                fingerprint(),
                idempotencyKey,
                Optional.empty(),
                Duration.ofMinutes(1),
                START);
    }

    private static ImmutableFingerprint fingerprint() {
        return new ImmutableFingerprint("v1", FINGERPRINT);
    }
}
