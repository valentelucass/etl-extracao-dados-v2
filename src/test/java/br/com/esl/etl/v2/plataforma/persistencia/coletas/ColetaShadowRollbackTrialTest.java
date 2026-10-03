package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaFreshnessOrigin;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedProvenance;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStatus;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneSource;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit.PageRead;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.persistencia.sombra.JdbcDataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityTestSupport;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class ColetaShadowRollbackTrialTest {
    @Test
    void sampleReadsOnlyStagingWithoutPromotionOrTerminalAndRollsBack() throws Exception {
        final var jdbc = new FakePhysical();
        final UUID run = UUID.randomUUID();
        jdbc.verificationRun = run;
        ColetaShadowRollbackTrial.execute(
                jdbc.session(),
                trial -> {
                    trial.stage(batch(run), () -> false);
                    final var result =
                            trial.verifyObservedSample(
                                    sampleBinding(run),
                                    List.of(expectedRoot()),
                                    Map.of(1, 1),
                                    () -> false);
                    assertTrue(result.matches());
                    assertEquals(1, result.observedPhysicalRows());
                    assertEquals(0, result.presenceComparedCells());
                    assertFalse(result.windowCompletenessProven());
                    assertFalse(result.childCompletenessProven());
                    trial.checkpoint();
                    return null;
                });
        assertFalse(jdbc.preparedQueries.stream().anyMatch(sql -> sql.contains("core.")));
        assertEquals(0, jdbc.physicalCommits);
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void sampleRejectsTerminalAuditSetDivergenceAndCopiedBinding() throws Exception {
        for (final String defect : List.of("terminal", "set", "binding", "generic")) {
            final var jdbc = new FakePhysical();
            final UUID run = UUID.randomUUID();
            jdbc.verificationRun = run;
            jdbc.sampleTerminalCount = defect.equals("terminal") ? 1 : 0;
            jdbc.divergeStage = defect.equals("set");
            jdbc.sampleFingerprint = defect.equals("binding") ? "b".repeat(64) : "a".repeat(64);
            jdbc.sampleGenericRows = defect.equals("generic") ? 2 : 1;
            assertThrows(
                    SQLException.class,
                    () ->
                            ColetaShadowRollbackTrial.execute(
                                    jdbc.session(),
                                    trial -> {
                                        trial.stage(batch(run), () -> false);
                                        trial.verifyObservedSample(
                                                sampleBinding(run),
                                                List.of(expectedRoot()),
                                                Map.of(1, 1),
                                                () -> false);
                                        return null;
                                    }),
                    defect);
            assertEquals(0, jdbc.physicalCommits);
            assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
        }
    }

    @Test
    void sampleRejectsWrongPageMapPresenceAndLostMultiplicity() throws Exception {
        for (final String defect : List.of("map", "presence", "multiplicity", "page")) {
            final var jdbc = new FakePhysical();
            final UUID run = UUID.randomUUID();
            jdbc.verificationRun = run;
            final var root = expectedRoot();
            final var expected =
                    new ExpectedRoot(
                            root.identity(),
                            OptionalInt.of(defect.equals("multiplicity") ? 2 : 1),
                            defect.equals("presence")
                                    ? Map.of("status", ColetaAttributePresence.VALUE)
                                    : Map.of(),
                            Set.of(1));
            assertThrows(
                    SQLException.class,
                    () ->
                            ColetaShadowRollbackTrial.execute(
                                    jdbc.session(),
                                    trial -> {
                                        trial.stage(batch(run), () -> false);
                                        trial.verifyObservedSample(
                                                defect.equals("page")
                                                        ? binding(run)
                                                        : sampleBinding(run),
                                                List.of(expected),
                                                Map.of(1, defect.equals("map") ? 2 : 1),
                                                () -> false);
                                        return null;
                                    }),
                    defect);
            assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
        }
    }

    @Test
    void sampleCancellationOrPreparedStateCannotReachReadback() throws Exception {
        for (final boolean cancel : List.of(true, false)) {
            final var jdbc = new FakePhysical();
            final UUID run = UUID.randomUUID();
            jdbc.verificationRun = run;
            ColetaShadowRollbackTrial.execute(
                    jdbc.session(),
                    trial -> {
                        trial.stage(batch(run), () -> false);
                        if (!cancel) {
                            trial.prepare(ContractTestSupport.promotionPermit(run), () -> false);
                        }
                        final int queries = jdbc.preparedQueries.size();
                        final Executable verification =
                                () ->
                                        trial.verifyObservedSample(
                                                sampleBinding(run),
                                                List.of(expectedRoot()),
                                                Map.of(1, 1),
                                                () -> cancel);
                        if (cancel) {
                            assertThrows(ResilienceCancelledException.class, verification);
                        } else {
                            assertThrows(SQLException.class, verification);
                        }
                        assertFalse(
                                jdbc
                                        .preparedQueries
                                        .subList(queries, jdbc.preparedQueries.size())
                                        .stream()
                                        .anyMatch(
                                                sql -> sql.contains("FROM ctl.execution_attempt")));
                        assertThrows(SQLException.class, trial::checkpoint);
                        return null;
                    });
            assertEquals(0, jdbc.physicalCommits);
            assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
        }
    }

    private static Binding sampleBinding(final UUID run) {
        final var binding = binding(run);
        return new Binding(
                binding.sourceInstance(),
                binding.tenantScope(),
                binding.requestDate(),
                binding.contractFingerprint(),
                run,
                1);
    }

    @Test
    void trackedStagingUsesOnePhysicalConnectionAndRollsBackBeforeClose() throws Exception {
        final var jdbc = new FakePhysical();
        final UUID run = UUID.randomUUID();
        ColetaShadowRollbackTrial.execute(
                jdbc.session(),
                trial -> {
                    final var staging = trial.stagingGateway();
                    staging.stage(batch(run), () -> false);
                    trial.checkpoint();
                    assertThrows(IllegalStateException.class, trial::promotedExecutionId);
                    return null;
                });
        assertEquals(1, jdbc.physicalConnections);
        assertEquals(1, jdbc.stagedBatches);
        assertEquals(0, jdbc.physicalCommits);
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void microbatchesAreMappedToAuditedPagesRatherThanTheirBatchNumbers() throws Exception {
        final var jdbc = new FakePhysical();
        final UUID run = UUID.randomUUID();
        ColetaShadowRollbackTrial.execute(
                jdbc.session(),
                trial -> {
                    final var staging = trial.stagingGateway();
                    jdbc.auditedPage = 1;
                    final var first = batch(run, 1, 100);
                    final var second = batch(run, 2, 10);
                    assertEquals(100, first.size());
                    assertEquals(10, second.size());
                    staging.stage(first, () -> false);
                    staging.stage(second, () -> false);
                    jdbc.auditedPage = 2;
                    staging.stage(batch(run, 3), () -> false);
                    assertEquals("112", batchPages(trial));
                    return null;
                });
        assertEquals(3, jdbc.stagedBatches);
        assertEquals(0, jdbc.physicalCommits);
    }

    @Test
    void sourceBatchMapDisagreementStopsBeforeComparisonAndRollsBack() throws Exception {
        final var jdbc = new FakePhysical();
        final UUID run = UUID.randomUUID();
        final var expected =
                new ExpectedRoot(
                        new ScopedSourceIdentity(
                                "source-a",
                                "tenant-a",
                                FirstWaveIdentityContract.Entity.COLETAS,
                                new ScopedSourceIdentity.SourceKey(
                                        ScopedSourceIdentity.WireType.INTEGER, "INTEGER:10")),
                        OptionalInt.of(1),
                        Map.of(),
                        Set.of(1));
        final var failure =
                assertThrows(
                        SQLException.class,
                        () ->
                                ColetaShadowRollbackTrial.execute(
                                        jdbc.session(),
                                        trial -> {
                                            trial.stagingGateway().stage(batch(run), () -> false);
                                            trial.verifyObservedTraversal(
                                                    ExpectedProvenance.SYNTHETIC_FIXTURE,
                                                    null,
                                                    List.of(expected),
                                                    Map.of(1, 2),
                                                    () -> false);
                                            return null;
                                        }));
        assertEquals("COL_SHADOW_BATCH_PAGE_DRIFT", failure.getMessage());
        assertEquals(0, jdbc.physicalCommits);
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void emptyTerminalTraversalStopsBeforePromotionAndRollsBack() throws Exception {
        final var jdbc = new FakePhysical();
        final var failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                ColetaShadowRollbackTrial.execute(
                                        jdbc.session(),
                                        trial -> {
                                            trial.prepare(null, () -> false);
                                            return null;
                                        }));
        assertEquals("COL_SHADOW_EMPTY_TRAVERSAL", failure.getMessage());
        assertEquals(0, jdbc.stagedBatches);
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void unbalancedNestedCommitFailsClosedAndStillRollsBack() throws Exception {
        final var jdbc = new FakePhysical();
        final var failure =
                assertThrows(
                        SQLException.class,
                        () ->
                                ColetaShadowRollbackTrial.execute(
                                        jdbc.session(),
                                        trial -> {
                                            try (var connection =
                                                            trial.controlPlaneDataSource()
                                                                    .getConnection();
                                                    var statement = connection.createStatement()) {
                                                statement.execute("BEGIN TRANSACTION");
                                                statement.execute("COMMIT TRANSACTION");
                                                trial.checkpoint();
                                                statement.execute("COMMIT TRANSACTION");
                                                trial.checkpoint();
                                            }
                                            return null;
                                        }));
        assertEquals("COL_SHADOW_TRANSACTION_DRIFT", failure.getMessage());
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
        assertEquals(0, jdbc.physicalCommits);
    }

    @Test
    void cancellationAndFailureRollbackWithoutStagingOrCommit() throws Exception {
        final var jdbc = new FakePhysical();
        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        ColetaShadowRollbackTrial.execute(
                                jdbc.session(),
                                trial -> {
                                    trial.stagingGateway()
                                            .stage(batch(UUID.randomUUID()), () -> true);
                                    return null;
                                }));
        assertEquals(0, jdbc.stagedBatches);
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void sqlStageFailureCannotReachPromotionAndStillClosesWithRollback() throws Exception {
        final var jdbc = new FakePhysical();
        jdbc.failStage = true;
        assertThrows(
                StagingPersistenceException.class,
                () ->
                        ColetaShadowRollbackTrial.execute(
                                jdbc.session(),
                                trial -> {
                                    trial.stagingGateway()
                                            .stage(batch(UUID.randomUUID()), () -> false);
                                    return null;
                                }));
        assertEquals(0, jdbc.physicalCommits);
        assertEquals(List.of("rollback", "rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void controlAuditAndStagingShareBoundedStatementsAndTimeoutRollsBack() throws Exception {
        final var jdbc = new FakePhysical();
        final UUID run = UUID.randomUUID();
        jdbc.timeoutOperation = "usp_control_plane_register_source";
        final var failure =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                ColetaShadowRollbackTrial.execute(
                                        jdbc.session(),
                                        trial -> {
                                            final var dataSource = trial.controlPlaneDataSource();
                                            new JdbcDataExportExtractionAudit(dataSource)
                                                    .pageRead(
                                                            new PageRead(
                                                                    run,
                                                                    1,
                                                                    100,
                                                                    1,
                                                                    1,
                                                                    Instant.now()));
                                            trial.stagingGateway().stage(batch(run), () -> false);
                                            trial.prepare(
                                                    ContractTestSupport.promotionPermit(run),
                                                    () -> false);
                                            new JdbcSqlServerControlPlane(dataSource)
                                                    .registerSource(
                                                            new ControlPlaneSource(
                                                                    "SYNTHETIC",
                                                                    "DATA_EXPORT",
                                                                    Instant.now()));
                                            return null;
                                        }));
        assertEquals(SQLTimeoutException.class, failure.getCause().getClass());
        assertEquals(20, jdbc.callTimeouts.get("usp_audit_page_read"));
        assertEquals(20, jdbc.callTimeouts.get("usp_stage_coleta_record"));
        assertEquals(20, jdbc.callTimeouts.get("usp_prepare_staged_execution"));
        assertEquals(20, jdbc.callTimeouts.get("usp_control_plane_register_source"));
        assertEquals(1, jdbc.physicalConnections);
        assertEquals(0, jdbc.physicalCommits);
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void promotionTimeoutOnSameSessionStopsAndRollsBack() throws Exception {
        final var jdbc = new FakePhysical();
        final UUID run = UUID.randomUUID();
        jdbc.timeoutOperation = "usp_apply_reconcile_publish_coletas";
        final var failure =
                assertThrows(
                        StagingPersistenceException.class,
                        () ->
                                ColetaShadowRollbackTrial.execute(
                                        jdbc.session(),
                                        trial -> {
                                            trial.stagingGateway().stage(batch(run), () -> false);
                                            final var permit =
                                                    ContractTestSupport.promotionPermit(run);
                                            trial.prepare(permit, () -> false);
                                            trial.promote(
                                                    permit,
                                                    DataQualityTestSupport.promotionPermit(run),
                                                    () -> false);
                                            return null;
                                        }));
        assertEquals(SQLTimeoutException.class, failure.getCause().getClass());
        assertEquals(20, jdbc.callTimeouts.get("usp_prepare_staged_execution"));
        assertEquals(20, jdbc.callTimeouts.get("usp_apply_reconcile_publish_coletas"));
        assertEquals(1, jdbc.physicalConnections);
        assertEquals(0, jdbc.physicalCommits);
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void promotedTraversalComparesPhysicalRowsBeforeRollback() throws Exception {
        final UUID run = UUID.randomUUID();
        final var jdbc = new FakePhysical();
        jdbc.verificationRun = run;
        final var verification =
                ColetaShadowRollbackTrial.execute(
                        jdbc.session(),
                        trial -> {
                            trial.stagingGateway().stage(batch(run));
                            final var permit = ContractTestSupport.promotionPermit(run);
                            final var promotion = trial.promotionGateway();
                            promotion.prepareCandidateSet(permit);
                            promotion.applyReconcileAndPublish(
                                    permit, DataQualityTestSupport.promotionPermit(run));
                            return trial.verifyObservedTraversal(
                                    ExpectedProvenance.CAPTURED_6908_BOUNDED_TRAVERSAL,
                                    binding(run),
                                    List.of(expectedRoot()),
                                    Map.of(1, 1),
                                    () -> false);
                        });
        assertEquals(1, verification.sql().expectedRoots());
        assertEquals(1, verification.observedPhysicalRows());
        assertEquals(0, verification.presenceComparedCells());
        assertEquals(false, verification.windowCompletenessProven());
        assertEquals(false, verification.childCompletenessProven());
        assertEquals(0, jdbc.physicalCommits);
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void setDivergenceStopsBeforeClaimingParityAndRollsBack() throws Exception {
        final UUID run = UUID.randomUUID();
        final var jdbc = new FakePhysical();
        jdbc.verificationRun = run;
        jdbc.divergeStage = true;
        final var failure =
                assertThrows(
                        ColetaShadowSetComparator.Mismatch.class,
                        () ->
                                ColetaShadowRollbackTrial.execute(
                                        jdbc.session(),
                                        trial -> {
                                            trial.stagingGateway().stage(batch(run), () -> false);
                                            final var permit =
                                                    ContractTestSupport.promotionPermit(run);
                                            trial.prepare(permit, () -> false);
                                            trial.promote(
                                                    permit,
                                                    DataQualityTestSupport.promotionPermit(run),
                                                    () -> false);
                                            trial.verifyObservedTraversal(
                                                    ExpectedProvenance.SYNTHETIC_FIXTURE,
                                                    binding(run),
                                                    List.of(expectedRoot()),
                                                    Map.of(1, 1),
                                                    () -> false);
                                            return null;
                                        }));
        assertEquals(1, failure.result().expectedOnlyStageTuples());
        assertEquals(0, jdbc.physicalCommits);
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void verificationReceiptRejectsUnprovenLabelsAndSqlDivergence() {
        final var matchingSql =
                new ColetaShadowSetComparator.Result(1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 1, 0);
        final var divergentSql =
                new ColetaShadowSetComparator.Result(1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 1, 0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ColetaShadowRollbackTrial.Verification(
                                matchingSql, "WINDOW_COMPLETE", 0, 1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ColetaShadowRollbackTrial.Verification(
                                divergentSql, "COMPARACAO_DE_RAIZES_OBSERVADAS", 0, 1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ColetaShadowRollbackTrial.Verification(
                                matchingSql, "COMPARACAO_DE_RAIZES_OBSERVADAS", -1, 1, 0));
    }

    @Test
    void crossExecutionStageAndSubsequentCheckpointFailClosed() throws Exception {
        final var jdbc = new FakePhysical();
        ColetaShadowRollbackTrial.execute(
                jdbc.session(),
                trial -> {
                    trial.stage(batch(UUID.randomUUID()), () -> false);
                    assertEquals(
                            "COL_SHADOW_STAGE_ORDER",
                            assertThrows(
                                            IllegalStateException.class,
                                            () ->
                                                    trial.stage(
                                                            batch(UUID.randomUUID()), () -> false))
                                    .getMessage());
                    assertEquals(
                            "COL_SHADOW_TRIAL_UNAVAILABLE",
                            assertThrows(SQLException.class, trial::checkpoint).getMessage());
                    return null;
                });
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void nonConsecutiveBatchAndInvalidAuditedPageStopBeforeSecondWrite() throws Exception {
        final var outOfOrder = new FakePhysical();
        assertEquals(
                "COL_SHADOW_BATCH_SEQUENCE",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        ColetaShadowRollbackTrial.execute(
                                                outOfOrder.session(),
                                                trial -> {
                                                    trial.stage(
                                                            batch(UUID.randomUUID(), 2),
                                                            () -> false);
                                                    return null;
                                                }))
                        .getMessage());
        assertEquals(0, outOfOrder.stagedBatches);
        assertEquals(List.of("rollback", "rollback", "close"), outOfOrder.lifecycle);

        final var invalidPage = new FakePhysical();
        invalidPage.auditedPage = 0;
        assertEquals(
                "COL_SHADOW_PAGE_AUDIT_REQUIRED",
                assertThrows(
                                SQLException.class,
                                () ->
                                        ColetaShadowRollbackTrial.execute(
                                                invalidPage.session(),
                                                trial -> {
                                                    trial.stage(
                                                            batch(UUID.randomUUID()), () -> false);
                                                    return null;
                                                }))
                        .getMessage());
        assertEquals(0, invalidPage.stagedBatches);
        assertEquals(List.of("rollback", "rollback", "close"), invalidPage.lifecycle);
    }

    @Test
    void promotionWithoutPreparationAndDuplicatePreparationStayUnpublished() throws Exception {
        final UUID run = UUID.randomUUID();
        final var unprepared = new FakePhysical();
        assertEquals(
                "COL_SHADOW_PROMOTION_ORDER",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        ColetaShadowRollbackTrial.execute(
                                                unprepared.session(),
                                                trial -> {
                                                    trial.stage(batch(run), () -> false);
                                                    trial.promote(
                                                            ContractTestSupport.promotionPermit(
                                                                    run),
                                                            DataQualityTestSupport.promotionPermit(
                                                                    run),
                                                            () -> false);
                                                    return null;
                                                }))
                        .getMessage());
        assertEquals(List.of("rollback", "rollback", "close"), unprepared.lifecycle);

        final var duplicate = new FakePhysical();
        assertEquals(
                "COL_SHADOW_PREPARE_ORDER",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        ColetaShadowRollbackTrial.execute(
                                                duplicate.session(),
                                                trial -> {
                                                    trial.stage(batch(run), () -> false);
                                                    final var permit =
                                                            ContractTestSupport.promotionPermit(
                                                                    run);
                                                    trial.prepare(permit, () -> false);
                                                    trial.prepare(permit, () -> false);
                                                    return null;
                                                }))
                        .getMessage());
        assertEquals(List.of("rollback", "rollback", "close"), duplicate.lifecycle);
    }

    @Test
    void missingTransactionReadbackFailsClosedBeforeAnyWrite() throws Exception {
        final var jdbc = new FakePhysical();
        assertEquals(
                "COL_SHADOW_TRANSACTION_UNOBSERVABLE",
                assertThrows(
                                SQLException.class,
                                () ->
                                        ColetaShadowRollbackTrial.execute(
                                                jdbc.session(),
                                                trial -> {
                                                    jdbc.transactionVisible = false;
                                                    trial.checkpoint();
                                                    return null;
                                                }))
                        .getMessage());
        assertEquals(0, jdbc.stagedBatches);
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    @Test
    void emptyExpectedTraversalCannotClaimParityAfterPromotion() throws Exception {
        final UUID run = UUID.randomUUID();
        final var jdbc = new FakePhysical();
        jdbc.verificationRun = run;
        assertEquals(
                "COL_SHADOW_EMPTY_EXPECTED_TRAVERSAL",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        ColetaShadowRollbackTrial.execute(
                                                jdbc.session(),
                                                trial -> {
                                                    trial.stage(batch(run), () -> false);
                                                    final var permit =
                                                            ContractTestSupport.promotionPermit(
                                                                    run);
                                                    trial.prepare(permit, () -> false);
                                                    trial.promote(
                                                            permit,
                                                            DataQualityTestSupport.promotionPermit(
                                                                    run),
                                                            () -> false);
                                                    trial.verifyObservedTraversal(
                                                            ExpectedProvenance.SYNTHETIC_FIXTURE,
                                                            binding(run),
                                                            List.of(),
                                                            Map.of(1, 1),
                                                            () -> false);
                                                    return null;
                                                }))
                        .getMessage());
        assertEquals(List.of("rollback", "rollback", "close"), jdbc.lifecycle);
    }

    private static Binding binding(final UUID run) {
        return new Binding(
                "synthetic-source",
                "synthetic-tenant",
                LocalDate.of(2026, 9, 30),
                "a".repeat(64),
                run,
                0);
    }

    private static ExpectedRoot expectedRoot() {
        return new ExpectedRoot(
                new ScopedSourceIdentity(
                        "synthetic-source",
                        "synthetic-tenant",
                        FirstWaveIdentityContract.Entity.COLETAS,
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.INTEGER, "INTEGER:10")),
                OptionalInt.of(1),
                Map.of(),
                Set.of(1));
    }

    private static ColetaStageBatch batch(final UUID run) {
        return batch(run, 1);
    }

    private static ColetaStageBatch batch(final UUID run, final int batchNumber) {
        return batch(run, batchNumber, 1);
    }

    private static ColetaStageBatch batch(final UUID run, final int batchNumber, final int size) {
        final var records = new ArrayList<ColetaStageRecord>();
        for (int ordinal = 1; ordinal <= size; ordinal++) {
            records.add(
                    ColetaStageRecord.valid(
                            ordinal,
                            new ScopedSourceIdentity.SourceKey(
                                    ScopedSourceIdentity.WireType.INTEGER, "INTEGER:10"),
                            ColetaAttributePresence.ABSENT,
                            null,
                            "{}",
                            "{}",
                            "{}",
                            ColetaStatus.resolve("done", null),
                            null,
                            null,
                            ColetaFreshnessOrigin.UNAVAILABLE));
        }
        return new ColetaStageBatch(
                run, batchNumber, records, Instant.parse("2026-09-30T12:00:00Z"));
    }

    private static String batchPages(final ColetaShadowRollbackTrial trial)
            throws ReflectiveOperationException {
        final var field = ColetaShadowRollbackTrial.class.getDeclaredField("batchPages");
        field.setAccessible(true);
        return (String) field.get(trial);
    }

    private static final class FakePhysical {
        private final List<String> preparedQueries = new ArrayList<>();
        private int sampleTerminalCount;
        private int sampleGenericRows = 1;
        private String sampleFingerprint = "a".repeat(64);
        private final List<String> lifecycle = new ArrayList<>();
        private final Map<String, Integer> callTimeouts = new HashMap<>();
        private String timeoutOperation;
        private UUID verificationRun;
        private boolean divergeStage;
        private boolean transactionVisible = true;
        private int depth;
        private int physicalConnections = 1;
        private int physicalCommits;
        private int stagedBatches;
        private boolean failStage;
        private int auditedPage = 1;

        private ColetaTemporalLaboratorySession session() throws Exception {
            final Connection physical =
                    (Connection)
                            Proxy.newProxyInstance(
                                    Connection.class.getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (proxy, method, args) ->
                                            switch (method.getName()) {
                                                case "createStatement" -> statement();
                                                case "prepareStatement" ->
                                                        preparedStatement((String) args[0]);
                                                case "prepareCall" ->
                                                        callStatement((String) args[0]);
                                                case "rollback" -> {
                                                    lifecycle.add("rollback");
                                                    depth = 0;
                                                    yield null;
                                                }
                                                case "commit" -> {
                                                    physicalCommits++;
                                                    depth = 0;
                                                    yield null;
                                                }
                                                case "close" -> {
                                                    lifecycle.add("close");
                                                    yield null;
                                                }
                                                case "isClosed" -> false;
                                                default -> defaultValue(method.getReturnType());
                                            });
            final var constructor =
                    ColetaTemporalLaboratorySession.class.getDeclaredConstructor(Connection.class);
            constructor.setAccessible(true);
            return constructor.newInstance(physical);
        }

        private Statement statement() {
            return (Statement)
                    Proxy.newProxyInstance(
                            Statement.class.getClassLoader(),
                            new Class<?>[] {Statement.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("execute")) {
                                    final String sql = (String) args[0];
                                    if (sql.equals("BEGIN TRANSACTION; BEGIN TRANSACTION;")) {
                                        depth += 2;
                                    } else if (sql.equals("BEGIN TRANSACTION")) {
                                        depth++;
                                    } else if (sql.equals("COMMIT TRANSACTION")) {
                                        depth--;
                                    }
                                    return false;
                                }
                                return defaultValue(method.getReturnType());
                            });
        }

        private PreparedStatement transactionStatement() {
            return (PreparedStatement)
                    Proxy.newProxyInstance(
                            PreparedStatement.class.getClassLoader(),
                            new Class<?>[] {PreparedStatement.class},
                            (proxy, method, args) ->
                                    method.getName().equals("executeQuery")
                                            ? transactionRows()
                                            : defaultValue(method.getReturnType()));
        }

        private PreparedStatement preparedStatement(final String sql) {
            preparedQueries.add(sql);
            if (sql.contains("MAX(page_number)")) {
                return pageStatement();
            }
            if (sql.contains("SELECT p.source_instance")) {
                return queryStatement(
                        sql.contains("a.current_state=N'FAILED'")
                                ? sampleBindingRows()
                                : bindingRows());
            }
            if (sql.startsWith("WITH expected AS")) {
                return queryStatement(
                        sql.contains("missing_core") ? comparisonRows() : sampleComparisonRows());
            }
            if (sql.startsWith("SELECT r.input_batch_number")) {
                return queryStatement(observedRows());
            }
            return transactionStatement();
        }

        private PreparedStatement queryStatement(final ResultSet rows) {
            return (PreparedStatement)
                    Proxy.newProxyInstance(
                            PreparedStatement.class.getClassLoader(),
                            new Class<?>[] {PreparedStatement.class},
                            (proxy, method, args) ->
                                    method.getName().equals("executeQuery")
                                            ? rows
                                            : defaultValue(method.getReturnType()));
        }

        private PreparedStatement pageStatement() {
            return (PreparedStatement)
                    Proxy.newProxyInstance(
                            PreparedStatement.class.getClassLoader(),
                            new Class<?>[] {PreparedStatement.class},
                            (proxy, method, args) ->
                                    method.getName().equals("executeQuery")
                                            ? pageRows()
                                            : defaultValue(method.getReturnType()));
        }

        private ResultSet pageRows() {
            return (ResultSet)
                    Proxy.newProxyInstance(
                            ResultSet.class.getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            new java.lang.reflect.InvocationHandler() {
                                private int cursor;

                                @Override
                                public Object invoke(
                                        final Object proxy,
                                        final java.lang.reflect.Method method,
                                        final Object[] args) {
                                    return switch (method.getName()) {
                                        case "next" -> ++cursor == 1;
                                        case "getInt" -> auditedPage;
                                        case "wasNull" -> false;
                                        default -> defaultValue(method.getReturnType());
                                    };
                                }
                            });
        }

        private ResultSet transactionRows() {
            return (ResultSet)
                    Proxy.newProxyInstance(
                            ResultSet.class.getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            new java.lang.reflect.InvocationHandler() {
                                private int cursor;

                                @Override
                                public Object invoke(
                                        final Object proxy,
                                        final java.lang.reflect.Method method,
                                        final Object[] args) {
                                    if (method.getName().equals("next")) {
                                        return transactionVisible && ++cursor == 1;
                                    }
                                    if (method.getName().equals("getInt")) {
                                        return switch ((int) args[0]) {
                                            case 1 -> depth;
                                            case 2 -> depth > 0 ? 1 : 0;
                                            case 3 -> 57;
                                            default -> throw new AssertionError();
                                        };
                                    }
                                    return defaultValue(method.getReturnType());
                                }
                            });
        }

        private ResultSet bindingRows() {
            final var row = new HashMap<Object, Object>();
            final var date = LocalDate.of(2026, 9, 30);
            final var zone = ZoneId.of("America/Sao_Paulo");
            row.put(1, "synthetic-source");
            row.put(2, "synthetic-tenant");
            row.put(3, Timestamp.from(date.atStartOfDay(zone).toInstant()));
            row.put(4, Timestamp.from(date.plusDays(1).atStartOfDay(zone).toInstant()));
            row.put(5, "a".repeat(64));
            row.put(6, verificationRun.toString());
            row.put(7, 1L);
            row.put(8, 2);
            return rows(row);
        }

        private ResultSet comparisonRows() {
            final var row = new HashMap<Object, Object>();
            for (int column = 1; column <= 12; column++) {
                row.put(column, column <= 6 || column == 11 ? 1L : 0L);
            }
            row.put(7, divergeStage ? 1L : 0L);
            return rows(row);
        }

        private ResultSet sampleBindingRows() {
            final var row = new HashMap<Object, Object>();
            final var date = LocalDate.of(2026, 9, 30);
            final var zone = ZoneId.of("America/Sao_Paulo");
            row.put(1, "synthetic-source");
            row.put(2, "synthetic-tenant");
            row.put(3, Timestamp.from(date.atStartOfDay(zone).toInstant()));
            row.put(4, Timestamp.from(date.plusDays(1).atStartOfDay(zone).toInstant()));
            row.put(5, sampleFingerprint);
            row.put(6, verificationRun.toString());
            row.put(7, 1L);
            row.put(8, 1L);
            row.put(9, 1L);
            row.put(10, (long) sampleTerminalCount);
            return rows(row);
        }

        private ResultSet sampleComparisonRows() {
            final var row = new HashMap<Object, Object>();
            for (int column = 1; column <= 9; column++) {
                row.put(column, column <= 4 ? 1L : 0L);
            }
            row.put(5, divergeStage ? 1L : 0L);
            row.put(7, (long) sampleGenericRows);
            return rows(row);
        }

        private ResultSet observedRows() {
            final var row = new HashMap<Object, Object>();
            row.put(1, 1);
            row.put(2, "INTEGER:10");
            row.put(3, "{}");
            return rows(row);
        }

        private ResultSet publicationRows() {
            final var row = new HashMap<Object, Object>();
            row.put("execution_id", verificationRun.toString());
            row.put("candidate_rows", 1L);
            row.put("inserted_rows", 1L);
            row.put("updated_rows", 0L);
            row.put("reactivated_rows", 0L);
            row.put("noop_rows", 0L);
            row.put("stale_noop_rows", 0L);
            row.put("reconciled_at_utc", Timestamp.from(Instant.parse("2026-09-30T12:00:00Z")));
            row.put("published_at_utc", Timestamp.from(Instant.parse("2026-09-30T12:00:01Z")));
            return rows(row);
        }

        private ResultSet rows(final Map<Object, Object> row) {
            return (ResultSet)
                    Proxy.newProxyInstance(
                            ResultSet.class.getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            new java.lang.reflect.InvocationHandler() {
                                private int cursor;
                                private boolean wasNull;

                                @Override
                                public Object invoke(
                                        final Object proxy,
                                        final java.lang.reflect.Method method,
                                        final Object[] args) {
                                    if (method.getName().equals("next")) {
                                        return ++cursor == 1;
                                    }
                                    if (method.getName().equals("wasNull")) {
                                        return wasNull;
                                    }
                                    if (method.getName().equals("getString")
                                            || method.getName().equals("getInt")
                                            || method.getName().equals("getLong")
                                            || method.getName().equals("getTimestamp")) {
                                        final Object value = row.get(args[0]);
                                        wasNull = value == null;
                                        return switch (method.getName()) {
                                            case "getString" ->
                                                    value == null ? null : value.toString();
                                            case "getInt" ->
                                                    value == null ? 0 : ((Number) value).intValue();
                                            case "getLong" ->
                                                    value == null
                                                            ? 0L
                                                            : ((Number) value).longValue();
                                            default -> value;
                                        };
                                    }
                                    return defaultValue(method.getReturnType());
                                }
                            });
        }

        private CallableStatement callStatement(final String sql) {
            final String operation = sql.substring(sql.indexOf('.') + 1, sql.indexOf('('));
            return (CallableStatement)
                    Proxy.newProxyInstance(
                            CallableStatement.class.getClassLoader(),
                            new Class<?>[] {CallableStatement.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("setQueryTimeout")) {
                                    callTimeouts.put(operation, (int) args[0]);
                                    return null;
                                }
                                if (method.getName().equals("executeUpdate")) {
                                    if (operation.equals(timeoutOperation)) {
                                        throw new SQLTimeoutException("SYNTHETIC_TIMEOUT");
                                    }
                                    return 1;
                                }
                                if (method.getName().equals("executeQuery")
                                        && operation.equals(timeoutOperation)) {
                                    throw new SQLTimeoutException("SYNTHETIC_TIMEOUT");
                                }
                                if (method.getName().equals("executeQuery")
                                        && operation.equals(
                                                "usp_apply_reconcile_publish_coletas")) {
                                    return publicationRows();
                                }
                                if (method.getName().equals("executeBatch")) {
                                    if (failStage) {
                                        throw new SQLException("SYNTHETIC_STAGE_FAILURE");
                                    }
                                    depth++;
                                    depth--;
                                    stagedBatches++;
                                    return new int[] {1};
                                }
                                return defaultValue(method.getReturnType());
                            });
        }

        private static Object defaultValue(final Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            return null;
        }
    }
}
