package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlInventory;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationPlanner;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationProcessEvidence;
import br.com.esl.etl.v2.plataforma.qualificacao.QualifiedPackage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Bounded one-shot supervisor. Resume observes immutable evidence and never repeats a reserved
 * case.
 */
public final class QualificationSupervisor {
    private final QualifiedPackage payload;
    private final QualificationCampaign campaign;
    private final QualificationConfiguration configuration;
    private final QualificationControlFiles files;
    private final CampaignJournal journal;
    private final String campaignHash;
    private final String configurationHash;

    public QualificationSupervisor(
            final QualifiedPackage payload,
            final QualificationCampaign campaign,
            final QualificationConfiguration configuration,
            final QualificationControlFiles files,
            final boolean create)
            throws IOException {
        this.payload = payload;
        this.campaign = campaign;
        this.configuration = configuration;
        this.files = files;
        payload.verifyPins(campaign.pins());
        campaignHash = QualificationJson.sha256(files.root().resolve("campaign.json"));
        configurationHash = QualificationJson.sha256(files.root().resolve("configuration.json"));
        journal =
                create
                        ? CampaignJournal.create(
                                files.root().resolve("journal"),
                                campaignHash,
                                payload.manifestSha256())
                        : CampaignJournal.open(
                                files.root().resolve("journal"),
                                campaignHash,
                                payload.manifestSha256());
        if (campaign.maximumSeconds() > configuration.campaignSeconds()) {
            throw new IllegalArgumentException("QUAL_CAMPAIGN_BUDGET");
        }
    }

    public ObjectNode execute() throws Exception {
        return controlled(false);
    }

    private ObjectNode controlled(final boolean resumed) throws Exception {
        final long deadline = System.nanoTime() + campaign.maximumSeconds() * 1_000_000_000L;
        try (var channel =
                        FileChannel.open(
                                files.root().resolve("controller.lock"),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE);
                var lock = channel.tryLock()) {
            if (lock == null) {
                throw new IllegalArgumentException("QUAL_CONTROLLER_BUSY");
            }
            if (resumed) {
                reconcilePending();
            }
            for (final var item : campaign.cases()) {
                final var statuses = journal.status();
                if (statuses.containsKey(item.id())) {
                    continue;
                }
                final var nonce = UUID.randomUUID();
                journal.append(
                        item.id(), nonce, CampaignJournal.Kind.RESERVED, "INTENT_BEFORE_PROCESS");
                final var directory = files.attempt(item.id(), true);
                QualificationControlFiles.atomic(
                        QualificationControlFiles.member(directory, "intent.json"),
                        JsonNodeFactory.instance
                                .objectNode()
                                .put("version", "qualification-intent-v1")
                                .put("case", item.id())
                                .put("nonce", nonce.toString())
                                .put("campaign", campaignHash)
                                .put("package", payload.manifestSha256())
                                .put("configuration", configurationHash)
                                .put("caseSeconds", configuration.caseSeconds()));
                final var plan = QualificationPlanner.plan(item);
                if (System.nanoTime() >= deadline
                        || plan.state() != QualificationGate.State.PASS_LOCAL
                        || item.dependencies().stream()
                                .anyMatch(
                                        id ->
                                                !statuses.containsKey(id)
                                                        || statuses.get(id).state()
                                                                != QualificationGate.State
                                                                        .PASS_LOCAL)) {
                    journal.append(
                            item.id(), nonce, CampaignJournal.Kind.DEFERRED, "BLOCKED_DEPENDENCY");
                    continue;
                }
                runChild(item, nonce, directory, deadline);
            }
            final var result = status();
            final var events = journal.read();
            final boolean settled =
                    campaign.cases().stream()
                            .allMatch(
                                    item ->
                                            events.stream()
                                                    .filter(
                                                            event ->
                                                                    event.caseId()
                                                                            .equals(item.id()))
                                                    .reduce((first, last) -> last)
                                                    .map(
                                                            event ->
                                                                    List.of(
                                                                                    CampaignJournal
                                                                                            .Kind
                                                                                            .TERMINAL,
                                                                                    CampaignJournal
                                                                                            .Kind
                                                                                            .DEFERRED,
                                                                                    CampaignJournal
                                                                                            .Kind
                                                                                            .RECONCILED)
                                                                            .contains(event.kind()))
                                                    .orElse(false));
            if (settled && !Files.exists(files.root().resolve("campaign-result.json"))) {
                QualificationControlFiles.atomic(
                        QualificationControlFiles.member(files.root(), "campaign-result.json"),
                        result);
            }
            return result;
        }
    }

    private void runChild(
            final QualificationCampaign.Case item,
            final UUID nonce,
            final Path directory,
            final long campaignDeadline)
            throws Exception {
        QualificationSqlEvidence.master(configuration);
        final String before = QualificationSqlEvidence.snapshot(configuration).sha256();
        QualificationControlFiles.atomic(
                QualificationControlFiles.member(directory, "baseline.json"),
                JsonNodeFactory.instance
                        .objectNode()
                        .put("nonce", nonce.toString())
                        .put("aggregate", before));
        final var java =
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toAbsolutePath();
        final var jar = payload.member("etl-dataexport-v2.jar", "APPLICATION");
        final var command =
                List.of(
                        java.toString(),
                        "-Xmx" + configuration.heapMiB() + "m",
                        "-Dfile.encoding=UTF-8",
                        "-Dshadow.local.integration.enabled=true",
                        "-Dshadow.local.integration.profile.active=true",
                        "-Djava.library.path=" + payload.root().resolve("native"),
                        "-cp",
                        jar.toString(),
                        "br.com.esl.etl.v2.bootstrap.QualificationLaboratoryMain",
                        "worker",
                        "--manifest-sha=" + payload.manifestSha256(),
                        "--control=" + files.root(),
                        "--case=" + item.id(),
                        "--nonce=" + nonce);
        final var stdout = QualificationControlFiles.member(directory, "stdout.log");
        final var stderr = QualificationControlFiles.member(directory, "stderr.log");
        Files.createFile(stdout);
        Files.createFile(stderr);
        final var builder =
                new ProcessBuilder(command)
                        .directory(payload.root().toFile())
                        .redirectOutput(stdout.toFile())
                        .redirectError(stderr.toFile());
        for (final var name :
                List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH")) {
            builder.environment().remove(name);
        }
        final var process = builder.start();
        final var start = process.info().startInstant().orElseThrow();
        final long deadline =
                Math.min(
                        campaignDeadline,
                        System.nanoTime() + configuration.caseSeconds() * 1_000_000_000L);
        boolean signalled = false;
        boolean forced = false;
        long cancellationAt = Long.MAX_VALUE;
        try {
            QualificationControlFiles.atomic(
                    QualificationControlFiles.member(directory, "process.json"),
                    JsonNodeFactory.instance
                            .objectNode()
                            .put("pid", process.pid())
                            .put("start", start.toString())
                            .put("nonce", nonce.toString())
                            .put("java", java.toString())
                            .put("jar", payload.members().get("etl-dataexport-v2.jar").sha256()));
            journal.append(
                    item.id(), nonce, CampaignJournal.Kind.STARTED, Long.toString(process.pid()));
            boolean barrierRecorded = false;
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                String cancelReason = null;
                if (Files.size(stdout) > 1048576 || Files.size(stderr) > 1048576) {
                    cancelReason = "LOG_LIMIT";
                }
                if (System.nanoTime() >= deadline) {
                    cancelReason = "CASE_DEADLINE";
                }
                final var barrierPath = directory.resolve("barrier.json");
                if (!barrierRecorded && Files.exists(barrierPath)) {
                    verifyBarrier(barrierPath, item, nonce, process.pid());
                    journal.append(
                            item.id(), nonce, CampaignJournal.Kind.BARRIER, item.barrier().name());
                    barrierRecorded = true;
                    if (item.barrier() != QualificationCampaign.Barrier.BEFORE_RECEIPT) {
                        cancelReason = "CONTROLLED_BARRIER";
                    }
                }
                if (cancelReason != null && !signalled) {
                    QualificationControlFiles.atomic(
                            QualificationControlFiles.member(directory, "cancel.json"),
                            JsonNodeFactory.instance
                                    .objectNode()
                                    .put("nonce", nonce.toString())
                                    .put("reason", cancelReason));
                    signalled = true;
                    cancellationAt = System.nanoTime();
                }
                if (signalled && System.nanoTime() - cancellationAt >= 5_000_000_000L) {
                    destroyOwned(process, start);
                    forced = true;
                    break;
                }
            }
            if (process.isAlive()) {
                throw new IllegalStateException("QUAL_OWNED_PROCESS_UNRELEASED");
            }
            verifyLog(stdout);
            verifyLog(stderr);
            reconcile(item, nonce, directory, process.exitValue(), forced, before);
        } finally {
            if (process.isAlive()) {
                destroyOwned(process, start);
            }
        }
    }

    private void reconcile(
            final QualificationCampaign.Case item,
            final UUID nonce,
            final Path directory,
            final int exit,
            final boolean forced,
            final String before)
            throws Exception {
        final String after = QualificationSqlEvidence.snapshot(configuration).sha256();
        final boolean rollback = before.equals(after);
        final var receiptPath = directory.resolve("receipt.json");
        final JsonNode receipt = Files.exists(receiptPath) ? receipt(item, nonce, directory) : null;
        final boolean terminal =
                rollback
                        && !forced
                        && receipt != null
                        && receipt.path("rollback").booleanValue()
                        && receipt.path("exit").intValue() == exit;
        QualificationControlFiles.atomic(
                QualificationControlFiles.member(directory, "reconciliation.json"),
                JsonNodeFactory.instance
                        .objectNode()
                        .put("nonce", nonce.toString())
                        .put("exit", exit)
                        .put("forced", forced)
                        .put("before", before)
                        .put("after", after)
                        .put("rollback", rollback)
                        .put(
                                "receipt",
                                receipt == null ? "MISSING" : QualificationJson.sha256(receiptPath))
                        .put("terminal", terminal));
        journal.append(
                item.id(),
                nonce,
                CampaignJournal.Kind.EVIDENCE,
                QualificationProcessEvidence.seal(directory));
        if (terminal && !receipt.path("state").asText().equals("OUTCOME_UNKNOWN")) {
            journal.append(
                    item.id(),
                    nonce,
                    CampaignJournal.Kind.ROLLBACK,
                    "AGGREGATES_AND_CHILD_ROLLBACK");
            journal.append(
                    item.id(),
                    nonce,
                    CampaignJournal.Kind.TERMINAL,
                    receipt.path("state").asText());
        } else if (rollback) {
            journal.append(
                    item.id(),
                    nonce,
                    CampaignJournal.Kind.RECONCILED,
                    "OUTCOME_UNKNOWN_ROLLBACK_CONFIRMED");
        }
    }

    public ObjectNode resume() throws Exception {
        return controlled(true);
    }

    private void reconcilePending() throws Exception {
        QualificationControlInventory.verify(files, campaign);
        final var statuses = journal.status();
        for (final var item : campaign.cases()) {
            final var status = statuses.get(item.id());
            if (status == null || status.state() != QualificationGate.State.OUTCOME_UNKNOWN) {
                continue;
            }
            final var directory = files.attempt(item.id(), false);
            if (Files.exists(directory.resolve("reconciliation.json"))) {
                continue;
            }
            if (Files.exists(directory.resolve("process.json"))) {
                final var recorded =
                        QualificationJson.read(directory.resolve("process.json"), 8192);
                verifyProcess(recorded, status.nonce());
                if (QualificationProcessEvidence.sameLiveOwner(recorded)) {
                    continue;
                }
            }
            if (Files.exists(directory.resolve("baseline.json"))) {
                final var baseline =
                        QualificationJson.read(directory.resolve("baseline.json"), 1024);
                if (!status.nonce().toString().equals(baseline.path("nonce").asText())) {
                    throw new IllegalArgumentException("QUAL_RESUME_BASELINE_NONCE");
                }
                // A re-used PID and an old receipt cannot reconstruct an unobserved process exit.
                reconcile(
                        item,
                        status.nonce(),
                        directory,
                        -1,
                        false,
                        QualificationJson.digest(baseline, "aggregate"));
            }
        }
    }

    public ObjectNode status() throws Exception {
        QualificationControlInventory.verify(files, campaign);
        final var statuses = journal.status();
        final var events = journal.read();
        final var known = new HashSet<String>();
        campaign.cases().forEach(item -> known.add(item.id()));
        if (!known.containsAll(statuses.keySet())) {
            throw new IllegalArgumentException("QUAL_JOURNAL_FOREIGN_CASE");
        }
        final var result =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "qualification-status-v1")
                        .put("campaign", campaignHash)
                        .put("package", payload.manifestSha256())
                        .put(
                                "journalHead",
                                events.isEmpty()
                                        ? QualificationJson.sha256(
                                                journal.root().resolve("header.json"))
                                        : events.get(events.size() - 1).sha256());
        final var cases = result.putArray("cases");
        boolean passed = true;
        for (final var item : campaign.cases()) {
            final var status = statuses.get(item.id());
            final var state =
                    status == null ? QualificationGate.State.OUTCOME_UNKNOWN : status.state();
            boolean testPassed = false;
            if (status != null) {
                final var directory = files.attempt(item.id(), false);
                if (Files.exists(directory.resolve("reconciliation.json"))) {
                    final var seals =
                            events.stream()
                                    .filter(
                                            event ->
                                                    event.caseId().equals(item.id())
                                                            && event.kind()
                                                                    == CampaignJournal.Kind
                                                                            .EVIDENCE)
                                    .toList();
                    if (seals.size() != 1
                            || !seals.get(0).nonce().equals(status.nonce())
                            || !seals.get(0)
                                    .detail()
                                    .equals(QualificationProcessEvidence.seal(directory))) {
                        throw new IllegalArgumentException("QUAL_PROCESS_EVIDENCE_CHAIN");
                    }
                    final var receiptPath = directory.resolve("receipt.json");
                    QualificationProcessEvidence.reconciliation(
                            QualificationJson.read(directory.resolve("reconciliation.json"), 4096),
                            status.nonce(),
                            QualificationJson.read(directory.resolve("baseline.json"), 1024),
                            Files.exists(receiptPath)
                                    ? receipt(item, status.nonce(), directory)
                                    : null,
                            Files.exists(receiptPath)
                                    ? QualificationJson.sha256(receiptPath)
                                    : "MISSING");
                }
                if (Files.exists(directory.resolve("receipt.json"))
                        && Files.exists(directory.resolve("reconciliation.json"))) {
                    final var receipt = receipt(item, status.nonce(), directory);
                    final var reconciliation =
                            QualificationJson.read(directory.resolve("reconciliation.json"), 4096);
                    testPassed =
                            receipt.path("testPassed").booleanValue()
                                    && state.name().equals(receipt.path("state").asText())
                                    && reconciliation.path("terminal").booleanValue()
                                    && QualificationJson.sha256(directory.resolve("receipt.json"))
                                            .equals(reconciliation.path("receipt").asText())
                                    && reconciliation.path("exit").intValue()
                                            == receipt.path("exit").intValue();
                } else if (state == QualificationGate.State.BLOCKED_DEPENDENCY) {
                    testPassed = item.expected() == state;
                } else if (state == QualificationGate.State.OUTCOME_UNKNOWN
                        && item.barrier() == QualificationCampaign.Barrier.BEFORE_RECEIPT
                        && Files.exists(directory.resolve("reconciliation.json"))) {
                    final var reconciliation =
                            QualificationJson.read(directory.resolve("reconciliation.json"), 4096);
                    final var process =
                            QualificationJson.read(directory.resolve("process.json"), 8192);
                    verifyBarrier(
                            directory.resolve("barrier.json"),
                            item,
                            status.nonce(),
                            process.path("pid").longValue());
                    testPassed =
                            item.expected() == state
                                    && reconciliation.path("rollback").booleanValue()
                                    && reconciliation.path("exit").intValue() == 0
                                    && !reconciliation.path("forced").booleanValue()
                                    && "MISSING".equals(reconciliation.path("receipt").asText());
                }
            }
            passed &= testPassed;
            cases.addObject()
                    .put("case", item.id())
                    .put("state", state.name())
                    .put("testPassed", testPassed);
        }
        result.put("testPassed", passed).put("exit", passed ? 0 : 2);
        final var finalPath = files.root().resolve("campaign-result.json");
        if (Files.exists(finalPath) && !result.equals(QualificationJson.read(finalPath, 1048576))) {
            throw new IllegalArgumentException("QUAL_CAMPAIGN_RESULT_CHAIN");
        }
        return result;
    }

    private JsonNode receipt(
            final QualificationCampaign.Case item, final UUID nonce, final Path directory)
            throws IOException {
        final var receipt = QualificationJson.read(directory.resolve("receipt.json"), 1048576);
        QualificationJson.fields(
                receipt,
                "version",
                "case",
                "nonce",
                "campaign",
                "package",
                "configuration",
                "pid",
                "processStart",
                "state",
                "reason",
                "testPassed",
                "exit",
                "rollback",
                "spid",
                "before",
                "after",
                "started",
                "finished",
                "report");
        final var process = QualificationJson.read(directory.resolve("process.json"), 8192);
        verifyProcess(process, nonce);
        if (!"qualification-receipt-v1".equals(receipt.path("version").asText())
                || !item.id().equals(receipt.path("case").asText())
                || !nonce.toString().equals(receipt.path("nonce").asText())
                || !campaignHash.equals(receipt.path("campaign").asText())
                || !payload.manifestSha256().equals(receipt.path("package").asText())
                || !configurationHash.equals(receipt.path("configuration").asText())
                || receipt.path("pid").longValue() != process.path("pid").longValue()
                || !receipt.path("processStart").asText().equals(process.path("start").asText())
                || !receipt.path("testPassed").isBoolean()
                || !receipt.path("rollback").isBoolean()
                || !receipt.path("exit").isIntegralNumber()) {
            throw new IllegalArgumentException("QUAL_RECEIPT_BINDING");
        }
        final var state =
                QualificationGate.State.valueOf(QualificationJson.text(receipt, "state", 40));
        if (receipt.path("testPassed").booleanValue()
                && (state != item.expected()
                        || !receipt.path("rollback").booleanValue()
                        || receipt.path("exit").intValue() != 0)) {
            throw new IllegalArgumentException("QUAL_RECEIPT_VERDICT");
        }
        if (state == QualificationGate.State.PASS_LOCAL) {
            QualificationProcessEvidence.coverage(receipt.path("report"));
        }
        return receipt;
    }

    private void verifyProcess(final JsonNode process, final UUID nonce) {
        QualificationProcessEvidence.process(
                process,
                nonce,
                payload.members().get("etl-dataexport-v2.jar").sha256(),
                Path.of(System.getProperty("java.home"), "bin", "java.exe"));
    }

    private static void verifyBarrier(
            final Path path,
            final QualificationCampaign.Case item,
            final UUID nonce,
            final long pid)
            throws IOException {
        final var barrier = QualificationJson.read(path, 1024);
        QualificationJson.fields(barrier, "nonce", "point", "pid", "observedAt");
        if (!nonce.toString().equals(barrier.path("nonce").asText())
                || pid != barrier.path("pid").longValue()
                || !item.barrier().name().equals(barrier.path("point").asText())
                || item.barrier() == QualificationCampaign.Barrier.NONE) {
            throw new IllegalArgumentException("QUAL_BARRIER_BINDING");
        }
    }

    private static void destroyOwned(final Process process, final Instant start)
            throws InterruptedException {
        if (process.isAlive() && process.info().startInstant().filter(start::equals).isPresent()) {
            process.destroyForcibly();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("QUAL_OWNED_KILL_UNCONFIRMED");
            }
        }
    }

    private static void verifyLog(final Path file) throws IOException {
        QualificationJson.regular(file);
        if (Files.size(file) > 1048576) {
            throw new IllegalArgumentException("QUAL_PROCESS_LOG_LIMIT");
        }
        final var decoded =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(Files.readAllBytes(file)));
        if (decoded.toString().indexOf('\ufffd') >= 0) {
            throw new IllegalArgumentException("QUAL_PROCESS_LOG_ENCODING");
        }
    }
}
