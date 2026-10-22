package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationPlanner;
import br.com.esl.etl.v2.plataforma.qualificacao.QualifiedPackage;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Denial ordering over an independent package fixture, without starting a SQL worker. */
class QualificationSupervisorOfflineRefusalTest {
    @TempDir Path temporary;

    @Test
    void excessiveCampaignBudgetCannotCreateAnAttemptOrReadAggregates() throws Exception {
        final var context =
                context(
                        campaign -> campaign.put("maximumSeconds", 601),
                        configuration -> configuration.put("campaignSeconds", 600));
        assertEquals(
                "QUAL_CAMPAIGN_BUDGET",
                assertThrows(IllegalArgumentException.class, () -> context.supervisor(true))
                        .getMessage());
        assertFalse(Files.exists(context.files().root().resolve("case-bootstrap")));
        assertFalse(Files.exists(context.files().root().resolve("campaign-result.json")));
        assertTrue(context.journal().read().isEmpty());
        assertEquals(0, context.snapshots().get());
    }

    @Test
    void journalCaseOutsideCampaignRefusesStatusAndResumeWithoutMutation() throws Exception {
        final var context = context(campaign -> {}, configuration -> {});
        final var supervisor = context.supervisor(true);
        context.journal()
                .append(
                        "foreign",
                        UUID.randomUUID(),
                        CampaignJournal.Kind.RESERVED,
                        "UNTRUSTED_CASE");
        final var before = pins(context.files().root());
        assertEquals(
                "QUAL_JOURNAL_FOREIGN_CASE",
                assertThrows(IllegalArgumentException.class, supervisor::status).getMessage());
        assertEquals(
                "QUAL_JOURNAL_FOREIGN_CASE",
                assertThrows(IllegalArgumentException.class, supervisor::resume).getMessage());
        assertEquals(before, pins(context.files().root()));
        assertFalse(Files.exists(context.files().root().resolve("case-bootstrap")));
        assertEquals(0, context.snapshots().get());
    }

    @Test
    void deferredDependencyBlocksAnOtherwiseDueSuccessorWithoutInventingAProcess()
            throws Exception {
        final var context =
                context(
                        campaign -> {
                            final var cases = (ArrayNode) campaign.path("cases");
                            final var first = (ObjectNode) cases.get(0);
                            final var dependent = first.deepCopy();
                            first.put("tick", "2036-03-01T12:00:00Z")
                                    .put("expected", "BLOCKED_DEPENDENCY");
                            dependent
                                    .put("id", "dependent")
                                    .put("expected", "BLOCKED_DEPENDENCY")
                                    .putArray("dependsOn")
                                    .add("bootstrap");
                            cases.add(dependent);
                        },
                        configuration -> {});
        assertEquals(
                QualificationGate.State.BLOCKED_DEPENDENCY,
                QualificationPlanner.plan(context.campaign().cases().get(0)).state());
        assertEquals(
                QualificationGate.State.PASS_LOCAL,
                QualificationPlanner.plan(context.campaign().cases().get(1)).state());
        final var supervisor = context.supervisor(true);
        final var result = supervisor.execute();
        assertEquals(0, result.path("exit").asInt());
        assertTrue(result.path("testPassed").asBoolean());
        for (final String id : new String[] {"bootstrap", "dependent"}) {
            final var attempt = context.files().attempt(id, false);
            assertTrue(Files.isRegularFile(attempt.resolve("intent.json")));
            assertFalse(Files.exists(attempt.resolve("process.json")));
            assertFalse(Files.exists(attempt.resolve("receipt.json")));
            assertEquals(
                    QualificationGate.State.BLOCKED_DEPENDENCY,
                    context.journal().status().get(id).state());
        }
        assertEquals(4, context.journal().read().size());
        final var before = pins(context.files().root());
        assertEquals(result, supervisor.resume());
        assertEquals(result, supervisor.execute());
        assertEquals(before, pins(context.files().root()));
        assertEquals(0, context.snapshots().get());
    }

    @Test
    void undeclaredControlFileStopsResumeBeforeJournalMutationOrAggregateRead() throws Exception {
        final var context = context(campaign -> {}, configuration -> {});
        final var supervisor = context.supervisor(true);
        final var foreign = context.files().root().resolve("untrusted-command.json");
        Files.writeString(foreign, "{\"synthetic\":true,\"command\":\"UNTRUSTED_INPUT\"}");
        final var before = pins(context.files().root());
        assertEquals(
                "QUAL_CONTROL_UNDECLARED_OR_TRUNCATED_FILE",
                assertThrows(IllegalArgumentException.class, supervisor::resume).getMessage());
        assertEquals(before, pins(context.files().root()));
        assertTrue(context.journal().read().isEmpty());
        assertEquals(0, context.snapshots().get());
    }

    @Test
    void foreignProcessNonceCannotBeAdoptedEvenWhenItsPidNamesTheCurrentJvm() throws Exception {
        final var context = context(campaign -> {}, configuration -> {});
        final var supervisor = context.supervisor(true);
        final var nonce = UUID.randomUUID();
        context.journal()
                .append("bootstrap", nonce, CampaignJournal.Kind.RESERVED, "INTENT_BEFORE_PROCESS");
        final var attempt = context.files().attempt("bootstrap", true);
        final var current = ProcessHandle.current();
        // This is deliberately invalid input. A different nonce must reject before any owner
        // adoption; the current JVM is not represented as a successful packaged worker.
        QualificationControlFiles.atomic(
                attempt.resolve("process.json"),
                JsonNodeFactory.instance
                        .objectNode()
                        .put("pid", current.pid())
                        .put("start", current.info().startInstant().orElseThrow().toString())
                        .put(
                                "nonce",
                                new UUID(
                                                nonce.getMostSignificantBits() ^ 1L,
                                                nonce.getLeastSignificantBits())
                                        .toString())
                        .put("java", current.info().command().orElseThrow())
                        .put(
                                "jar",
                                context.payload().members().get("etl-dataexport-v2.jar").sha256()));
        final var before = pins(context.files().root());
        assertEquals(
                "QUAL_PROCESS_EVIDENCE_BINDING",
                assertThrows(IllegalArgumentException.class, supervisor::resume).getMessage());
        assertEquals(before, pins(context.files().root()));
        assertEquals(1, context.journal().read().size());
        assertFalse(Files.exists(attempt.resolve("receipt.json")));
        assertFalse(Files.exists(attempt.resolve("reconciliation.json")));
        assertEquals(0, context.snapshots().get());
    }

    private Context context(
            final Consumer<ObjectNode> campaignChanges,
            final Consumer<ObjectNode> configurationChanges)
            throws Exception {
        final var fixture =
                QualificationPackageFixture.createOffline(temporary.resolve("target/payload"));
        final var payload = fixture.verify();
        final var campaign = fixture.campaign();
        final var configuration =
                (ObjectNode)
                        QualificationJson.read(
                                payload.root().resolve("config/config.synthetic.json"), 8192);
        campaignChanges.accept(campaign);
        configurationChanges.accept(configuration);
        final var files =
                new QualificationControlFiles(
                        payload.root(), payload.root().resolveSibling("control"), true);
        QualificationControlFiles.atomic(files.root().resolve("campaign.json"), campaign);
        QualificationControlFiles.atomic(files.root().resolve("configuration.json"), configuration);
        Files.createFile(files.root().resolve("controller.lock"));
        return new Context(
                payload,
                QualificationCampaign.parse(campaign),
                QualificationConfiguration.parse(configuration),
                files,
                new AtomicInteger());
    }

    private static Map<String, String> pins(final Path root) throws Exception {
        final var result = new TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (final var path : paths.filter(Files::isRegularFile).toList()) {
                result.put(root.relativize(path).toString(), QualificationJson.sha256(path));
            }
        }
        return result;
    }

    private record Context(
            QualifiedPackage payload,
            QualificationCampaign campaign,
            QualificationConfiguration configuration,
            QualificationControlFiles files,
            AtomicInteger snapshots) {
        QualificationSupervisor supervisor(final boolean create) throws IOException {
            return new QualificationSupervisor(
                    payload,
                    campaign,
                    configuration,
                    files,
                    create,
                    () -> {
                        snapshots.incrementAndGet();
                        throw new AssertionError("No aggregate snapshot is permitted in this case");
                    });
        }

        CampaignJournal journal() throws IOException {
            return CampaignJournal.open(
                    files.root().resolve("journal"),
                    QualificationJson.sha256(files.root().resolve("campaign.json")),
                    payload.manifestSha256());
        }
    }
}
