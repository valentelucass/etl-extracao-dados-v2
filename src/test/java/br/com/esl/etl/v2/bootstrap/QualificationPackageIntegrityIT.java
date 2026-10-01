package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationProcessEvidence;
import br.com.esl.etl.v2.plataforma.qualificacao.QualifiedPackage;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Offline package correspondence after Maven has built the executable JAR and runtime libraries.
 */
class QualificationPackageIntegrityIT {
    @TempDir Path directory;

    @Test
    void catalogPomsRetainThePinnedPublisherBytes() throws Exception {
        final var catalog = Path.of("docs/catalogos/macrobloco-qualificacao-pacote");
        final var lock = QualificationJson.read(catalog.resolve("dependency-lock.json"), 65536);
        assertEquals(9, lock.path("dependencies").size());
        for (final var dependency : lock.path("dependencies")) {
            final String pom = dependency.path("pom").asText();
            assertEquals(
                    dependency.path("pomSha256").asText(),
                    QualificationJson.sha256(catalog.resolve("third-party").resolve(pom)),
                    pom);
        }
    }

    @Test
    void deadOwnedProcessWithEqualAggregatesReconcilesWithoutSqlOrWorkerRetry() throws Exception {
        final var fixture =
                QualificationPackageFixture.createOffline(
                        directory.resolve("target/round-reconcile/payload"));
        final var payload = fixture.verify();
        final var document = fixture.campaign();
        final var campaign = QualificationCampaign.parse(document);
        final var configurationDocument =
                QualificationJson.read(
                        payload.root().resolve("config/config.synthetic.json"), 8192);
        final var configuration = QualificationConfiguration.parse(configurationDocument);
        final var files =
                new QualificationControlFiles(
                        payload.root(), payload.root().resolveSibling("control-reconcile"), true);
        QualificationControlFiles.atomic(files.root().resolve("campaign.json"), document);
        QualificationControlFiles.atomic(
                files.root().resolve("configuration.json"), configurationDocument);
        final var reads = new AtomicInteger();
        final String aggregate = QualificationJson.sha256("synthetic aggregate".getBytes());
        final var supervisor =
                new QualificationSupervisor(
                        payload,
                        campaign,
                        configuration,
                        files,
                        true,
                        () -> {
                            reads.incrementAndGet();
                            return aggregate;
                        });
        final var journal =
                CampaignJournal.open(
                        files.root().resolve("journal"),
                        QualificationJson.sha256(files.root().resolve("campaign.json")),
                        payload.manifestSha256());
        final var nonce = UUID.randomUUID();
        final long deadPid = Long.MAX_VALUE / 2;
        final String start = "2000-01-01T00:00:00Z";
        journal.append("bootstrap", nonce, CampaignJournal.Kind.RESERVED, "INTENT_BEFORE_PROCESS");
        final var attempt = files.attempt("bootstrap", true);
        QualificationControlFiles.atomic(
                attempt.resolve("process.json"),
                JsonNodeFactory.instance
                        .objectNode()
                        .put("pid", deadPid)
                        .put("start", start)
                        .put("nonce", nonce.toString())
                        .put(
                                "java",
                                Path.of(System.getProperty("java.home"), "bin", "java.exe")
                                        .toString())
                        .put("jar", payload.members().get("etl-dataexport-v2.jar").sha256()));
        QualificationControlFiles.atomic(
                attempt.resolve("baseline.json"),
                JsonNodeFactory.instance
                        .objectNode()
                        .put("nonce", nonce.toString())
                        .put("aggregate", aggregate));
        Files.writeString(attempt.resolve("stdout.log"), "synthetic fixture only\n");
        Files.writeString(attempt.resolve("stderr.log"), "");
        journal.append("bootstrap", nonce, CampaignJournal.Kind.STARTED, Long.toString(deadPid));

        final var resumed = supervisor.resume();
        assertEquals(1, reads.get());
        assertEquals(2, resumed.path("exit").intValue());
        assertEquals(
                CampaignJournal.Kind.RECONCILED,
                journal.read().get(journal.read().size() - 1).kind());
        final var reconciliation =
                QualificationJson.read(attempt.resolve("reconciliation.json"), 4096);
        assertTrue(reconciliation.path("rollback").booleanValue());
        assertFalse(reconciliation.path("terminal").booleanValue());
        assertEquals("MISSING", reconciliation.path("receipt").asText());
        assertEquals(resumed, supervisor.resume());
        assertEquals(1, reads.get());
    }

    @Test
    void syntheticSealedFailureResumesWithoutLaunchingAWorkerAndCliReportsTheResult()
            throws Exception {
        final var fixture =
                QualificationPackageFixture.createOffline(
                        directory.resolve("target/round-failure/payload"));
        final var payload = fixture.verify();
        final var document = fixture.campaign();
        final var campaign = QualificationCampaign.parse(document);
        final var configurationDocument =
                QualificationJson.read(
                        payload.root().resolve("config/config.synthetic.json"), 8192);
        final var configuration = QualificationConfiguration.parse(configurationDocument);
        final var files =
                new QualificationControlFiles(
                        payload.root(), payload.root().resolveSibling("control-failure"), true);
        QualificationControlFiles.atomic(files.root().resolve("campaign.json"), document);
        QualificationControlFiles.atomic(
                files.root().resolve("configuration.json"), configurationDocument);
        final var supervisor =
                new QualificationSupervisor(payload, campaign, configuration, files, true);
        assertEquals(2, command(payload, files, "status").path("exit").intValue());

        final var journal =
                CampaignJournal.open(
                        files.root().resolve("journal"),
                        QualificationJson.sha256(files.root().resolve("campaign.json")),
                        payload.manifestSha256());
        final var nonce = UUID.randomUUID();
        journal.append("bootstrap", nonce, CampaignJournal.Kind.RESERVED, "INTENT_BEFORE_PROCESS");
        final var caseRoot = files.attempt("bootstrap", true);
        final var reserved = journal.read();
        assertEquals(2, supervisor.resume().path("exit").intValue());
        assertEquals(reserved, journal.read());
        assertFalse(Files.exists(caseRoot.resolve("process.json")));
        assertFalse(Files.exists(files.root().resolve("campaign-result.json")));

        final long deadPid = Long.MAX_VALUE / 2;
        final String start = "2000-01-01T00:00:00Z";
        final String aggregate = QualificationJson.sha256("synthetic aggregate".getBytes());
        final var process =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("pid", deadPid)
                        .put("start", start)
                        .put("nonce", nonce.toString())
                        .put(
                                "java",
                                Path.of(System.getProperty("java.home"), "bin", "java.exe")
                                        .toString())
                        .put("jar", payload.members().get("etl-dataexport-v2.jar").sha256());
        QualificationControlFiles.atomic(caseRoot.resolve("process.json"), process);
        QualificationControlFiles.atomic(
                caseRoot.resolve("baseline.json"),
                JsonNodeFactory.instance
                        .objectNode()
                        .put("nonce", nonce.toString())
                        .put("aggregate", aggregate));
        Files.writeString(caseRoot.resolve("stdout.log"), "synthetic fixture only\n");
        Files.writeString(caseRoot.resolve("stderr.log"), "");
        final var receipt =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "qualification-receipt-v1")
                        .put("case", "bootstrap")
                        .put("nonce", nonce.toString())
                        .put(
                                "campaign",
                                QualificationJson.sha256(files.root().resolve("campaign.json")))
                        .put("package", payload.manifestSha256())
                        .put(
                                "configuration",
                                QualificationJson.sha256(
                                        files.root().resolve("configuration.json")))
                        .put("pid", deadPid)
                        .put("processStart", start)
                        .put("state", "FAILED")
                        .put("reason", "SYNTHETIC_FAILURE")
                        .put("testPassed", false)
                        .put("exit", 2)
                        .put("rollback", true)
                        .put("spid", 0)
                        .put("before", aggregate)
                        .put("after", aggregate)
                        .put("started", start)
                        .put("finished", start);
        receipt.set("report", JsonNodeFactory.instance.objectNode());
        QualificationControlFiles.atomic(caseRoot.resolve("receipt.json"), receipt);
        QualificationControlFiles.atomic(
                caseRoot.resolve("reconciliation.json"),
                JsonNodeFactory.instance
                        .objectNode()
                        .put("nonce", nonce.toString())
                        .put("exit", 2)
                        .put("forced", false)
                        .put("before", aggregate)
                        .put("after", aggregate)
                        .put("rollback", true)
                        .put("receipt", QualificationJson.sha256(caseRoot.resolve("receipt.json")))
                        .put("terminal", true));
        journal.append("bootstrap", nonce, CampaignJournal.Kind.STARTED, Long.toString(deadPid));
        journal.append(
                "bootstrap",
                nonce,
                CampaignJournal.Kind.EVIDENCE,
                QualificationProcessEvidence.seal(caseRoot));

        final var resumed = supervisor.resume();
        assertEquals(2, resumed.path("exit").intValue());
        assertEquals("FAILED", resumed.path("cases").get(0).path("state").asText());
        assertFalse(resumed.path("testPassed").booleanValue());
        assertEquals(
                CampaignJournal.Kind.TERMINAL,
                journal.read().get(journal.read().size() - 1).kind());
        assertEquals(resumed, command(payload, files, "status"));
        assertEquals(resumed, supervisor.resume());
        assertEquals(
                resumed,
                QualificationJson.read(files.root().resolve("campaign-result.json"), 1048576));
        Files.writeString(caseRoot.resolve("stdout.log"), "altered synthetic fixture\n");
        assertEquals(
                "QUAL_PROCESS_EVIDENCE_CHAIN",
                assertThrows(IllegalArgumentException.class, supervisor::status).getMessage());
    }

    private static ObjectNode command(
            final QualifiedPackage payload,
            final QualificationControlFiles files,
            final String name)
            throws Exception {
        final String executable =
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        final String classpath =
                payload.root().resolve("etl-dataexport-v2.jar")
                        + System.getProperty("path.separator")
                        + payload.root().resolve("lib")
                        + java.io.File.separator
                        + "*";
        final var process =
                new ProcessBuilder(
                                instrumentedJarCommand(
                                        executable,
                                        classpath,
                                        name,
                                        "--manifest-sha=" + payload.manifestSha256(),
                                        "--control=" + files.root()))
                        .redirectErrorStream(true)
                        .start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("offline qualification command timed out");
        }
        final byte[] bytes = process.getInputStream().readNBytes(65537);
        assertTrue(bytes.length <= 65536);
        assertEquals(2, process.exitValue());
        return (ObjectNode) QualificationJson.parse(bytes, 65536);
    }

    @Test
    void sealedPackageMatchesLockSbomResourcesAndSchemaBeforeAnySqlSession() throws Exception {
        final var fixture =
                QualificationPackageFixture.createOffline(directory.resolve("target/payload"));
        final var payload = fixture.verify();

        final var document = fixture.campaign();
        ((ObjectNode) document.path("cases").get(0))
                .put("tick", "2036-03-01T12:00:00Z")
                .put("expected", "BLOCKED_DEPENDENCY");
        final var campaign = QualificationCampaign.parse(document);
        final var configuration =
                QualificationConfiguration.read(
                        payload.root().resolve("config/config.synthetic.json"));
        final var files =
                new QualificationControlFiles(
                        payload.root(), payload.root().resolveSibling("control"), true);
        QualificationControlFiles.atomic(files.root().resolve("campaign.json"), document);
        QualificationControlFiles.atomic(
                files.root().resolve("configuration.json"),
                QualificationJson.read(
                        payload.root().resolve("config/config.synthetic.json"), 8192));
        final String executable =
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        final String classpath =
                payload.root().resolve("etl-dataexport-v2.jar")
                        + System.getProperty("path.separator")
                        + payload.root().resolve("lib")
                        + java.io.File.separator
                        + "*";
        final var planProcess =
                new ProcessBuilder(
                                instrumentedJarCommand(
                                        executable,
                                        classpath,
                                        "plan",
                                        "--manifest-sha=" + payload.manifestSha256(),
                                        "--campaign=" + files.root().resolve("campaign.json"),
                                        "--configuration="
                                                + files.root().resolve("configuration.json")))
                        .redirectErrorStream(true)
                        .start();
        if (!planProcess.waitFor(30, TimeUnit.SECONDS)) {
            planProcess.destroyForcibly();
            throw new AssertionError("offline qualification plan timed out");
        }
        final byte[] planBytes = planProcess.getInputStream().readNBytes(65537);
        assertTrue(planBytes.length <= 65536);
        assertEquals(0, planProcess.exitValue());
        final var plan = QualificationJson.parse(planBytes, 65536);
        assertEquals(campaign.id(), plan.path("campaign").asText());
        assertEquals(campaign.cases().size(), plan.path("cases").size());
        assertEquals("BLOCKED_DEPENDENCY", plan.path("cases").get(0).path("state").asText());
        final var supervisor =
                new QualificationSupervisor(payload, campaign, configuration, files, true);
        assertFalse(supervisor.status().path("testPassed").asBoolean());
        final var blocked = supervisor.execute();
        assertTrue(blocked.path("testPassed").asBoolean());
        assertEquals("BLOCKED_DEPENDENCY", blocked.path("cases").get(0).path("state").asText());
        assertEquals(blocked, supervisor.resume());
        assertFalse(Files.exists(files.attempt("bootstrap", false).resolve("process.json")));
        Files.writeString(
                files.root().resolve("campaign-result.json"),
                blocked.deepCopy().put("exit", 2) + "\n");
        assertEquals(
                "QUAL_CAMPAIGN_RESULT_CHAIN",
                assertThrows(IllegalArgumentException.class, supervisor::status).getMessage());

        fixture.mutate(
                "dependencies.json",
                value ->
                        ((ObjectNode) value.path("dependencies").get(0))
                                .put("origin", "SYNTHETIC_INVALID_ORIGIN"));
        assertEquals(
                "QUAL_SBOM_LOCK_CORRESPONDENCE",
                assertThrows(IllegalArgumentException.class, fixture::verify).getMessage());
    }

    @Test
    void packagedSequenceSelectionBindsTheDeclaredCaseBeforeAnySqlSession() throws Exception {
        final var fixture =
                QualificationPackageFixture.createOffline(
                        directory.resolve("target/sequence/payload"));
        fixture.includeSequenceCases();
        final var payload = fixture.verify();
        final var campaign =
                QualificationCampaign.parse(
                        fixture.sequenceCampaign("sequence-a", "PASS_LOCAL", "NONE"));
        final var selected = campaign.cases().get(0);
        final var sequence =
                QualificationArtifactCase.loadSequence(
                        payload, campaign, selected, CancellationToken.none());
        assertEquals(7, sequence.steps().size());
        assertEquals(campaign.roots(), sequence.roots());
        assertEquals(campaign.pageSize(), sequence.pageSize());
        assertEquals(1800, QualificationArtifactCase.caseSeconds(selected, null));
    }

    @Test
    void liveUnreconciledOwnerCannotAdmitAnIndependentOfflineCase() throws Exception {
        final var fixture =
                QualificationPackageFixture.createOffline(
                        directory.resolve("target/live-owner/payload"));
        final var payload = fixture.verify();
        final var document = fixture.campaign();
        final var independent = ((ObjectNode) document.path("cases").get(0)).deepCopy();
        independent.put("id", "independent");
        ((ArrayNode) document.path("cases")).add(independent);
        final var campaign = QualificationCampaign.parse(document);
        final var configurationDocument =
                QualificationJson.read(
                        payload.root().resolve("config/config.synthetic.json"), 8192);
        final var configuration = QualificationConfiguration.parse(configurationDocument);
        final var files =
                new QualificationControlFiles(
                        payload.root(), payload.root().resolveSibling("control-live"), true);
        QualificationControlFiles.atomic(files.root().resolve("campaign.json"), document);
        QualificationControlFiles.atomic(
                files.root().resolve("configuration.json"), configurationDocument);
        new QualificationSupervisor(payload, campaign, configuration, files, true);

        final var journal =
                CampaignJournal.open(
                        files.root().resolve("journal"),
                        QualificationJson.sha256(files.root().resolve("campaign.json")),
                        payload.manifestSha256());
        final var nonce = UUID.randomUUID();
        journal.append("bootstrap", nonce, CampaignJournal.Kind.RESERVED, "INTENT_BEFORE_PROCESS");
        final var caseRoot = files.attempt("bootstrap", true);
        final var owner = ProcessHandle.current();
        final var process =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("pid", owner.pid())
                        .put("start", owner.info().startInstant().orElseThrow().toString())
                        .put("nonce", nonce.toString())
                        .put("java", owner.info().command().orElseThrow())
                        .put("jar", payload.members().get("etl-dataexport-v2.jar").sha256());
        QualificationControlFiles.atomic(caseRoot.resolve("process.json"), process);
        journal.append(
                "bootstrap", nonce, CampaignJournal.Kind.STARTED, Long.toString(owner.pid()));
        assertTrue(QualificationProcessEvidence.sameLiveOwner(process));
        final var before = journal.read();
        final var result =
                new QualificationSupervisor(payload, campaign, configuration, files, false)
                        .resume();
        assertEquals("OUTCOME_UNKNOWN", result.path("cases").get(0).path("state").asText());
        assertEquals(2, result.path("exit").intValue());
        assertFalse(Files.exists(files.root().resolve("case-independent")));
        assertEquals(before, journal.read());
    }

    private static java.util.List<String> instrumentedJarCommand(
            final String executable, final String classpath, final String... arguments) {
        final var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        // The package still runs in its own JVM. Forward only the coverage agent so its
        // executed bytecode is counted by the same Maven report as the parent IT.
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> argument.startsWith("-javaagent:"))
                .filter(argument -> argument.contains("org.jacoco.agent"))
                .findFirst()
                .ifPresent(command::add);
        command.add("-cp");
        command.add(classpath);
        command.add("br.com.esl.etl.v2.bootstrap.QualificationLaboratoryMain");
        java.util.Collections.addAll(command, arguments);
        return command;
    }
}
