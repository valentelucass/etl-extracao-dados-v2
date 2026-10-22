package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real local barrier publication and cancellation ordering, without attaching a SQL session. */
@Timeout(10)
class QualificationCaseControlBarrierLifecycleTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"BEFORE_SQL", "AFTER_PREPARATION"})
    void aPublishedBarrierRemainsWaitingUntilItsOwnCancellationIsObserved(final String name)
            throws Exception {
        final var point = QualificationCampaign.Barrier.valueOf(name);
        final var nonce = UUID.randomUUID();
        try (var control = new QualificationCaseControl(directory, nonce, point, 60)) {
            final var worker = startBarrier(control, point);
            try {
                awaitBarrier(worker);
                assertTrue(worker.thread().isAlive(), "published barrier must not pass by itself");
                assertFalse(control.isCancellationRequested());
                assertEquals("NONE", control.reason());
                final var observed =
                        QualificationJson.read(directory.resolve("barrier.json"), 1024);
                assertEquals(nonce.toString(), observed.path("nonce").asText());
                assertEquals(name, observed.path("point").asText());
                final String before = QualificationJson.sha256(directory.resolve("barrier.json"));

                // This control is written only after observing the actual published barrier.
                QualificationControlFiles.atomic(
                        QualificationControlFiles.member(directory, "cancel.json"),
                        JsonNodeFactory.instance
                                .objectNode()
                                .put("nonce", nonce.toString())
                                .put("reason", "CONTROLLED_BARRIER"));
                awaitTermination(worker);

                assertInstanceOf(ResilienceCancelledException.class, worker.failure().get());
                assertTrue(control.isCancellationRequested());
                assertEquals("CONTROLLED_BARRIER", control.reason());
                assertFalse(worker.interrupted().get());
                assertEquals(before, QualificationJson.sha256(directory.resolve("barrier.json")));
                assertFalse(Files.exists(directory.resolve("receipt.json")));
                assertFalse(Files.exists(directory.resolve("process.json")));
            } finally {
                stopWorker(worker);
            }
        }
    }

    @Test
    void interruptionOfTheWaitingWorkerPreservesItsInterruptAndDoesNotCancelTheCaller()
            throws Exception {
        final boolean callerInterrupted = Thread.currentThread().isInterrupted();
        try (var control =
                new QualificationCaseControl(
                        directory,
                        UUID.randomUUID(),
                        QualificationCampaign.Barrier.BEFORE_SQL,
                        60)) {
            final var worker = startBarrier(control, QualificationCampaign.Barrier.BEFORE_SQL);
            try {
                awaitBarrier(worker);
                worker.thread().interrupt();
                awaitTermination(worker);

                final var failure =
                        assertInstanceOf(IllegalStateException.class, worker.failure().get());
                assertEquals("QUAL_BARRIER_INTERRUPTED", failure.getMessage());
                assertInstanceOf(InterruptedException.class, failure.getCause());
                assertTrue(worker.interrupted().get());
                assertEquals(callerInterrupted, Thread.currentThread().isInterrupted());
                assertFalse(control.isCancellationRequested());
                assertEquals("NONE", control.reason());
                assertFalse(Files.exists(directory.resolve("cancel.json")));
                assertFalse(Files.exists(directory.resolve("receipt.json")));
            } finally {
                stopWorker(worker);
            }
        }
    }

    @Test
    void captureBarrierWithoutAnAttachedSessionRefusesBeforeAnyJdbcWork() throws Exception {
        final var nonce = UUID.randomUUID();
        try (var control =
                new QualificationCaseControl(
                        directory, nonce, QualificationCampaign.Barrier.DURING_CAPTURE, 60)) {
            assertEquals(
                    "QUAL_CAPTURE_WITHOUT_SESSION",
                    assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            control.barrier(
                                                    QualificationCampaign.Barrier.DURING_CAPTURE))
                            .getMessage());
            final var observed = QualificationJson.read(directory.resolve("barrier.json"), 1024);
            assertEquals(nonce.toString(), observed.path("nonce").asText());
            assertEquals("DURING_CAPTURE", observed.path("point").asText());
            assertFalse(control.isCancellationRequested());
            assertEquals("NONE", control.reason());
            assertFalse(Files.exists(directory.resolve("receipt.json")));
            assertFalse(Files.exists(directory.resolve("process.json")));
        }
    }

    @Test
    void anInterruptedBarrierPublicationCannotOverwriteItsReservationOrBecomeAReceipt()
            throws Exception {
        final var reservation = directory.resolve("barrier.json.reserved");
        Files.createFile(reservation);
        try (var control =
                new QualificationCaseControl(
                        directory,
                        UUID.randomUUID(),
                        QualificationCampaign.Barrier.BEFORE_RECEIPT,
                        60)) {
            final var failure =
                    assertThrows(
                            UncheckedIOException.class,
                            () -> control.barrier(QualificationCampaign.Barrier.BEFORE_RECEIPT));
            assertInstanceOf(FileAlreadyExistsException.class, failure.getCause());
            assertEquals(0L, Files.size(reservation));
            assertFalse(Files.exists(directory.resolve("barrier.json")));
            assertFalse(Files.exists(directory.resolve("barrier.json.partial")));
            assertFalse(Files.exists(directory.resolve("receipt.json")));
            assertFalse(control.isCancellationRequested());
            assertEquals("NONE", control.reason());
        }
    }

    private static Worker startBarrier(
            final QualificationCaseControl control, final QualificationCampaign.Barrier point) {
        final var failure = new AtomicReference<Throwable>();
        final var interrupted = new AtomicBoolean();
        final var thread =
                new Thread(
                        () -> {
                            try {
                                control.barrier(point);
                            } catch (final Throwable observed) {
                                failure.set(observed);
                            } finally {
                                interrupted.set(Thread.currentThread().isInterrupted());
                            }
                        },
                        "owned-offline-barrier-test");
        thread.setDaemon(true);
        thread.start();
        return new Worker(thread, failure, interrupted);
    }

    private void awaitBarrier(final Worker worker) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        final var barrier = directory.resolve("barrier.json");
        while (!Files.isRegularFile(barrier)
                && worker.thread().isAlive()
                && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertTrue(Files.isRegularFile(barrier), "own barrier was not published within its bound");
        // Observe the actual waiting phase after publication, rather than interrupting an
        // in-progress file write or relying on an arbitrary delay after starting the worker.
        boolean waiting = false;
        while (worker.thread().isAlive() && System.nanoTime() < deadline) {
            if (worker.thread().getState() == Thread.State.TIMED_WAITING) {
                waiting = true;
                break;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertTrue(waiting, "owned barrier did not enter its bounded waiting phase");
        assertNull(worker.failure().get());
    }

    private static void awaitTermination(final Worker worker) throws InterruptedException {
        worker.thread().join(TimeUnit.SECONDS.toMillis(3));
        assertFalse(worker.thread().isAlive(), "owned barrier worker did not terminate");
        assertNotNull(worker.failure().get(), "blocked barrier cannot finish as successful work");
    }

    private static void stopWorker(final Worker worker) throws InterruptedException {
        if (worker.thread().isAlive()) {
            worker.thread().interrupt();
        }
        worker.thread().join(TimeUnit.SECONDS.toMillis(3));
        assertFalse(worker.thread().isAlive(), "owned barrier worker remains alive after cleanup");
    }

    private record Worker(
            Thread thread, AtomicReference<Throwable> failure, AtomicBoolean interrupted) {}
}
