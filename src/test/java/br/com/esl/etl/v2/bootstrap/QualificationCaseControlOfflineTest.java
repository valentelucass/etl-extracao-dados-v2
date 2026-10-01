package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class QualificationCaseControlOfflineTest {
    @TempDir Path directory;

    @Test
    @Timeout(5)
    void receiptLossBarrierWritesOnlyForItsDeclaredPointWithoutOpeningSql() throws Exception {
        final var nonce = UUID.randomUUID();
        final var barrier = directory.resolve("barrier.json");
        try (var control =
                new QualificationCaseControl(
                        directory, nonce, QualificationCampaign.Barrier.BEFORE_RECEIPT, 3)) {
            control.barrier(QualificationCampaign.Barrier.BEFORE_SQL);
            assertFalse(Files.exists(barrier));
            control.barrier(QualificationCampaign.Barrier.BEFORE_RECEIPT);
            final var observed = QualificationJson.read(barrier, 1024);
            assertEquals(nonce.toString(), observed.path("nonce").asText());
            assertEquals("BEFORE_RECEIPT", observed.path("point").asText());
            assertEquals(ProcessHandle.current().pid(), observed.path("pid").asLong());
            assertFalse(control.isCancellationRequested());
            assertEquals("NONE", control.reason());
        }
    }

    @Test
    @Timeout(5)
    void matchingCancellationStopsOnlyTheOwnedCaseWithTheDeclaredReason() throws Exception {
        final var nonce = UUID.randomUUID();
        try (var control =
                new QualificationCaseControl(
                        directory, nonce, QualificationCampaign.Barrier.BEFORE_SQL, 3)) {
            cancel(nonce, "CONTROLLED_BARRIER");
            awaitCancellation(control);
            assertEquals("CONTROLLED_BARRIER", control.reason());
            assertThrows(
                    ResilienceCancelledException.class,
                    () -> control.barrier(QualificationCampaign.Barrier.BEFORE_SQL));
            assertFalse(Files.exists(directory.resolve("barrier.json")));
        }
    }

    @Test
    @Timeout(5)
    void foreignNonceAndUndeclaredReasonFailClosedBeforeAnySql() throws Exception {
        for (final boolean foreignNonce : new boolean[] {true, false}) {
            final var caseDirectory =
                    Files.createDirectory(directory.resolve("case-" + foreignNonce));
            final var nonce = UUID.randomUUID();
            try (var control =
                    new QualificationCaseControl(
                            caseDirectory, nonce, QualificationCampaign.Barrier.BEFORE_SQL, 3)) {
                QualificationControlFiles.atomic(
                        QualificationControlFiles.member(caseDirectory, "cancel.json"),
                        JsonNodeFactory.instance
                                .objectNode()
                                .put("nonce", (foreignNonce ? UUID.randomUUID() : nonce).toString())
                                .put("reason", foreignNonce ? "CONTROLLED_BARRIER" : "UNDECLARED"));
                awaitCancellation(control);
                assertEquals("CANCELLATION_CONTROL_INVALID", control.reason());
                assertFalse(Files.exists(caseDirectory.resolve("barrier.json")));
            }
        }
    }

    @Test
    @Timeout(5)
    void expiredCaseCancelsWithoutAControlFileOrSqlSession() throws Exception {
        try (var control =
                new QualificationCaseControl(
                        directory,
                        UUID.randomUUID(),
                        QualificationCampaign.Barrier.BEFORE_SQL,
                        0)) {
            awaitCancellation(control);
            assertEquals("CASE_DEADLINE", control.reason());
            assertFalse(Files.exists(directory.resolve("cancel.json")));
        }
    }

    private void cancel(final UUID nonce, final String reason) throws Exception {
        QualificationControlFiles.atomic(
                QualificationControlFiles.member(directory, "cancel.json"),
                JsonNodeFactory.instance
                        .objectNode()
                        .put("nonce", nonce.toString())
                        .put("reason", reason));
    }

    private static void awaitCancellation(final QualificationCaseControl control)
            throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!control.isCancellationRequested() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(control.isCancellationRequested(), "owned watcher must consume control");
    }
}
