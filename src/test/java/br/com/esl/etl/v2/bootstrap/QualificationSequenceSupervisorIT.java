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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** P04 proof: SEQUENCE keeps the existing supervisor protocol and its rollback-only boundary. */
class QualificationSequenceSupervisorIT {
    @Test
    @Timeout(1800)
    void sequenceSuccessBindsSevenStageReceiptsAndAllThirtyThreePreviews() throws Exception {
        if (PackagedFixtureRuntime.runIfExploded(
                getClass(), "sequenceSuccessBindsSevenStageReceiptsAndAllThirtyThreePreviews")) {
            return;
        }
        final var setup = setup("PASS_LOCAL", "NONE");

        final var result = setup.supervisor().execute();

        assertEquals(0, result.path("exit").intValue());
        assertEquals("PASS_LOCAL", result.path("cases").get(0).path("state").asText());
        final var receipt = receipt(setup);
        assertTrue(receipt.path("rollback").booleanValue());
        assertEquals("PASS_LOCAL", receipt.path("state").asText());
        assertStageEvidence(receipt.path("report"), 7);
        assertEquals(
                CampaignJournal.Kind.TERMINAL,
                setup.journal().read().get(setup.journal().read().size() - 1).kind());
    }

    @Test
    @Timeout(1800)
    void laterSequenceOracleFailurePreservesCompletedStageEvidenceAndRollsBack() throws Exception {
        if (PackagedFixtureRuntime.runIfExploded(
                getClass(),
                "laterSequenceOracleFailurePreservesCompletedStageEvidenceAndRollsBack")) {
            return;
        }
        final var setup = setup("BLOCKED_DEPENDENCY", "NONE");

        final var result = setup.supervisor().execute();

        assertEquals(0, result.path("exit").intValue(), "expected negative test must pass");
        assertEquals("BLOCKED_DEPENDENCY", result.path("cases").get(0).path("state").asText());
        final var receipt = receipt(setup);
        assertTrue(receipt.path("testPassed").booleanValue(), "negative result is contractual");
        assertTrue(receipt.path("rollback").booleanValue());
        assertStageEvidence(receipt.path("report"), 5, "BLOCKED_DEPENDENCY", 0);
        assertDirectFactFailure(receipt.path("report"));
        assertEquals(
                CampaignJournal.Kind.TERMINAL,
                setup.journal().read().get(setup.journal().read().size() - 1).kind());
    }

    @Test
    @Timeout(1800)
    void ownedCancellationPreservesObservedStageEvidenceWithoutGrantingSuccess() throws Exception {
        if (PackagedFixtureRuntime.runIfExploded(
                getClass(),
                "ownedCancellationPreservesObservedStageEvidenceWithoutGrantingSuccess")) {
            return;
        }
        final var setup = setup("CANCELLED", "AFTER_PREPARATION");

        final var result = setup.supervisor().execute();

        assertEquals(0, result.path("exit").intValue());
        assertEquals("CANCELLED", result.path("cases").get(0).path("state").asText());
        final var receipt = receipt(setup);
        assertTrue(receipt.path("rollback").booleanValue());
        assertEquals("CANCELLED", receipt.path("state").asText());
        assertStageEvidence(receipt.path("report"), 7);
        assertTrue(
                setup.journal().read().stream()
                        .anyMatch(event -> event.kind() == CampaignJournal.Kind.BARRIER));
        assertEquals(
                CampaignJournal.Kind.TERMINAL,
                setup.journal().read().get(setup.journal().read().size() - 1).kind());
    }

    @Test
    void sequenceInputMismatchIsRefusedBeforeReservationOrSql() throws Exception {
        if (PackagedFixtureRuntime.runIfExploded(
                getClass(), "sequenceInputMismatchIsRefusedBeforeReservationOrSql")) {
            return;
        }
        final var setup = setup("PASS_LOCAL", "NONE");
        final var document = setup.document();
        document.put("pageSize", 3);
        final var campaign = QualificationCampaign.parse(document);
        final var supervisor =
                new QualificationSupervisor(
                        setup.payload(), campaign, setup.configuration(), setup.files(), false);

        assertEquals(
                "QUAL_SEQUENCE_CASE_LIMITS",
                assertThrows(IllegalArgumentException.class, supervisor::execute).getMessage());
        assertTrue(setup.journal().read().isEmpty());
        assertFalse(Files.exists(setup.files().root().resolve("case-sequence-a")));
    }

    @Test
    void liveSequenceOwnerRefusesResumeBeforeAnotherSequenceCanBeReserved() throws Exception {
        if (PackagedFixtureRuntime.runIfExploded(
                getClass(), "liveSequenceOwnerRefusesResumeBeforeAnotherSequenceCanBeReserved")) {
            return;
        }
        final var setup = setup("PASS_LOCAL", "NONE", true);
        final var journal =
                CampaignJournal.open(
                        setup.files().root().resolve("journal"),
                        QualificationJson.sha256(setup.files().root().resolve("campaign.json")),
                        setup.payload().manifestSha256());
        final var nonce = UUID.randomUUID();
        journal.append("sequence-a", nonce, CampaignJournal.Kind.RESERVED, "INTENT_BEFORE_PROCESS");
        final Path attempt = setup.files().attempt("sequence-a", true);
        final var owner = ProcessHandle.current();
        final var process =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("pid", owner.pid())
                        .put("start", owner.info().startInstant().orElseThrow().toString())
                        .put("nonce", nonce.toString())
                        .put("java", owner.info().command().orElseThrow())
                        .put(
                                "jar",
                                setup.payload().members().get("etl-dataexport-v2.jar").sha256());
        QualificationControlFiles.atomic(attempt.resolve("process.json"), process);
        journal.append(
                "sequence-a", nonce, CampaignJournal.Kind.STARTED, Long.toString(owner.pid()));
        assertTrue(QualificationProcessEvidence.sameLiveOwner(process));
        final var before = journal.read();

        final var result =
                new QualificationSupervisor(
                                setup.payload(),
                                QualificationCampaign.parse(setup.document()),
                                setup.configuration(),
                                setup.files(),
                                false)
                        .resume();

        assertEquals(2, result.path("exit").intValue());
        assertFalse(Files.exists(setup.files().root().resolve("case-sequence-b")));
        assertEquals(before, journal.read());
    }

    private static void assertStageEvidence(
            final com.fasterxml.jackson.databind.JsonNode report, final int expectedStages) {
        assertStageEvidence(report, expectedStages, "PASS_LOCAL", 33);
    }

    private static void assertStageEvidence(
            final com.fasterxml.jackson.databind.JsonNode report,
            final int expectedStages,
            final String terminalState) {
        assertStageEvidence(report, expectedStages, terminalState, 33);
    }

    private static void assertStageEvidence(
            final com.fasterxml.jackson.databind.JsonNode report,
            final int expectedStages,
            final String terminalState,
            final int terminalPreviewCount) {
        assertEquals(expectedStages, report.path("stages").size());
        for (int index = 0; index < expectedStages; index++) {
            final var stage = report.path("stages").get(index);
            assertEquals(
                    index == expectedStages - 1 ? terminalState : "PASS_LOCAL",
                    stage.path("selectedState").asText());
            assertEquals(19, stage.path("outputs").size());
            assertEquals(
                    index == expectedStages - 1 ? terminalPreviewCount : 33,
                    stage.path("sweepPreview").size());
        }
    }

    private static void assertDirectFactFailure(
            final com.fasterxml.jackson.databind.JsonNode report) {
        final var terminal = report.path("stages").get(report.path("stages").size() - 1);
        boolean factFailure = false;
        for (final var scope : terminal.path("scopes")) {
            if (scope.path("state").asText().equals("FAILED")
                    && scope.path("reason").asText().equals("FACT_EQUATION_DIVERGENCE")) {
                factFailure = true;
            }
        }
        assertTrue(
                factFailure, "the direct fact failure must remain observable before propagation");
    }

    private static com.fasterxml.jackson.databind.JsonNode receipt(final Setup setup)
            throws Exception {
        return QualificationJson.read(
                setup.files().attempt("sequence-a", false).resolve("receipt.json"), 1048576);
    }

    private static Setup setup(final String expected, final String barrier) throws Exception {
        return setup(expected, barrier, false);
    }

    private static Setup setup(
            final String expected, final String barrier, final boolean includeSecondSequence)
            throws Exception {
        final var fixture = QualificationPackageFixture.create();
        fixture.includeSequenceCases();
        if (expected.equals("BLOCKED_DEPENDENCY")) {
            fixture.makeSequenceStageFail("sequence-a", 4);
        }
        final var payload = fixture.verify();
        final var document = fixture.sequenceCampaign("sequence-a", expected, barrier);
        if (includeSecondSequence) {
            final var second = ((ObjectNode) document.path("cases").get(0)).deepCopy();
            second.put("id", "sequence-b");
            ((ArrayNode) document.path("cases")).add(second);
        }
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
        final var supervisor =
                new QualificationSupervisor(payload, campaign, configuration, files, true);
        final var journal =
                CampaignJournal.open(
                        files.root().resolve("journal"),
                        QualificationJson.sha256(files.root().resolve("campaign.json")),
                        payload.manifestSha256());
        return new Setup(payload, document, configuration, files, supervisor, journal);
    }

    private record Setup(
            br.com.esl.etl.v2.plataforma.qualificacao.QualifiedPackage payload,
            ObjectNode document,
            QualificationConfiguration configuration,
            QualificationControlFiles files,
            QualificationSupervisor supervisor,
            CampaignJournal journal) {}
}
