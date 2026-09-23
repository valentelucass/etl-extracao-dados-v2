package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RuntimeRecoveryLocalIntegrationIT {
    @Test
    void qualifiesPolicyRevocationAndSqlOnlyHistoricalReadback() throws Exception {
        assertEquals("true", System.getProperty("runtime.recovery.local.integration.enabled"));
        assertEquals(
                "true", System.getProperty("runtime.recovery.local.integration.profile.active"));
        final String manifest = System.getProperty("runtime.recovery.local.integration.manifest");
        final var campaign = new RuntimePhysicalCampaign(Path.of(manifest));
        for (final var template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            final UUID published = campaign.reserve();
            run(manifest, published, template, "write", "none");
            run(manifest, published, template, "recover", "none");
            run(manifest, published, template, "revoke-policy", "none");
            run(manifest, published, template, "recover", "none");
            final UUID sealed = campaign.reserve();
            run(manifest, sealed, template, "write", "revoked");
            run(manifest, sealed, template, "revoke-policy", "revoked");
            run(manifest, sealed, template, "refuse", "revoked");
        }
    }

    @Test
    void qualifiesDurableDenialAndTemporalPlansAcrossProcesses() throws Exception {
        assertEquals("true", System.getProperty("runtime.recovery.local.integration.enabled"));
        assertEquals(
                "true", System.getProperty("runtime.recovery.local.integration.profile.active"));
        final String manifest = System.getProperty("runtime.recovery.local.integration.manifest");
        final var campaign = new RuntimePhysicalCampaign(Path.of(manifest));
        for (final String action : new String[] {"authority", "temporal"}) {
            final UUID id = campaign.reserve();
            if (action.equals("temporal")) {
                // Three planned occurrences share one persisted plan; admit all three before I/O.
                campaign.reserve();
                campaign.reserve();
            }
            run(manifest, id, DataExportTemplate.COLETAS, action, "none");
            run(manifest, id, DataExportTemplate.COLETAS, action, "none");
        }
    }

    @Test
    void qualifiesLegacyMissingReceiptAndIndependentDependencyPublication() throws Exception {
        assertEquals("true", System.getProperty("runtime.recovery.local.integration.enabled"));
        assertEquals(
                "true", System.getProperty("runtime.recovery.local.integration.profile.active"));
        final String manifest = System.getProperty("runtime.recovery.local.integration.manifest");
        final var campaign = new RuntimePhysicalCampaign(Path.of(manifest));
        final UUID legacy = campaign.reserve();
        run(manifest, legacy, DataExportTemplate.COLETAS, "write", "legacy-coleta");
        run(manifest, legacy, DataExportTemplate.COLETAS, "refuse", "legacy-coleta");
        final var source = new RuntimePhysicalDataSource("none");
        final UUID cycle = UUID.randomUUID();
        final var works = new RuntimePhysicalFixture.Work[3];
        RuntimePhysicalFixture.Fixture fixture = null;
        for (int index = 0; index < 3; index++) {
            final UUID execution = campaign.reserve();
            final String namespace =
                    index == 2
                            ? works[1].fixture.namespace
                            : "SYNTHETIC_B53_" + execution.toString().replace("-", "");
            final var template =
                    index == 2 ? DataExportTemplate.FRETES : DataExportTemplate.COLETAS;
            final var policy =
                    RuntimePhysicalProcessProbe.seedPolicy(source, namespace, template, "BACKFILL");
            fixture = new RuntimePhysicalFixture.Fixture(source, cycle, namespace, policy);
            works[index] =
                    new RuntimePhysicalFixture.Work(
                            fixture,
                            template,
                            "work-" + index,
                            execution,
                            index == 2
                                    ? new br.com.esl.etl.v2.plataforma.orquestracao
                                                    .RuntimeWorkloadId[] {works[1].id}
                                    : new br.com.esl.etl.v2.plataforma.orquestracao
                                                    .RuntimeWorkloadId[0]);
        }
        works[1].drift = true;
        final var plan =
                br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry.of(
                                works[0].definition, works[1].definition, works[2].definition)
                        .plan(
                                new br.com.esl.etl.v2.plataforma.orquestracao
                                        .RuntimePlanningRequest(
                                        cycle,
                                        "LOCAL_SHADOW",
                                        RuntimePhysicalFixture.PLAN,
                                        RuntimePhysicalFixture.NOW,
                                        works[0].request,
                                        works[1].request,
                                        works[2].request));
        final var dispatcher =
                new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatcher(
                        fixture.control,
                        fixture.clock,
                        fixture.recoveryPort(),
                        new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchBinding(
                                works[0].id, works[0].handler),
                        new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchBinding(
                                works[1].id, works[1].handler),
                        new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchBinding(
                                works[2].id, works[2].handler));
        final var result = dispatcher.dispatch(plan, fixture.cancellation);
        assertEquals("PUBLISHED", result.result(works[0].id).status().name());
        assertEquals("FAILED", result.result(works[1].id).status().name());
        assertEquals("BLOCKED", result.result(works[2].id).status().name());
        assertEquals(0, works[2].fetches.get());
        try (var connection = source.getConnection();
                var query =
                        connection.prepareStatement(
                                "SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id IN(?,?,?)")) {
            for (int i = 0; i < 3; i++) {
                query.setString(i + 1, works[i].execution.toString());
            }
            try (var row = query.executeQuery()) {
                assertTrue(row.next());
                assertEquals(1, row.getLong(1));
            }
        }
        System.out.println(
                "DEPENDENCY_PHYSICAL_PASS independent=PUBLISHED failed=FAILED dependent=BLOCKED dependent_fetches=0 publication=1 outcome="
                        + result.outcome());
    }

    @Test
    void qualifiesRollbackApplyRaceAndLeaseFencing() throws Exception {
        assertEquals("true", System.getProperty("runtime.recovery.local.integration.enabled"));
        assertEquals(
                "true", System.getProperty("runtime.recovery.local.integration.profile.active"));
        final String manifest = System.getProperty("runtime.recovery.local.integration.manifest");
        final var campaign = new RuntimePhysicalCampaign(Path.of(manifest));
        for (final var template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            for (final String fault : new String[] {"apply-rollback", "cancel-commit"}) {
                final UUID id = campaign.reserve();
                run(manifest, id, template, "write", fault);
                run(manifest, id, template, "recover", fault);
                run(manifest, id, template, "recover", fault);
            }
            final UUID lease = campaign.reserve();
            run(manifest, lease, template, "write", "lease-race");
            run(manifest, lease, template, "refuse", "lease-race");
            final UUID race = campaign.reserve();
            final var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                final var writer =
                        workers.submit(
                                () -> {
                                    run(manifest, race, template, "write", "apply-race");
                                    return true;
                                });
                final var reader =
                        workers.submit(
                                () -> {
                                    run(manifest, race, template, "recover", "apply-race");
                                    return true;
                                });
                assertTrue(writer.get(60, TimeUnit.SECONDS));
                assertTrue(reader.get(60, TimeUnit.SECONDS));
            } finally {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
            }
            run(manifest, race, template, "recover", "none");
        }
    }

    @Test
    void qualifiesReplayIncrementalAndHistoricalTypedReceipts() throws Exception {
        assertEquals("true", System.getProperty("runtime.recovery.local.integration.enabled"));
        assertEquals(
                "true", System.getProperty("runtime.recovery.local.integration.profile.active"));
        final String manifest = System.getProperty("runtime.recovery.local.integration.manifest");
        final var campaign = new RuntimePhysicalCampaign(Path.of(manifest));
        for (final var template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            final UUID original = campaign.reserve();
            run(manifest, original, template, "write", "incremental");
            run(manifest, original, template, "recover", "incremental");
            final UUID replay = campaign.reserve();
            run(manifest, replay, template, "write", "replay", original);
            run(manifest, replay, template, "recover", "replay", original);
            run(manifest, original, template, "recover", "incremental");
        }
        final UUID original = campaign.reserve();
        run(manifest, original, DataExportTemplate.COLETAS, "write", "col03-base");
        run(manifest, original, DataExportTemplate.COLETAS, "recover", "col03-base");
        final UUID terminal = campaign.reserve();
        run(manifest, terminal, DataExportTemplate.COLETAS, "write", "col03-next", original);
        run(manifest, terminal, DataExportTemplate.COLETAS, "recover", "col03-next", original);
        run(manifest, original, DataExportTemplate.COLETAS, "recover", "col03-base");
    }

    @Test
    void qualifiesCommittedRecoveryAcrossIndependentJavaProcesses() throws Exception {
        assertEquals(
                "true",
                System.getProperty("runtime.recovery.local.integration.enabled"),
                "explicit physical opt-in required");
        assertEquals(
                "true",
                System.getProperty("runtime.recovery.local.integration.profile.active"),
                "profile required");
        final String manifest = System.getProperty("runtime.recovery.local.integration.manifest");
        final var campaign = new RuntimePhysicalCampaign(Path.of(manifest));
        for (final var template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            for (final String negative : new String[] {"partial", "expired"}) {
                final UUID refused = campaign.reserve();
                run(manifest, refused, template, "write", negative);
                run(manifest, refused, template, "refuse", negative);
            }
            for (final String fault : new String[] {"none", "lost-ack", "sealed", "before-apply"}) {
                final UUID execution = campaign.reserve();
                run(manifest, execution, template, "write", fault);
                run(manifest, execution, template, "recover", fault);
                run(manifest, execution, template, "recover", fault);
            }
            final UUID concurrent = campaign.reserve();
            run(manifest, concurrent, template, "write", "sealed");
            final var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                final var first =
                        workers.submit(
                                () -> {
                                    run(manifest, concurrent, template, "race", "sealed");
                                    return true;
                                });
                final var second =
                        workers.submit(
                                () -> {
                                    run(manifest, concurrent, template, "race", "sealed");
                                    return true;
                                });
                assertTrue(first.get(60, TimeUnit.SECONDS));
                assertTrue(second.get(60, TimeUnit.SECONDS));
            } finally {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
            }
            run(manifest, concurrent, template, "recover", "sealed");
            final UUID cancelled = campaign.reserve();
            run(manifest, cancelled, template, "write", "sealed");
            run(manifest, cancelled, template, "cancel-lock", "sealed");
            run(manifest, cancelled, template, "recover", "sealed");
        }
        campaign.checkDeadline();
    }

    private static void run(
            final String manifest,
            final UUID execution,
            final DataExportTemplate template,
            final String action,
            final String fault,
            final UUID... origin)
            throws Exception {
        final var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
        command.add("-Djava.library.path=" + System.getProperty("java.library.path"));
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Druntime.recovery.local.integration.enabled=true");
        command.add("-Druntime.recovery.local.integration.profile.active=true");
        command.add("-cp");
        command.add(
                System.getProperty(
                        "surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(RuntimePhysicalProcessProbe.class.getName());
        command.addAll(
                java.util.List.of(manifest, execution.toString(), template.name(), action, fault));
        if (origin.length == 1) {
            command.add(origin[0].toString());
        }
        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "owned JVM deadline");
            final String output =
                    new String(
                            process.getInputStream().readNBytes(16384),
                            java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            System.out.print(output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "owned JVM termination");
            }
        }
    }
}
