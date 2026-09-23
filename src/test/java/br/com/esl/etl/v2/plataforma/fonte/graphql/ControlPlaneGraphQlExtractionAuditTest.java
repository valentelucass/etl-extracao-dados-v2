package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCounts;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCycle;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePage;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePageTerminality;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneRecoveryResult;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneSource;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneTransition;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ControlPlaneGraphQlExtractionAuditTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000332");
    private static final Instant NOW = Instant.parse("2026-09-01T16:30:00Z");

    @Test
    void mapsPopulatedPagesToTypedLocalTerminalityWithoutCursorOrPayload() {
        final RecordingControlPlane controlPlane = new RecordingControlPlane();
        final ControlPlaneGraphQlExtractionAudit audit =
                new ControlPlaneGraphQlExtractionAudit(controlPlane);

        audit.executionStarted(
                new GraphQlExtractionAudit.ExecutionStarted(
                        EXECUTION_ID, GraphQlReadOperation.USERS_SNAPSHOT, 20, 2, 40, NOW));
        audit.pageRead(page(1, 2, 2, 512, true));
        audit.pageRead(page(2, 1, 1, 256, false));

        assertEquals(2, controlPlane.pages.size());
        final ControlPlanePage first = controlPlane.pages.get(0);
        assertEquals(EXECUTION_ID, first.executionId());
        assertEquals(1, first.pageNumber());
        assertEquals(1, first.pageAttempt());
        assertEquals(20, first.requestedPageSize());
        assertEquals(2, first.physicalRows());
        assertEquals(2, first.distinctRootKeys());
        assertEquals(512, first.responseBytes());
        assertEquals(ControlPlanePageTerminality.NONE, first.terminality());
        assertEquals(NOW, first.readAt());
        assertEquals(
                ControlPlanePageTerminality.GRAPHQL_PAGE_INFO,
                controlPlane.pages.get(1).terminality());
    }

    @Test
    void lifecycleRequiresRealPagesAndFailuresFromControlPlanePropagate() {
        final RecordingControlPlane recording = new RecordingControlPlane();
        final ControlPlaneGraphQlExtractionAudit audit =
                new ControlPlaneGraphQlExtractionAudit(recording);
        audit.executionStarted(
                new GraphQlExtractionAudit.ExecutionStarted(
                        EXECUTION_ID, GraphQlReadOperation.USERS_SNAPSHOT, 20, 2, 40, NOW));
        assertThrows(
                IllegalStateException.class,
                () ->
                        audit.executionCompleted(
                                new GraphQlExtractionResult(
                                        EXECUTION_ID,
                                        GraphQlReadOperation.USERS_SNAPSHOT,
                                        1,
                                        1,
                                        NOW,
                                        NOW,
                                        GraphQlTraversalVerification
                                                .LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED)));
        audit.executionFailed(
                new GraphQlExtractionAudit.ExecutionFailed(
                        EXECUTION_ID,
                        GraphQlReadOperation.USERS_SNAPSHOT,
                        0,
                        0,
                        NOW,
                        GraphQlFailureCategory.RUNTIME_FAILURE));
        assertEquals(0, recording.pages.size());

        assertThrows(NullPointerException.class, () -> audit.executionStarted(null));
        assertThrows(NullPointerException.class, () -> audit.pageRead(null));
        assertThrows(NullPointerException.class, () -> audit.executionCompleted(null));
        assertThrows(NullPointerException.class, () -> audit.executionFailed(null));
        final var failing = new ControlPlaneGraphQlExtractionAudit(new RecordingControlPlane(true));
        failing.executionStarted(
                new GraphQlExtractionAudit.ExecutionStarted(
                        EXECUTION_ID, GraphQlReadOperation.USERS_SNAPSHOT, 20, 2, 40, NOW));
        assertEquals(
                "synthetic-control-plane-failure",
                assertThrows(
                                IllegalStateException.class,
                                () -> failing.pageRead(page(1, 1, 1, 64, false)))
                        .getMessage());
        assertThrows(
                NullPointerException.class, () -> new ControlPlaneGraphQlExtractionAudit(null));
    }

    private static GraphQlExtractionAudit.PageRead page(
            final int pageNumber,
            final int nodes,
            final int distinct,
            final long bytes,
            final boolean hasNext) {
        return new GraphQlExtractionAudit.PageRead(
                EXECUTION_ID,
                GraphQlReadOperation.USERS_SNAPSHOT,
                pageNumber,
                20,
                nodes,
                distinct,
                bytes,
                hasNext,
                NOW);
    }

    private static final class RecordingControlPlane implements ControlPlane {

        private final List<ControlPlanePage> pages = new ArrayList<>();
        private final boolean failPage;

        private RecordingControlPlane() {
            this(false);
        }

        private RecordingControlPlane(final boolean failPage) {
            this.failPage = failPage;
        }

        @Override
        public void registerSource(final ControlPlaneSource source) {}

        @Override
        public void startCycle(final ControlPlaneCycle cycle) {}

        @Override
        public void startExecution(final ControlPlaneStart start) {}

        @Override
        public void heartbeat(
                final UUID executionId, final Instant heartbeatAt, final Duration leaseExtension) {}

        @Override
        public void recordPage(final ControlPlanePage page) {
            if (failPage) {
                throw new IllegalStateException("synthetic-control-plane-failure");
            }
            pages.add(page);
        }

        @Override
        public void recordCounts(final ControlPlaneCounts counts) {}

        @Override
        public void transition(final ControlPlaneTransition transition) {}

        @Override
        public void registerIncrementalFrontier(
                final ExecutionPartitionKey incrementalPartition,
                final Instant initialContiguousEnd,
                final Instant registeredAt) {}

        @Override
        public ControlPlaneRecoveryResult recoverStaleExecutions() {
            return new ControlPlaneRecoveryResult(0);
        }
    }
}
