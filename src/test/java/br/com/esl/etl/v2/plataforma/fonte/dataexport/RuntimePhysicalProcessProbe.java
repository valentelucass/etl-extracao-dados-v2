package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryExpectation;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/** Separate JVM entrypoint available only in test-classes; never in the official JAR. */
public final class RuntimePhysicalProcessProbe {
    private RuntimePhysicalProcessProbe() {}

    public static void main(final String[] args) throws Exception {
        if (!"true".equals(System.getProperty("runtime.recovery.local.integration.enabled"))
                || !"true"
                        .equals(
                                System.getProperty(
                                        "runtime.recovery.local.integration.profile.active"))) {
            throw new IllegalStateException("PHYSICAL_CHILD_OPT_IN_REQUIRED");
        }
        if (args.length != 5 && args.length != 6) {
            throw new IllegalArgumentException("PHYSICAL_ARGUMENTS_REQUIRED");
        }
        final var campaign = new RuntimePhysicalCampaign(Path.of(args[0]));
        final UUID execution = UUID.fromString(args[1]);
        campaign.requireReservation(execution);
        if (args[3].equals("authority")) {
            br.com.esl.etl.v2.plataforma.autorizacao.WindowsRuntimeAuthorityPhysicalHarness
                    .verifyDurableDenial(new RuntimePhysicalDataSource("none"), execution);
            campaign.checkDeadline();
            return;
        }
        if (args[3].equals("temporal")) {
            temporal(execution);
            campaign.checkDeadline();
            return;
        }
        final DataExportTemplate template = DataExportTemplate.valueOf(args[2]);
        final boolean writer = args[3].equals("write");
        if (!writer
                && !java.util.Set.of("recover", "race", "cancel-lock", "refuse", "revoke-policy")
                        .contains(args[3])) {
            throw new IllegalArgumentException("PHYSICAL_ACTION_REQUIRED");
        }
        if (!java.util.Set.of(
                        "none",
                        "lost-ack",
                        "sealed",
                        "before-apply",
                        "partial",
                        "expired",
                        "revoked",
                        "lease-race",
                        "apply-rollback",
                        "cancel-commit",
                        "apply-race",
                        "incremental",
                        "replay",
                        "col03-base",
                        "col03-next",
                        "legacy-coleta")
                .contains(args[4])) {
            throw new IllegalArgumentException("FAULT_REQUIRED");
        }
        final UUID origin = args.length == 6 ? UUID.fromString(args[5]) : execution;
        campaign.requireReservation(origin);
        final String namespace = "SYNTHETIC_B53_" + origin.toString().replace("-", "");
        final var mode =
                args[4].equals("incremental")
                        ? br.com.esl.etl.v2.plataforma.controle.ExecutionMode.INCREMENTAL
                        : args[4].equals("replay")
                                ? br.com.esl.etl.v2.plataforma.controle.ExecutionMode.REPLAY
                                : br.com.esl.etl.v2.plataforma.controle.ExecutionMode.BACKFILL;
        final Path evidence = Path.of("target/bloco53", execution + ".properties");
        if (!writer && args[4].equals("apply-race")) {
            final long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
            while (true) {
                try (var paths = Files.list(Path.of("target/bloco53"))) {
                    if (paths.anyMatch(
                            path ->
                                    path.getFileName().toString().startsWith(execution + ".")
                                            && path.toString().endsWith(".ready"))) {
                        break;
                    }
                }
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("APPLY_BARRIER_DEADLINE");
                }
                Thread.sleep(25);
            }
        }
        final var dataSource =
                new RuntimePhysicalDataSource(
                        writer
                                ? java.util.Set.of("expired", "lease-race", "revoked")
                                                .contains(args[4])
                                        ? "sealed"
                                        : args[4]
                                : "none");
        final var metadata = new Properties();
        final DataQualityPolicyReference policy;
        if (writer) {
            Files.createFile(Path.of("target/bloco53", execution + ".admitted"));
            policy = seedPolicy(dataSource, namespace, template, mode.name());
            metadata.setProperty("policy.version", policy.version());
            metadata.setProperty("policy.sha256", policy.sha256());
            try (var output = Files.newBufferedWriter(evidence)) {
                metadata.store(output, "Synthetic occurrence metadata; no source payload");
            }
        } else {
            try (var reader = Files.newBufferedReader(evidence)) {
                metadata.load(reader);
            }
            policy =
                    new DataQualityPolicyReference(
                            metadata.getProperty("policy.version"),
                            metadata.getProperty("policy.sha256"));
        }
        final var fixture =
                new RuntimePhysicalFixture.Fixture(dataSource, execution, namespace, policy);
        if (args[3].equals("revoke-policy")) {
            try (var connection = dataSource.getConnection();
                    var query =
                            connection.prepareStatement(
                                    "UPDATE ctl.data_quality_policy SET policy_state=N'REVOKED'"
                                            + " WHERE policy_version=? AND policy_fingerprint=?"
                                            + " AND policy_version LIKE ? AND policy_state=N'RATIFIED'")) {
                query.setString(1, policy.version());
                query.setString(2, policy.sha256());
                query.setString(3, namespace + "[_]%");
                if (query.executeUpdate() != 1) {
                    throw new IllegalStateException("OWN_SYNTHETIC_POLICY_REVOCATION_REQUIRED");
                }
            }
            System.out.println("SYNTHETIC_POLICY_REVOKED_WITH_VALID_TRIGGER");
            return;
        }
        fixture.mode = mode;
        if (args[4].equals("cancel-commit")) {
            dataSource.cancelAfterCommit = fixture.cancellation::cancel;
        }
        if (!writer && args[4].equals("apply-race")) {
            fixture.barrier = execution.toString();
        }
        if (args[4].equals("replay")) {
            if (origin.equals(execution)) {
                throw new IllegalArgumentException("REPLAY_ORIGIN_REQUIRED");
            }
            fixture.replay = java.util.Optional.of(origin);
        }
        if (args[4].equals("col03-base")) {
            fixture.status = "pending";
            fixture.freshness = RuntimePhysicalFixture.NOW.plusSeconds(60);
        }
        if (java.util.Set.of("expired", "lease-race").contains(args[4])) {
            fixture.lease = java.time.Duration.ofSeconds(10);
        }
        final var work =
                new RuntimePhysicalFixture.Work(fixture, template, "physical-work", execution);
        final var handler = work.handler;
        work.handler =
                session -> {
                    try {
                        handler.execute(session);
                    } catch (final RuntimeException failure) {
                        if (args[4].equals("none")) {
                            failure.printStackTrace();
                        }
                        throw failure;
                    }
                };
        if (writer) {
            if (args[4].equals("incremental")) {
                fixture.plan(work)
                        .forEach(
                                item -> {
                                    fixture.control.registerSource(
                                            new br.com.esl.etl.v2.plataforma.controle
                                                    .ControlPlaneSource(
                                                    namespace, "ESL", RuntimePhysicalFixture.NOW));
                                    fixture.control.registerIncrementalFrontier(
                                            item.partition(),
                                            RuntimePhysicalFixture.NOW,
                                            RuntimePhysicalFixture.NOW);
                                });
            }
            final var result =
                    fixture.dispatcher(work)
                            .dispatch(fixture.plan(work), fixture.cancellation)
                            .result(work.id);
            final String expected =
                    java.util.Set.of(
                                            "none",
                                            "incremental",
                                            "replay",
                                            "col03-base",
                                            "col03-next",
                                            "legacy-coleta")
                                    .contains(args[4])
                            ? "PUBLISHED"
                            : "RECOVERY_REQUIRED";
            if (!result.status().name().equals(expected)
                    && !(args[4].equals("apply-race")
                            && result.status().name().equals("PUBLISHED"))) {
                result.recovery()
                        .ifPresent(
                                session ->
                                        session.failureCause()
                                                .ifPresent(Throwable::printStackTrace));
                throw new IllegalStateException("PHYSICAL_DISPATCH_" + result.status());
            }
            if (work.fetches.get() != 2) {
                throw new IllegalStateException("PHYSICAL_FETCH_COUNT");
            }
            System.out.println("WRITER_PASS " + template + " " + args[4] + " " + result.status());
        } else {
            if (args[3].equals("refuse")) {
                if (args[4].equals("lease-race")) {
                    fixture.barrier = "LEASE";
                }
                if (args[4].equals("expired")) {
                    Thread.sleep(11000);
                }
                final var refusal =
                        fixture.dispatcher(work)
                                .recover(
                                        fixture.plan(work),
                                        fixture.cancellation,
                                        new RuntimeRecoveryExpectation(
                                                work.id, work.binding, policy));
                final String expected =
                        args[4].equals("revoked")
                                ? "DQ_OBSOLETE"
                                : args[4].equals("legacy-coleta")
                                        ? "EVIDENCE_MISSING"
                                        : args[4].equals("partial")
                                                ? "PARTIAL_EXTRACTION"
                                                : "LEASE_LOST";
                if (!refusal.snapshot(work.id).reason().name().equals(expected)
                        || work.fetches.get() != 0) {
                    throw new IllegalStateException(
                            "PHYSICAL_REFUSAL_" + refusal.snapshot(work.id).reason());
                }
                System.out.println("REFUSAL_PASS " + template + " " + expected + " fetches=0");
                return;
            }
            if (args[3].equals("race")) {
                fixture.barrier = execution.toString();
            }
            if (args[3].equals("cancel-lock")) {
                try (var lock = dataSource.getConnection();
                        var query =
                                lock.prepareStatement(
                                        "SELECT execution_id FROM ctl.execution_attempt WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=?")) {
                    lock.setAutoCommit(false);
                    query.setString(1, execution.toString());
                    try (var row = query.executeQuery()) {
                        if (!row.next()) {
                            throw new IllegalStateException("LOCK_TARGET_REQUIRED");
                        }
                    }
                    final var timer =
                            java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                    try {
                        timer.schedule(
                                fixture.cancellation::cancel,
                                300,
                                java.util.concurrent.TimeUnit.MILLISECONDS);
                        final long started = System.nanoTime();
                        final var cancelled =
                                fixture.dispatcher(work)
                                        .recover(
                                                fixture.plan(work),
                                                fixture.cancellation,
                                                new RuntimeRecoveryExpectation(
                                                        work.id, work.binding, policy));
                        if (cancelled.snapshot(work.id).publication().isPresent()
                                || work.fetches.get() != 0
                                || System.nanoTime() - started
                                        > java.time.Duration.ofSeconds(12).toNanos()) {
                            throw new IllegalStateException("LOCK_CANCELLATION_FAILED");
                        }
                    } finally {
                        timer.shutdownNow();
                        lock.rollback();
                    }
                }
                System.out.println(
                        "CANCELLATION_PASS real_lock SQL_attention rollback_owned_connection");
                return;
            }
            final var result =
                    fixture.dispatcher(work)
                            .recover(
                                    fixture.plan(work),
                                    fixture.cancellation,
                                    new RuntimeRecoveryExpectation(work.id, work.binding, policy));
            result.failureCause(work.id).ifPresent(Throwable::printStackTrace);
            final var snapshot = result.snapshot(work.id);
            if (!snapshot.reason().name().equals("PUBLISHED") || work.fetches.get() != 0) {
                throw new IllegalStateException("PHYSICAL_RECOVERY_" + snapshot.reason());
            }
            final var receipt = snapshot.publication().orElseThrow();
            if (receipt.candidateRows() != 3
                    || receipt.insertedRows()
                            != (args[4].equals("replay") || args[4].equals("col03-next") ? 0 : 3)
                    || receipt.updatedRows() != (args[4].equals("col03-next") ? 3 : 0)
                    || receipt.reactivatedRows() != 0
                    || receipt.noopRows() != (args[4].equals("replay") ? 3 : 0)
                    || receipt.staleNoopRows() != 0
                    || receipt.incrementalFrontierBefore().isPresent()
                            != args[4].equals("incremental")
                    || receipt.incrementalFrontierAfter().isPresent()
                            != args[4].equals("incremental")
                    || !receipt.executionId().equals(execution)
                    || receipt.reconciledAt().isAfter(receipt.publishedAt())) {
                throw new IllegalStateException("PHYSICAL_EXACT_RECEIPT");
            }
            if (args[4].equals("incremental")
                    && (!receipt.incrementalFrontierBefore()
                                    .orElseThrow()
                                    .equals(RuntimePhysicalFixture.NOW)
                            || !receipt.incrementalFrontierAfter()
                                    .orElseThrow()
                                    .equals(RuntimePhysicalFixture.NOW.plusSeconds(3600)))) {
                throw new IllegalStateException("INCREMENTAL_EXACT_FRONTIER");
            }
            compareIndependentSqlReceipt(dataSource, receipt, template);
            try (var connection = dataSource.getConnection();
                    var query =
                            connection.prepareStatement(
                                    "SELECT (SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id=?)"
                                            + ",(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WHERE execution_id=?)"
                                            + ",(SELECT COUNT_BIG(*) FROM ctl.execution_page_audit WHERE execution_id=?)")) {
                for (int index = 1; index <= 3; index++) {
                    query.setString(index, execution.toString());
                }
                try (var row = query.executeQuery()) {
                    if (!row.next()
                            || row.getLong(1) != 1
                            || row.getLong(2) != 3
                            || row.getLong(3) != 2
                            || row.next()) {
                        throw new IllegalStateException("PHYSICAL_DUPLICATE_EFFECT");
                    }
                }
            }
            System.out.println(
                    "READER_PASS "
                            + template
                            + " PUBLISHED exact_fields=11 fetches=0 publication=1 candidates=3 pages=2");
        }
        campaign.checkDeadline();
    }

    static DataQualityPolicyReference seedPolicy(
            final RuntimePhysicalDataSource dataSource,
            final String namespace,
            final DataExportTemplate template,
            final String mode)
            throws Exception {
        final String sql =
                Files.readString(Path.of("src/test/resources/runtime-physical/seed-policy.sql"));
        try (var connection = dataSource.getConnection();
                var query = connection.prepareStatement(sql)) {
            query.setString(1, namespace);
            query.setString(2, template == DataExportTemplate.COLETAS ? "coletas" : "fretes");
            query.setString(3, mode);
            try (var row = query.executeQuery()) {
                if (!row.next()) {
                    throw new IllegalStateException("POLICY_RECEIPT_REQUIRED");
                }
                final var policy =
                        new DataQualityPolicyReference(row.getString(1), row.getString(2));
                if (row.next()) {
                    throw new IllegalStateException("POLICY_RECEIPT_DUPLICATED");
                }
                return policy;
            }
        }
    }

    private static void compareIndependentSqlReceipt(
            final RuntimePhysicalDataSource source,
            final br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult
                    expected,
            final DataExportTemplate template)
            throws Exception {
        final String table =
                template == DataExportTemplate.COLETAS
                        ? "ctl.runtime_coleta_publication_receipt"
                        : "recon.execution_reconciliation_result";
        try (var connection = source.getConnection();
                var query =
                        connection.prepareStatement(
                                "SELECT r.execution_id,r.candidate_rows,r.inserted_rows,r.updated_rows,"
                                        + "r.reactivated_rows,r.noop_rows,r.stale_noop_rows,r.reconciled_at_utc,"
                                        + "r.published_at_utc,p.incremental_frontier_before_utc,p.incremental_frontier_after_utc FROM "
                                        + table
                                        + " r JOIN ctl.execution_publication_event p ON p.execution_id=r.execution_id"
                                        + " WHERE r.execution_id=?")) {
            query.setString(1, expected.executionId().toString());
            try (var row = query.executeQuery()) {
                if (!row.next()) {
                    throw new IllegalStateException("INDEPENDENT_SQL_RECEIPT_MISSING");
                }
                final var calendar =
                        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                final var before = row.getTimestamp(10, calendar);
                final var after = row.getTimestamp(11, calendar);
                final var actual =
                        new br.com.esl.etl.v2.plataforma.persistencia.staging
                                .StagingPublicationResult(
                                UUID.fromString(row.getString(1)),
                                row.getLong(2),
                                row.getLong(3),
                                row.getLong(4),
                                row.getLong(5),
                                row.getLong(6),
                                row.getLong(7),
                                row.getTimestamp(8, calendar).toInstant(),
                                row.getTimestamp(9, calendar).toInstant(),
                                java.util.Optional.ofNullable(before)
                                        .map(java.sql.Timestamp::toInstant),
                                java.util.Optional.ofNullable(after)
                                        .map(java.sql.Timestamp::toInstant));
                if (!expected.equals(actual) || row.next()) {
                    throw new IllegalStateException("INDEPENDENT_SQL_RECEIPT_MISMATCH");
                }
            }
        }
    }

    private static void temporal(final UUID plan) throws Exception {
        final var source = new RuntimePhysicalDataSource("none");
        final var store =
                new br.com.esl.etl.v2.plataforma.persistencia.controle.JdbcSqlServerTemporalPlan(
                        source);
        final var coordinator =
                new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalCoordinator(store);
        final var policy =
                new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy(
                        "synthetic-b53-temporal-v1",
                        java.time.ZoneId.of("Etc/UTC"),
                        br.com.esl.etl.v2.plataforma.controle.ExecutionMode.BACKFILL,
                        br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy.INTERVAL,
                        br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy.Cadence
                                .CIVIL_DAY,
                        java.time.LocalTime.MIDNIGHT,
                        java.time.Duration.ofHours(2),
                        java.time.Duration.ofHours(1),
                        java.time.Duration.ofHours(4),
                        java.time.Duration.ofHours(2),
                        1,
                        3,
                        4,
                        0,
                        java.util.List.of());
        final String namespace =
                java.util.HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                plan.toString()
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8)));
        final java.time.Instant start = java.time.Instant.parse("2024-02-01T00:00:00Z");
        final var windows =
                coordinator.persistCatchUp(
                        plan,
                        namespace,
                        policy,
                        java.time.LocalDate.of(2024, 2, 1),
                        start.plusSeconds(10 * 86400));
        final var gaps = coordinator.reconcile(namespace, policy, start);
        if (windows.windows().size() != 3
                || !windows.backlogRemaining()
                || !gaps.contiguousEnd().equals(start)
                || gaps.pending().size() != 1
                || gaps.pending().get(0).action()
                        != br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalCoordinator
                                .NextAction.START_ORIGINAL) {
            throw new IllegalStateException("TEMPORAL_PHYSICAL_PLAN_OR_GAP");
        }
        try (var connection = source.getConnection();
                var query =
                        connection.prepareStatement(
                                "SELECT COUNT_BIG(*) FROM ctl.runtime_temporal_window WHERE plan_id=?")) {
            query.setString(1, plan.toString());
            try (var row = query.executeQuery()) {
                if (!row.next() || row.getLong(1) != 3 || row.next()) {
                    throw new IllegalStateException("TEMPORAL_PHYSICAL_DUPLICATION");
                }
            }
        }
        System.out.println(
                "TEMPORAL_JDBC_PASS windows=3 bounded_pending=1 missing_execution_retained=1 frontier_not_advanced=1");
    }
}
