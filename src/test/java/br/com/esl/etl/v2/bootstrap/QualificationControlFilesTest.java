package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QualificationControlFilesTest {
    @Test
    void controlIsAnImmutableSiblingWithinTheDeclaredTargetAndCannotEscapeThroughCaseOrMember()
            throws Exception {
        final var root =
                Files.createDirectories(
                        Path.of("target", "qualification-files-" + UUID.randomUUID()));
        final var payload = Files.createDirectory(root.resolve("pacote com Unicode á"));
        final var files = new QualificationControlFiles(payload, root.resolve("control"), true);
        final var attempt = files.attempt("first", true);
        assertThrows(IllegalArgumentException.class, () -> files.attempt("../escape", true));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationControlFiles.member(attempt, "arbitrary.sql"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new QualificationControlFiles(payload, root.resolve("nested/control"), true));
        final var receipt = QualificationControlFiles.member(attempt, "receipt.json");
        QualificationControlFiles.atomic(
                receipt, JsonNodeFactory.instance.objectNode().put("nonce", "first"));
        final String before = QualificationJson.sha256(receipt);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QualificationControlFiles.atomic(
                                receipt,
                                JsonNodeFactory.instance.objectNode().put("nonce", "second")));
        assertEquals(before, QualificationJson.sha256(receipt));
        assertTrue(Files.exists(attempt.resolve("receipt.json.reserved")));
    }

    @Test
    void interruptedAtomicWriteKeepsItsReservationAndCannotBeTurnedIntoAReceipt() throws Exception {
        final var root =
                Files.createDirectories(
                        Path.of("target", "qualification-partial-" + UUID.randomUUID()));
        final var receipt = QualificationControlFiles.member(root, "receipt.json");
        Files.createFile(root.resolve("receipt.json.reserved"));
        Files.writeString(root.resolve("receipt.json.partial"), "{\"state\":");
        assertThrows(
                java.nio.file.FileAlreadyExistsException.class,
                () ->
                        QualificationControlFiles.atomic(
                                receipt,
                                JsonNodeFactory.instance.objectNode().put("state", "PASS_LOCAL")));
        assertTrue(Files.notExists(receipt));
    }

    @Test
    void deferredCaseHasNoInventedProcessAndCannotLaterBeStartedOrApproved() throws Exception {
        final var root =
                Files.createDirectories(
                        Path.of("target", "qualification-deferred-" + UUID.randomUUID()));
        final var journal =
                CampaignJournal.create(root.resolve("journal"), "a".repeat(64), "b".repeat(64));
        final var nonce = UUID.randomUUID();
        journal.append("first", nonce, CampaignJournal.Kind.RESERVED, "PLANNER");
        journal.append("first", nonce, CampaignJournal.Kind.DEFERRED, "BLOCKED_DEPENDENCY");
        assertEquals(
                QualificationGate.State.BLOCKED_DEPENDENCY, journal.status().get("first").state());
        assertThrows(
                IllegalArgumentException.class,
                () -> journal.append("first", nonce, CampaignJournal.Kind.STARTED, "123"));
        assertThrows(
                IllegalArgumentException.class,
                () -> journal.append("first", nonce, CampaignJournal.Kind.TERMINAL, "PASS_LOCAL"));
    }
}
