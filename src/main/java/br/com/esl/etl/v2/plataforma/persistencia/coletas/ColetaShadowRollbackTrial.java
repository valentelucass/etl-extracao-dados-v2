package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaPromotionGateway;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedProvenance;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ObservedRow;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Opt-in shadow trial. Every adapter receives the same rollback-only physical connection. The outer
 * transaction is deliberately nested; the versioned procedures must still be verified to balance
 * their own BEGIN/COMMIT and contain no ROLLBACK. Every phase checks that the transaction and SQL
 * session are still the ones that were opened.
 */
public final class ColetaShadowRollbackTrial implements AutoCloseable {
    private static final int MAXIMUM_BATCHES = 1000;
    private static final String TRANSACTION_STATE = "SELECT @@TRANCOUNT, XACT_STATE(), @@SPID";
    private static final String CURRENT_AUDITED_PAGE =
            "SELECT MAX(page_number) FROM ctl.execution_page_audit"
                    + " WHERE execution_id=? AND physical_rows>0 AND terminal_empty_page=0";
    private final ColetaTemporalLaboratorySession session;
    private final TransactionState boundary;
    private UUID executionId;
    private int stagedBatches;
    private int lastStagedPage;
    // One ASCII page digit per consecutive batch; at most 1000 digits, no retained key collection.
    private String batchPages = "";
    private boolean prepared;
    private boolean promoted;
    private boolean failed;
    private boolean closed;

    private ColetaShadowRollbackTrial(final ColetaTemporalLaboratorySession session)
            throws SQLException {
        this.session = Objects.requireNonNull(session);
        session.controlStatements(ColetaTemporalLaboratorySession.PILOT_QUERY_SECONDS);
        try (var connection = session.getConnection();
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            statement.execute("BEGIN TRANSACTION; BEGIN TRANSACTION;");
        }
        boundary = readTransactionState();
        if (boundary.depth() < 2 || boundary.state() != 1 || boundary.sessionId() < 1) {
            throw new SQLException("COL_SHADOW_TRANSACTION_NOT_PROTECTED");
        }
    }

    public static <T> T executeFromEnvironment(final Work<T> work) throws Exception {
        Objects.requireNonNull(work);
        return execute(ColetaTemporalLaboratorySession.openPilotFromEnvironment(), work);
    }

    static <T> T execute(final ColetaTemporalLaboratorySession session, final Work<T> work)
            throws Exception {
        try (session) {
            Objects.requireNonNull(work);
            try (var trial = new ColetaShadowRollbackTrial(session)) {
                return work.apply(trial);
            }
        }
    }

    /** Control-plane JDBC only; every loan resolves to this session's one physical connection. */
    public DataSource controlPlaneDataSource() throws SQLException {
        checkTransaction();
        return session;
    }

    /** Inject this tracked adapter into ExtrairColetasDataExport. */
    public ColetaStagingGateway stagingGateway() throws SQLException {
        checkTransaction();
        return new ColetaStagingGateway() {
            @Override
            public void stage(final ColetaStageBatch batch) {
                stage(batch, CancellationToken.none());
            }

            @Override
            public void stage(final ColetaStageBatch batch, final CancellationToken cancellation) {
                try {
                    ColetaShadowRollbackTrial.this.stage(batch, cancellation);
                } catch (final SQLException failure) {
                    throw new IllegalStateException("COL_SHADOW_STAGE_TRANSACTION", failure);
                }
            }
        };
    }

    /** Inject this tracked adapter into LocalColetasFretesRuntime. */
    public ColetaPromotionGateway promotionGateway() throws SQLException {
        checkTransaction();
        return new ColetaPromotionGateway() {
            @Override
            public void prepareCandidateSet(final ContractPromotionPermit permit) {
                prepareCandidateSet(permit, CancellationToken.none());
            }

            @Override
            public void prepareCandidateSet(
                    final ContractPromotionPermit permit, final CancellationToken cancellation) {
                try {
                    ColetaShadowRollbackTrial.this.prepare(permit, cancellation);
                } catch (final SQLException failure) {
                    throw new IllegalStateException("COL_SHADOW_PREPARE_TRANSACTION", failure);
                }
            }

            @Override
            public StagingPublicationResult applyReconcileAndPublish(
                    final ContractPromotionPermit permit,
                    final DataQualityPromotionPermit quality) {
                return applyReconcileAndPublish(permit, quality, CancellationToken.none());
            }

            @Override
            public StagingPublicationResult applyReconcileAndPublish(
                    final ContractPromotionPermit permit,
                    final DataQualityPromotionPermit quality,
                    final CancellationToken cancellation) {
                try {
                    return ColetaShadowRollbackTrial.this.promote(permit, quality, cancellation);
                } catch (final SQLException failure) {
                    throw new IllegalStateException("COL_SHADOW_PROMOTION_TRANSACTION", failure);
                }
            }
        };
    }

    /**
     * Call after each control-plane procedure that runs through {@link #controlPlaneDataSource()}.
     */
    public void checkpoint() throws SQLException {
        checkTransaction();
    }

    public void stage(final ColetaStageBatch batch, final CancellationToken cancellation)
            throws SQLException {
        Objects.requireNonNull(batch);
        Objects.requireNonNull(cancellation);
        checkTransaction();
        if (prepared
                || promoted
                || (executionId != null && !executionId.equals(batch.executionId()))) {
            failed = true;
            throw new IllegalStateException("COL_SHADOW_STAGE_ORDER");
        }
        try {
            cancellation.throwIfCancellationRequested();
            final int sourcePage = currentAuditedPage(batch.executionId());
            if (batch.batchNumber() != stagedBatches + 1 || batch.batchNumber() > MAXIMUM_BATCHES) {
                throw new IllegalStateException("COL_SHADOW_BATCH_SEQUENCE");
            }
            if (sourcePage < lastStagedPage
                    || sourcePage > lastStagedPage + 1
                    || sourcePage > 4
                    || (stagedBatches == 0 && sourcePage != 1)) {
                throw new IllegalStateException("COL_SHADOW_PAGE_SEQUENCE");
            }
            new JdbcSqlServerColetaStagingGateway(session).stage(batch, cancellation);
            checkTransaction();
            executionId = batch.executionId();
            batchPages += (char) ('0' + sourcePage);
            lastStagedPage = sourcePage;
            stagedBatches++;
        } catch (final SQLException | RuntimeException | Error failure) {
            failed = true;
            throw failure;
        }
    }

    public void prepare(final ContractPromotionPermit permit, final CancellationToken cancellation)
            throws SQLException {
        if (stagedBatches == 0) {
            failed = true;
            throw new IllegalStateException("COL_SHADOW_EMPTY_TRAVERSAL");
        }
        Objects.requireNonNull(permit);
        Objects.requireNonNull(cancellation);
        checkTransaction();
        if (prepared || !executionId.equals(permit.executionId())) {
            failed = true;
            throw new IllegalStateException("COL_SHADOW_PREPARE_ORDER");
        }
        try {
            cancellation.throwIfCancellationRequested();
            new JdbcSqlServerColetaPromotionGateway(session)
                    .prepareCandidateSet(permit, cancellation);
            checkTransaction();
            prepared = true;
        } catch (final SQLException | RuntimeException | Error failure) {
            failed = true;
            throw failure;
        }
    }

    public StagingPublicationResult promote(
            final ContractPromotionPermit permit,
            final DataQualityPromotionPermit quality,
            final CancellationToken cancellation)
            throws SQLException {
        Objects.requireNonNull(permit);
        Objects.requireNonNull(quality);
        Objects.requireNonNull(cancellation);
        checkTransaction();
        if (!prepared || promoted || !executionId.equals(permit.executionId())) {
            failed = true;
            throw new IllegalStateException("COL_SHADOW_PROMOTION_ORDER");
        }
        try {
            cancellation.throwIfCancellationRequested();
            final var result =
                    new JdbcSqlServerColetaPromotionGateway(session)
                            .applyReconcileAndPublish(permit, quality, cancellation);
            checkTransaction();
            promoted = true;
            return result;
        } catch (final SQLException | RuntimeException | Error failure) {
            failed = true;
            throw failure;
        }
    }

    public UUID promotedExecutionId() throws SQLException {
        checkTransaction();
        if (!promoted) {
            failed = true;
            throw new IllegalStateException("COL_SHADOW_NOT_PROMOTED");
        }
        return executionId;
    }

    public Verification verifyObservedTraversal(
            final ExpectedProvenance provenance,
            final Binding expectedBinding,
            final List<ExpectedRoot> expectedRoots,
            final Map<Integer, Integer> sourceBatchPages,
            final CancellationToken cancellation)
            throws SQLException {
        Objects.requireNonNull(cancellation);
        try {
            if (expectedRoots == null || expectedRoots.isEmpty()) {
                throw new IllegalArgumentException("COL_SHADOW_EMPTY_EXPECTED_TRAVERSAL");
            }
            verifySourceBatchPages(sourceBatchPages);
            final UUID run = promotedExecutionId();
            if (provenance != ExpectedProvenance.CAPTURED_6908_BOUNDED_TRAVERSAL
                    && provenance != ExpectedProvenance.SYNTHETIC_FIXTURE) {
                throw new IllegalArgumentException("COL_SHADOW_UNPROVEN_ORACLE");
            }
            final Binding expected = Objects.requireNonNull(expectedBinding);
            final Binding observed = ColetaShadowSetComparator.readObservedBinding(session, run);
            if (!run.equals(expected.comparisonCohort())
                    || expected.sourcePage() != 0
                    || !expected.equals(observed)) {
                throw new SQLException("COL_SHADOW_BINDING_MISMATCH");
            }
            checkTransaction();
            cancellation.throwIfCancellationRequested();
            final var result = ColetaShadowSetComparator.compare(session, run, expectedRoots);
            checkTransaction();
            cancellation.throwIfCancellationRequested();
            if (!result.matches()) {
                throw new ColetaShadowSetComparator.Mismatch(result);
            }
            if (result.expectedPhysicalRows() > 1000) {
                throw new SQLException("COL_SHADOW_READER_ROW_LIMIT");
            }
            final List<ObservedRow> rows =
                    ColetaShadowSetComparator.readBoundedBatchRows(
                            session,
                            executionId,
                            expectedRoots.get(0).identity(),
                            (int) result.expectedPhysicalRows(),
                            batchPages);
            checkTransaction();
            cancellation.throwIfCancellationRequested();
            final var domain =
                    ColetaObservedRootComparator.compare(
                            provenance, expected, observed, expectedRoots, rows);
            if (!domain.declaredObservationsMatch()) {
                throw new SQLException("COL_SHADOW_PRESENCE_OR_MULTIPLICITY_DIVERGENCE");
            }
            return new Verification(
                    result,
                    domain.evidenceLabel(),
                    domain.presenceComparedCells(),
                    domain.observedPhysicalRows(),
                    domain.repeatedRootRows());
        } catch (final SQLException | RuntimeException | Error failure) {
            failed = true;
            throw failure;
        }
    }

    public record Verification(
            ColetaShadowSetComparator.Result sql,
            String evidenceLabel,
            int presenceComparedCells,
            int observedPhysicalRows,
            int repeatedRootRows) {
        public Verification {
            Objects.requireNonNull(sql);
            if (!"PARIDADE_DA_TRAVESSIA_LIMITADA_OBSERVADA".equals(evidenceLabel)
                    && !"COMPARACAO_DE_RAIZES_OBSERVADAS".equals(evidenceLabel)) {
                throw new IllegalArgumentException("COL_SHADOW_VERIFICATION_LABEL");
            }
            if (!sql.matches()
                    || presenceComparedCells < 0
                    || observedPhysicalRows < 0
                    || repeatedRootRows < 0) {
                throw new IllegalArgumentException("COL_SHADOW_VERIFICATION_INVALID");
            }
        }

        public boolean windowCompletenessProven() {
            return false;
        }

        public boolean childCompletenessProven() {
            return false;
        }
    }

    private void checkTransaction() throws SQLException {
        if (closed || failed) {
            throw new SQLException("COL_SHADOW_TRIAL_UNAVAILABLE");
        }
        final TransactionState current;
        try {
            current = readTransactionState();
        } catch (final SQLException | RuntimeException failure) {
            failed = true;
            throw failure;
        }
        if (!boundary.equals(current)) {
            failed = true;
            throw new SQLException("COL_SHADOW_TRANSACTION_DRIFT");
        }
    }

    private TransactionState readTransactionState() throws SQLException {
        try (var connection = session.getConnection();
                var statement = connection.prepareStatement(TRANSACTION_STATE)) {
            statement.setQueryTimeout(10);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("COL_SHADOW_TRANSACTION_UNOBSERVABLE");
                }
                final var state =
                        new TransactionState(rows.getInt(1), rows.getInt(2), rows.getInt(3));
                if (rows.next()) {
                    throw new SQLException("COL_SHADOW_TRANSACTION_UNOBSERVABLE");
                }
                return state;
            }
        }
    }

    private int currentAuditedPage(final UUID run) throws SQLException {
        try (var connection = session.getConnection();
                var statement = connection.prepareStatement(CURRENT_AUDITED_PAGE)) {
            statement.setQueryTimeout(10);
            statement.setString(1, run.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("COL_SHADOW_PAGE_AUDIT_REQUIRED");
                }
                final int page = rows.getInt(1);
                if (page < 1 || rows.wasNull() || rows.next()) {
                    throw new SQLException("COL_SHADOW_PAGE_AUDIT_REQUIRED");
                }
                return page;
            }
        }
    }

    private void verifySourceBatchPages(final Map<Integer, Integer> sourceBatchPages)
            throws SQLException {
        final Map<Integer, Integer> pages = Objects.requireNonNull(sourceBatchPages);
        if (pages.size() != stagedBatches) {
            throw new SQLException("COL_SHADOW_BATCH_PAGE_DRIFT");
        }
        for (int batch = 1; batch <= stagedBatches; batch++) {
            if (!Integer.valueOf(batchPages.charAt(batch - 1) - '0').equals(pages.get(batch))) {
                throw new SQLException("COL_SHADOW_BATCH_PAGE_DRIFT");
            }
        }
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            // An explicit rollback precedes the session's own defensive rollback and close.
            try (session) {
                session.rollback();
            }
        }
    }

    @FunctionalInterface
    public interface Work<T> {
        T apply(ColetaShadowRollbackTrial trial) throws Exception;
    }

    private record TransactionState(int depth, int state, int sessionId) {}
}
