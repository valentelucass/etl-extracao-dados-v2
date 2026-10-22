package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Bank-owned physical boundary for the separate DEV engine. Preparation and verification must
 * perform no SQL/TDS, HTTP or secret lookup. A bundle or a catalog admission is never an engine
 * admission. Runtime binds the concrete Bank adapter directly in the leased application JAR; there
 * is no provider discovery or caller-selected implementation.
 */
public interface RuntimeDevEngineBankBinding {
    Admission prepare(Scope scope) throws SQLException;

    /**
     * All values are descriptive; only the Bank implementation validates and consumes authority.
     */
    record Scope(
            RuntimeConfiguration configuration,
            RuntimeAuthorizationScope authorization,
            C78LocalSqlTarget target,
            String bundleManifestSHA256,
            String configurationSHA256,
            String requestSHA256,
            String planSHA256,
            DataExportTemplate template,
            int pageSize,
            DataExportExtractionLimits extractionLimits) {
        public Scope {
            Objects.requireNonNull(configuration);
            Objects.requireNonNull(authorization);
            Objects.requireNonNull(target);
            Objects.requireNonNull(template);
            Objects.requireNonNull(extractionLimits);
            for (final String digest :
                    java.util.List.of(
                            bundleManifestSHA256, configurationSHA256, requestSHA256, planSHA256)) {
                if (!digest.matches("[a-f0-9]{64}")) {
                    throw new IllegalArgumentException("DEV_ENGINE_SCOPE_DIGEST");
                }
            }
            if (!configuration.shadowStorage().usesC78LocalAuthentication()
                    || !target.jdbcUrl().equals(configuration.shadowStorage().jdbcUrl())
                    || pageSize < 1) {
                throw new IllegalArgumentException("DEV_ENGINE_SCOPE_TARGET");
            }
        }

        @Override
        public String toString() {
            return "RuntimeDevEngineScope[redacted; descriptive-not-authority]";
        }
    }

    interface Admission extends AutoCloseable {
        /**
         * Validate current Root dispatch, exact scope/bytes, finite SQL/HTTP budgets, target,
         * contracts, writer protection and Fontes' current same-caller quota evidence. Refuse
         * before open on any missing/expired gate. This method does not execute physical I/O.
         */
        void verify() throws SQLException;

        /**
         * Bank alone opens the admitted C78 session, with actual native/TLS checks before LOGIN7.
         */
        Session open() throws SQLException;

        @Override
        void close() throws SQLException;
    }

    interface Session extends AutoCloseable {
        /** The bounded, real C78 DataSource; never the Windows factory or a synthetic adapter. */
        DataSource dataSource();

        /**
         * Explicit SAMPLE staging in this same Bank-owned physical transaction. The real adapter
         * binds23parameters/batch/timeouts; borrowers cannot commit or independently rollback.
         * Historical sessions retain a refusal until Banco supplies this capability.
         */
        default br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway
                sampleStagingGateway() throws SQLException {
            throw new Refusal(MissingGate.PHYSICAL_CONTRACT);
        }

        /**
         * Bank-only scoped readback, rollback, three reserved cleanup SELECTs and physical close.
         * No borrower commit/autocommit capability or arbitrary query port is exposed. A return
         * must derive from the same owned connection, never from serialized acceptance flags.
         */
        default SampleReadback finishSample(final SampleFinish finish) throws SQLException {
            throw new Refusal(MissingGate.PHYSICAL_CONTRACT);
        }

        /** Consume the unit's finite budget and recheck its gates; no automatic retry. */
        void checkpoint() throws SQLException;

        /** Reconcile uncertain outcomes and close only owned resources under the Bank contract. */
        @Override
        void close() throws SQLException;
    }

    /**
     * Bounded SAMPLE12 expectation, memory only; identities and execution UUID are never logged.
     */
    final class SampleExpectation {
        public static final int MAXIMUM_PHYSICAL_ROWS = 12;
        private final UUID executionId;
        private final int physicalRows;
        private final List<ExpectedRoot> expectedRoots;
        private final Map<Integer, Integer> batchPages;

        public SampleExpectation(
                final UUID executionId,
                final int physicalRows,
                final List<ExpectedRoot> expectedRoots,
                final Map<Integer, Integer> batchPages) {
            this.executionId = Objects.requireNonNull(executionId);
            Objects.requireNonNull(expectedRoots);
            Objects.requireNonNull(batchPages);
            if (physicalRows < 1
                    || physicalRows > MAXIMUM_PHYSICAL_ROWS
                    || expectedRoots.size() != 1
                    || batchPages.size() != 1) {
                throw new IllegalArgumentException("DEV_SAMPLE_EXPECTATION_BOUNDS");
            }
            this.expectedRoots = List.copyOf(expectedRoots);
            this.batchPages = Map.copyOf(batchPages);
            final var root = this.expectedRoots.get(0);
            if (root.physicalRows().isEmpty()
                    || root.physicalRows().getAsInt() != physicalRows
                    || !root.fieldPresence().isEmpty()
                    || !root.sourcePages().equals(Set.of(1))
                    || !this.batchPages.equals(Map.of(1, 1))) {
                throw new IllegalArgumentException("DEV_SAMPLE_EXPECTATION_MULTIPLICITY");
            }
            this.physicalRows = physicalRows;
        }

        public UUID executionId() {
            return executionId;
        }

        public int physicalRows() {
            return physicalRows;
        }

        /** At most one immutable scoped root and its physical multiplicity; no payload. */
        public List<ExpectedRoot> expectedRoots() {
            return expectedRoots;
        }

        /** Exactly one microbatch from source page1 under the SAMPLE12 profile. */
        public Map<Integer, Integer> batchPages() {
            return batchPages;
        }

        @Override
        public String toString() {
            return "SampleExpectation[redacted; rows=" + physicalRows + ", roots=1, batches=1]";
        }
    }

    enum SampleOutcome {
        PARTIAL_BOUNDARY,
        CANCELLED,
        FAILED
    }

    record SampleFinish(
            Optional<SampleExpectation> expectation,
            RuntimeExitCategory executorOutcome,
            SampleOutcome outcome) {
        public SampleFinish {
            Objects.requireNonNull(expectation);
            Objects.requireNonNull(executorOutcome);
            Objects.requireNonNull(outcome);
            if (executorOutcome == RuntimeExitCategory.SUCCESS
                    || expectation.isPresent() != (outcome == SampleOutcome.PARTIAL_BOUNDARY)) {
                throw new IllegalArgumentException("DEV_SAMPLE_FINISH_OUTCOME");
            }
        }

        @Override
        public String toString() {
            return "SampleFinish[redacted; outcome="
                    + outcome
                    + ", executor="
                    + executorOutcome
                    + "]";
        }
    }

    /** Aggregate owner evidence only; no execution/source identity or business key is returned. */
    record SampleReadback(
            boolean expectationVerified,
            boolean rollbackVerified,
            boolean scopedAbsenceAndNoOwnXVerified,
            boolean finalTransactionZeroVerified,
            boolean physicalClosed,
            int physicalRowsVerified,
            int rootsVerified,
            int batchesVerified,
            int sqlStatementsUsed) {
        public SampleReadback {
            if (physicalRowsVerified < 0
                    || physicalRowsVerified > SampleExpectation.MAXIMUM_PHYSICAL_ROWS
                    || rootsVerified < 0
                    || rootsVerified > 1
                    || batchesVerified < 0
                    || batchesVerified > 1
                    || sqlStatementsUsed < 0
                    || sqlStatementsUsed > 128) {
                throw new IllegalArgumentException("DEV_SAMPLE_READBACK_BOUNDS");
            }
        }

        public boolean cleanupVerified() {
            return rollbackVerified
                    && scopedAbsenceAndNoOwnXVerified
                    && finalTransactionZeroVerified
                    && physicalClosed;
        }

        public boolean matches(final SampleFinish finish) {
            Objects.requireNonNull(finish);
            if (!cleanupVerified()) {
                return false;
            }
            if (finish.expectation().isEmpty()) {
                return !expectationVerified
                        && physicalRowsVerified == 0
                        && rootsVerified == 0
                        && batchesVerified == 0;
            }
            final var expected = finish.expectation().orElseThrow();
            return expectationVerified
                    && physicalRowsVerified == expected.physicalRows()
                    && rootsVerified == expected.expectedRoots().size()
                    && batchesVerified == expected.batchPages().size();
        }
    }

    enum MissingGate {
        ADMISSION,
        SOURCE_QUOTA,
        PHYSICAL_CONTRACT,
        WRITER_PROTECTION,
        BUDGET
    }

    /**
     * Static reason only; no provider exception message, scope, credential or payload is logged.
     */
    final class Refusal extends SQLException {
        private static final long serialVersionUID = 1L;
        private final MissingGate gate;

        public Refusal(final MissingGate gate) {
            super("DEV_ENGINE_GATE_" + Objects.requireNonNull(gate).name());
            this.gate = gate;
        }

        public MissingGate gate() {
            return gate;
        }
    }
}
