package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationProcessEvidence;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationTopology;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualificationProcessEvidenceTest {
    @TempDir Path directory;

    @Test
    void reusedLivePidWithDifferentCreationTimeCannotBecomeTheRecordedOwner() {
        final var nonce = UUID.randomUUID();
        final var java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        final var node =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("pid", ProcessHandle.current().pid())
                        .put("start", Instant.EPOCH.toString())
                        .put("nonce", nonce.toString())
                        .put("java", java.toString())
                        .put("jar", "a".repeat(64));
        QualificationProcessEvidence.process(node, nonce, "a".repeat(64), java);
        assertFalse(QualificationProcessEvidence.sameLiveOwner(node));
        node.put("nonce", UUID.randomUUID().toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationProcessEvidence.process(node, nonce, "a".repeat(64), java));
    }

    @Test
    void exactOutputAndScopeSetsRejectDuplicatesEvenWhenTheirCountsRemainNineteenAndThirtyFive() {
        final var report = report();
        QualificationProcessEvidence.coverage(report);
        final var duplicate = report.deepCopy();
        ((ObjectNode) duplicate.path("outputs").get(18)).put("contract", "SQL-01");
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationProcessEvidence.coverage(duplicate));
        ((ObjectNode) report.path("scopes").get(34)).put("scope", "INPUT_CAP");
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationProcessEvidence.coverage(report));
    }

    @Test
    void rollbackAndTerminalAreRefusedWhenExitOrNonceBelongsToAnotherAttempt() {
        final var nonce = UUID.randomUUID();
        final var hash = "a".repeat(64);
        final var baseline =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("nonce", nonce.toString())
                        .put("aggregate", hash);
        final var receipt =
                JsonNodeFactory.instance.objectNode().put("exit", 0).put("rollback", true);
        final var node =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("nonce", nonce.toString())
                        .put("exit", 0)
                        .put("forced", false)
                        .put("before", hash)
                        .put("after", hash)
                        .put("rollback", true)
                        .put("receipt", hash)
                        .put("terminal", true);
        QualificationProcessEvidence.reconciliation(node, nonce, baseline, receipt, hash);
        node.put("exit", 2);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QualificationProcessEvidence.reconciliation(
                                node, nonce, baseline, receipt, hash));
        node.put("terminal", false).put("receipt", "MISSING");
        QualificationProcessEvidence.reconciliation(node, nonce, baseline, null, "MISSING");
        node.put("nonce", UUID.randomUUID().toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QualificationProcessEvidence.reconciliation(
                                node, nonce, baseline, null, "MISSING"));
    }

    @Test
    void changingReceiptBytesChangesTheJournalEvidenceSeal() throws Exception {
        final var empty = QualificationProcessEvidence.seal(directory);
        assertEquals(64, empty.length());
        Files.writeString(directory.resolve("receipt.json"), "{}");
        final var first = QualificationProcessEvidence.seal(directory);
        Files.writeString(directory.resolve("receipt.json"), "{\"exit\":0}");
        assertNotEquals(first, QualificationProcessEvidence.seal(directory));
        final var receipt = QualificationProcessEvidence.seal(directory);
        assertNotEquals(empty, receipt);
        Files.writeString(directory.resolve("stdout.log"), "partial");
        assertNotEquals(receipt, QualificationProcessEvidence.seal(directory));
    }

    private static ObjectNode report() {
        final var report = JsonNodeFactory.instance.objectNode().put("physicalColumns", 971);
        final var outputs = report.putArray("outputs");
        for (int index = 1; index <= 19; index++) {
            outputs.addObject()
                    .put("contract", String.format(java.util.Locale.ROOT, "SQL-%02d", index))
                    .put("expectedRows", 2)
                    .put("observedRows", 2)
                    .put("differences", 0)
                    .put("gate", "PASS_LOCAL")
                    .putArray("sample");
        }
        final var scopes = report.putArray("scopes");
        QualificationTopology.nodes()
                .forEach(
                        node ->
                                scopes.addObject()
                                        .put("scope", node.id())
                                        .put("state", "PASS_LOCAL")
                                        .put("reason", "TEST_PROOF")
                                        .put("layer", "ORACLE"));
        return report;
    }
}
