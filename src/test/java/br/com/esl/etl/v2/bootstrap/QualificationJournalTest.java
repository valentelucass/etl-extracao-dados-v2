package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualifiedPackage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualificationJournalTest {
    @TempDir Path temporary;

    private CampaignJournal journal(final String name) throws Exception {
        return CampaignJournal.create(temporary.resolve(name), "a".repeat(64), "b".repeat(64));
    }

    @Test
    void reservationPrecedesEffectAndTerminalRequiresObservedRollback() throws Exception {
        final var journal = journal("control");
        final var nonce = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                () -> journal.append("first", nonce, CampaignJournal.Kind.STARTED, "123"));
        journal.append("first", nonce, CampaignJournal.Kind.RESERVED, "SCENARIO");
        journal.append("first", nonce, CampaignJournal.Kind.STARTED, "123,2036-01-01T00:00:00Z");
        assertThrows(
                IllegalArgumentException.class,
                () -> journal.append("first", nonce, CampaignJournal.Kind.TERMINAL, "PASS_LOCAL"));
        assertEquals(
                QualificationGate.State.OUTCOME_UNKNOWN, journal.status().get("first").state());
        journal.append("first", nonce, CampaignJournal.Kind.ROLLBACK, "CONFIRMED");
        journal.append("first", nonce, CampaignJournal.Kind.TERMINAL, "PASS_LOCAL");
        assertEquals(
                QualificationGate.State.PASS_LOCAL,
                CampaignJournal.open(journal.root(), "a".repeat(64), "b".repeat(64))
                        .status()
                        .get("first")
                        .state());
        assertThrows(
                IllegalArgumentException.class,
                () -> journal.append("first", nonce, CampaignJournal.Kind.TERMINAL, "PASS_LOCAL"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        journal.append(
                                "first",
                                UUID.randomUUID(),
                                CampaignJournal.Kind.RESERVED,
                                "REPLAY"));
    }

    @Test
    void lostReceiptEvenAfterRollbackAndExitDoesNotPermitAutomaticDuplication() throws Exception {
        final var journal = journal("lost");
        final var nonce = UUID.randomUUID();
        journal.append("first", nonce, CampaignJournal.Kind.RESERVED, "QUERY");
        journal.append("first", nonce, CampaignJournal.Kind.STARTED, "123");
        journal.append("first", nonce, CampaignJournal.Kind.ROLLBACK, "CONFIRMED");
        journal.append("first", nonce, CampaignJournal.Kind.BARRIER, "BEFORE_RECEIPT");
        final var reopened = CampaignJournal.open(journal.root(), "a".repeat(64), "b".repeat(64));
        assertEquals(
                QualificationGate.State.OUTCOME_UNKNOWN, reopened.status().get("first").state());
        reopened.append(
                "first",
                nonce,
                CampaignJournal.Kind.RECONCILED,
                "OUTCOME_UNKNOWN_ROLLBACK_CONFIRMED");
        assertEquals(
                QualificationGate.State.OUTCOME_UNKNOWN, reopened.status().get("first").state());
        assertThrows(
                IllegalArgumentException.class,
                () -> reopened.append("first", nonce, CampaignJournal.Kind.STARTED, "123"));
    }

    @Test
    void journalRejectsAnotherAttemptRevisionTruncationMutationAndExternalFiles() throws Exception {
        final var journal = journal("corrupt");
        final var nonce = UUID.randomUUID();
        journal.append("first", nonce, CampaignJournal.Kind.RESERVED, "QUERY");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        journal.append(
                                "first", UUID.randomUUID(), CampaignJournal.Kind.STARTED, "123"));
        assertThrows(
                IllegalArgumentException.class,
                () -> CampaignJournal.open(journal.root(), "c".repeat(64), "b".repeat(64)));
        Files.writeString(journal.root().resolve("0001.json"), "{");
        assertThrows(Exception.class, journal::read);
        final var extra = journal("extra");
        Files.writeString(extra.root().resolve("result.log"), "EXIT_0");
        assertThrows(IllegalArgumentException.class, extra::read);
        final var gap = journal("gap");
        gap.append("first", nonce, CampaignJournal.Kind.RESERVED, "QUERY");
        Files.move(gap.root().resolve("0001.json"), gap.root().resolve("0002.json"));
        assertThrows(IllegalArgumentException.class, gap::read);
    }

    @Test
    void packageNamesRejectPlatformEscapesAndPinMismatchBeforeReadingMembers() throws Exception {
        for (final String name :
                List.of(
                        "../x",
                        "C:/x",
                        "//host/x",
                        "file:stream",
                        "CON.txt",
                        "a/./b",
                        "a./b",
                        "a\\b",
                        "package.json",
                        "a/",
                        "á.txt")) {
            assertThrows(IllegalArgumentException.class, () -> QualifiedPackage.safeMember(name));
        }
        assertEquals(
                "lib/dependency-1.0.jar", QualifiedPackage.safeMember("lib/dependency-1.0.jar"));
        Files.writeString(temporary.resolve("package.json"), "{}");
        assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedPackage.verify(temporary, "0".repeat(64)));
    }
}
