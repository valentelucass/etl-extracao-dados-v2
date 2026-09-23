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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** A live owner from a lost controller must keep the campaign's single-worker admission closed. */
class QualificationResumeAdmissionIT {
    @Test
    @Timeout(180)
    void reservationWithoutProcessEvidenceDoesNotAuthorizeAnotherWorker() throws Exception {
        final var fixture = QualificationPackageFixture.create();
        final var payload = fixture.verify();
        final var document = fixture.campaign();
        final var next = ((ObjectNode) document.path("cases").get(0)).deepCopy();
        next.put("id", "independent");
        ((ArrayNode) document.path("cases")).add(next);
        final var campaign = QualificationCampaign.parse(document);
        final var configuration =
                QualificationConfiguration.read(
                        payload.root().resolve("config/config.synthetic.json"));
        final var files =
                new QualificationControlFiles(
                        payload.root(),
                        payload.root().resolveSibling("control-" + UUID.randomUUID()),
                        true);
        QualificationControlFiles.atomic(files.root().resolve("campaign.json"), document);
        QualificationControlFiles.atomic(
                files.root().resolve("configuration.json"),
                QualificationJson.read(
                        payload.root().resolve("config/config.synthetic.json"), 8192));
        new QualificationSupervisor(payload, campaign, configuration, files, true);
        final var journal =
                CampaignJournal.open(
                        files.root().resolve("journal"),
                        QualificationJson.sha256(files.root().resolve("campaign.json")),
                        payload.manifestSha256());
        journal.append(
                "bootstrap",
                UUID.randomUUID(),
                CampaignJournal.Kind.RESERVED,
                "INTENT_BEFORE_PROCESS");
        files.attempt("bootstrap", true);
        final var before = journal.read();
        final var result =
                new QualificationSupervisor(payload, campaign, configuration, files, false)
                        .resume();
        assertEquals(2, result.path("exit").intValue());
        assertFalse(Files.exists(files.root().resolve("case-independent")));
        assertEquals(before, journal.read());
    }

    @ParameterizedTest
    @EnumSource(
            value = CampaignJournal.Kind.class,
            names = {"EVIDENCE", "ROLLBACK"})
    @Timeout(180)
    void resumesASealedResultWithoutRepeatingTheWorker(final CampaignJournal.Kind boundary)
            throws Exception {
        final var interrupted = interruptedAfter(boundary);
        final var before = interrupted.journal().read();
        final var processHash =
                QualificationJson.sha256(interrupted.caseRoot().resolve("process.json"));
        final var result = interrupted.supervisor().resume();
        assertEquals(0, result.path("exit").intValue());
        assertEquals("PASS_LOCAL", result.path("cases").get(0).path("state").asText());
        final var after = interrupted.journal().read();
        assertEquals(before, after.subList(0, before.size()));
        assertEquals(CampaignJournal.Kind.TERMINAL, after.get(after.size() - 1).kind());
        assertEquals(
                processHash,
                QualificationJson.sha256(interrupted.caseRoot().resolve("process.json")));
        assertEquals(result, interrupted.supervisor().resume());
        assertEquals(after, interrupted.journal().read());
    }

    @Test
    @Timeout(180)
    void refusesAlteredSealedEvidenceBeforeCompletingTheJournal() throws Exception {
        final var interrupted = interruptedAfter(CampaignJournal.Kind.EVIDENCE);
        final var before = interrupted.journal().read();
        Files.writeString(
                interrupted.caseRoot().resolve("stdout.log"), "altered synthetic fixture\n");
        final var error =
                assertThrows(IllegalArgumentException.class, interrupted.supervisor()::resume);
        assertEquals("QUAL_PROCESS_EVIDENCE_CHAIN", error.getMessage());
        assertEquals(before, interrupted.journal().read());
    }

    @Test
    @Timeout(180)
    void refusesToInventAnExitFromAnUnsealedReconciliation() throws Exception {
        final var interrupted = interruptedAfter(CampaignJournal.Kind.STARTED);
        final var before = interrupted.journal().read();
        final var error =
                assertThrows(IllegalArgumentException.class, interrupted.supervisor()::resume);
        assertEquals("QUAL_PROCESS_EVIDENCE_CHAIN", error.getMessage());
        assertEquals(before, interrupted.journal().read());
    }

    private record Interrupted(
            QualificationSupervisor supervisor, CampaignJournal journal, Path caseRoot) {}

    private static Interrupted interruptedAfter(final CampaignJournal.Kind boundary)
            throws Exception {
        final var fixture = QualificationPackageFixture.create();
        final var payload = fixture.verify();
        final var document = fixture.campaign();
        final var campaign = QualificationCampaign.parse(document);
        final var configuration =
                QualificationConfiguration.read(
                        payload.root().resolve("config/config.synthetic.json"));
        final var original =
                new QualificationControlFiles(
                        payload.root(),
                        payload.root().resolveSibling("original-" + UUID.randomUUID()),
                        true);
        QualificationControlFiles.atomic(original.root().resolve("campaign.json"), document);
        QualificationControlFiles.atomic(
                original.root().resolve("configuration.json"),
                QualificationJson.read(
                        payload.root().resolve("config/config.synthetic.json"), 8192));
        final var executed =
                new QualificationSupervisor(payload, campaign, configuration, original, true)
                        .execute();
        assertEquals(0, executed.path("exit").intValue());
        final var sourceJournal =
                CampaignJournal.open(
                        original.root().resolve("journal"),
                        QualificationJson.sha256(original.root().resolve("campaign.json")),
                        payload.manifestSha256());
        final var copy =
                new QualificationControlFiles(
                        payload.root(),
                        payload.root().resolveSibling("interrupted-" + UUID.randomUUID()),
                        true);
        for (final var name : List.of("campaign.json", "configuration.json")) {
            Files.copy(original.root().resolve(name), copy.root().resolve(name));
        }
        final var sourceCase = original.attempt("bootstrap", false);
        final var copyCase = copy.attempt("bootstrap", true);
        try (var members = Files.list(sourceCase)) {
            for (final var member : members.toList()) {
                Files.copy(member, copyCase.resolve(member.getFileName()));
            }
        }
        final var journal =
                CampaignJournal.create(
                        copy.root().resolve("journal"),
                        QualificationJson.sha256(copy.root().resolve("campaign.json")),
                        payload.manifestSha256());
        for (final var event : sourceJournal.read()) {
            journal.append(event.caseId(), event.nonce(), event.kind(), event.detail());
            if (event.kind() == boundary) {
                break;
            }
        }
        return new Interrupted(
                new QualificationSupervisor(payload, campaign, configuration, copy, false),
                journal,
                copyCase);
    }

    @Test
    @Timeout(180)
    void resumeDoesNotAdmitAnotherCaseWhileTheRecordedOwnerIsStillAlive() throws Exception {
        final var fixture = QualificationPackageFixture.create();
        final var payload = fixture.verify();
        final var document = fixture.campaign();
        final var next = ((ObjectNode) document.path("cases").get(0)).deepCopy();
        next.put("id", "independent");
        ((ArrayNode) document.path("cases")).add(next);
        final var campaign = QualificationCampaign.parse(document);
        final var configuration =
                QualificationConfiguration.read(
                        payload.root().resolve("config/config.synthetic.json"));
        final var files =
                new QualificationControlFiles(
                        payload.root(),
                        payload.root().resolveSibling("control-" + UUID.randomUUID()),
                        true);
        QualificationControlFiles.atomic(files.root().resolve("campaign.json"), document);
        QualificationControlFiles.atomic(
                files.root().resolve("configuration.json"),
                QualificationJson.read(
                        payload.root().resolve("config/config.synthetic.json"), 8192));
        new QualificationSupervisor(payload, campaign, configuration, files, true);
        final var journal =
                CampaignJournal.open(
                        files.root().resolve("journal"),
                        QualificationJson.sha256(files.root().resolve("campaign.json")),
                        payload.manifestSha256());
        final var nonce = UUID.randomUUID();
        journal.append("bootstrap", nonce, CampaignJournal.Kind.RESERVED, "INTENT_BEFORE_PROCESS");
        final var directory = files.attempt("bootstrap", true);
        final var process = ProcessHandle.current();
        final var owner =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("pid", process.pid())
                        .put("start", process.info().startInstant().orElseThrow().toString())
                        .put("nonce", nonce.toString())
                        .put("java", process.info().command().orElseThrow())
                        .put("jar", payload.members().get("etl-dataexport-v2.jar").sha256());
        QualificationControlFiles.atomic(directory.resolve("process.json"), owner);
        journal.append(
                "bootstrap", nonce, CampaignJournal.Kind.STARTED, Long.toString(process.pid()));
        assertTrue(QualificationProcessEvidence.sameLiveOwner(owner));
        final var eventsBefore = journal.read();
        final var result =
                new QualificationSupervisor(payload, campaign, configuration, files, false)
                        .resume();
        assertEquals("OUTCOME_UNKNOWN", result.path("cases").get(0).path("state").asText());
        assertEquals(2, result.path("exit").intValue());
        assertFalse(
                Files.exists(files.root().resolve("case-independent")),
                "A live unreconciled owner must prevent a second worker reservation and SQL session");
        assertEquals(
                eventsBefore,
                journal.read(),
                "An observing resume must not spend another case budget");
    }
}
