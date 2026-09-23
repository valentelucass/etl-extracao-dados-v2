package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Only this child's session is cancelled; no process enumeration or foreign PID termination. */
public final class QualificationCaseControl implements AutoCloseable, CancellationToken {
    private final Path directory;
    private final UUID nonce;
    private final QualificationCampaign.Barrier requested;
    private final long deadline;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<ColetaTemporalLaboratorySession> session =
            new AtomicReference<>();
    private final AtomicReference<String> failure = new AtomicReference<>("NONE");
    private final Thread watcher;

    public QualificationCaseControl(
            final Path directory,
            final UUID nonce,
            final QualificationCampaign.Barrier requested,
            final int seconds) {
        this.directory = directory;
        this.nonce = nonce;
        this.requested = requested;
        deadline = System.nanoTime() + seconds * 1_000_000_000L;
        watcher = new Thread(this::watch, "qualification-owned-cancellation");
        watcher.setDaemon(true);
        watcher.start();
    }

    public void attach(final ColetaTemporalLaboratorySession value) {
        if (!session.compareAndSet(null, value)) {
            throw new IllegalStateException("QUAL_SESSION_ALREADY_ATTACHED");
        }
    }

    @Override
    public boolean isCancellationRequested() {
        return cancelled.get();
    }

    public String reason() {
        return failure.get();
    }

    public void barrier(final QualificationCampaign.Barrier point) {
        throwIfCancellationRequested();
        if (point != requested) {
            return;
        }
        try {
            QualificationControlFiles.atomic(
                    QualificationControlFiles.member(directory, "barrier.json"),
                    JsonNodeFactory.instance
                            .objectNode()
                            .put("nonce", nonce.toString())
                            .put("point", point.name())
                            .put("pid", ProcessHandle.current().pid())
                            .put("observedAt", Instant.now().toString()));
            if (point == QualificationCampaign.Barrier.BEFORE_RECEIPT) {
                // Deliberate receipt loss after rollback: the process exits normally but cannot
                // approve data.
                return;
            }
            if (point == QualificationCampaign.Barrier.DURING_CAPTURE) {
                final var active = session.get();
                if (active == null) {
                    throw new IllegalStateException("QUAL_CAPTURE_WITHOUT_SESSION");
                }
                try (var connection = active.getConnection();
                        var statement = connection.prepareStatement("WAITFOR DELAY '00:00:20'")) {
                    statement.setQueryTimeout(30);
                    statement.execute();
                } catch (final java.sql.SQLException expected) {
                    if (!cancelled.get()) {
                        throw new IllegalStateException("QUAL_BARRIER_JDBC_UNEXPECTED", expected);
                    }
                }
            }
            while (!cancelled.get()) {
                Thread.sleep(50);
            }
            throwIfCancellationRequested();
        } catch (final IOException error) {
            throw new UncheckedIOException(error);
        } catch (final InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("QUAL_BARRIER_INTERRUPTED", error);
        }
    }

    private void watch() {
        try {
            while (!stopped.get()) {
                String reason = null;
                if (System.nanoTime() >= deadline) {
                    reason = "CASE_DEADLINE";
                }
                final var cancel = directory.resolve("cancel.json");
                if (Files.exists(cancel)) {
                    final var json = QualificationJson.read(cancel, 1024);
                    QualificationJson.fields(json, "nonce", "reason");
                    if (!nonce.toString().equals(json.path("nonce").asText())) {
                        throw new IllegalArgumentException("QUAL_CANCEL_FOREIGN_NONCE");
                    }
                    reason = QualificationJson.text(json, "reason", 40);
                    if (!reason.matches(
                            "CONTROLLED_BARRIER|CASE_DEADLINE|CAMPAIGN_DEADLINE|LOG_LIMIT")) {
                        throw new IllegalArgumentException("QUAL_CANCEL_REASON");
                    }
                }
                if (reason != null) {
                    failure.set(reason);
                    cancelled.set(true);
                    final var active = session.get();
                    if (active != null) {
                        active.cancelActiveStatements();
                    }
                    return;
                }
                Thread.sleep(50);
            }
        } catch (final InterruptedException error) {
            Thread.currentThread().interrupt();
            if (!stopped.get()) {
                failure.set("CANCELLATION_WATCH_INTERRUPTED");
                cancelled.set(true);
            }
        } catch (final Exception error) {
            failure.set("CANCELLATION_CONTROL_INVALID");
            cancelled.set(true);
            final var active = session.get();
            if (active != null) {
                try {
                    active.cancelActiveStatements();
                } catch (final java.sql.SQLException cancellation) {
                    failure.set("CANCELLATION_JDBC_FAILED");
                }
            }
        }
    }

    @Override
    public void close() {
        stopped.set(true);
        watcher.interrupt();
        try {
            watcher.join(5000);
        } catch (final InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("QUAL_CANCELLATION_CLOSE_INTERRUPTED", error);
        }
        if (watcher.isAlive()) {
            throw new IllegalStateException("QUAL_CANCELLATION_RESOURCE_UNRELEASED");
        }
    }
}
