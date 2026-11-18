package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Exactly one admitted status query. The owner persists its effect before another query is
 * eligible.
 */
public final class ExportFilePoller {
    @FunctionalInterface
    public interface Port {
        IndependentExportCycle.Reply get(String relativePath, Duration timeout) throws Exception;
    }

    public interface Journal {
        /**
         * Must reserve this operation durably and reject any previously consumed operation/nonce.
         */
        void reserve(ExportFileLifecycle.PollPlan plan) throws Exception;

        /**
         * Append/read back exact receipt; return its confirmed digest. No canonical control
         * mutation here.
         */
        String appendAndConfirm(ExportFileLifecycle.Receipt receipt) throws Exception;
    }

    public interface AdmittingJournal extends Journal {
        /**
         * Issue within the caller's existing finite budget; never solicit per-query human approval.
         */
        ExportFileLifecycle.Admission nextAdmission(ExportFileLifecycle.Control control)
                throws Exception;
    }

    @FunctionalInterface
    public interface Waiter {
        void await(Duration duration) throws InterruptedException;
    }

    public record DriveResult(String status, ExportFileLifecycle.Control control) {}

    private final IndependentExportCycle.SerialChannel channel;
    private final Port port;
    private final Journal journal;
    private final Clock clock;
    private ExportFileLifecycle.Control control;
    private boolean uncertain;

    public ExportFilePoller(
            final ExportFileLifecycle.Control control,
            final IndependentExportCycle.SerialChannel channel,
            final Port port,
            final Journal journal,
            final Clock clock) {
        this.control = Objects.requireNonNull(control);
        this.channel = Objects.requireNonNull(channel);
        this.port = Objects.requireNonNull(port);
        this.journal = Objects.requireNonNull(journal);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized ExportFileLifecycle.Control poll(
            final ExportFileLifecycle.Admission admission) throws Exception {
        require(!uncertain, "EXPORT_POLL_RECONCILIATION_REQUIRED");
        final var plan = ExportFileLifecycle.plan(control, admission, clock.instant());
        uncertain = true;
        journal.reserve(plan);
        final var reply =
                channel.exchange(timeout -> port.get(plan.path(), timeout), plan.timeout());
        final var receipt = ExportFileLifecycle.observe(control, plan, reply, clock.instant());
        final String persisted = journal.appendAndConfirm(receipt);
        control = ExportFileLifecycle.confirmPersisted(control, receipt, persisted);
        uncertain = false;
        return control;
    }

    /**
     * A bounded generation wait, not transport retry. Each successful iteration commits a new
     * effect/receipt and obtains its next admission automatically from the same owner budget.
     */
    public synchronized DriveResult drive(final Waiter waiter) throws Exception {
        require(journal instanceof AdmittingJournal, "EXPORT_AUTOMATIC_BUDGET_ISSUER_REQUIRED");
        final var issuer = (AdmittingJournal) journal;
        Objects.requireNonNull(waiter);
        for (int iteration = 0; iteration <= control.maximumPolls(); iteration++) {
            if (uncertain) {
                return new DriveResult("PENDING_RECONCILIATION", control);
            }
            if (control.state() != ExportFileLifecycle.State.ENQUEUED
                    && control.state() != ExportFileLifecycle.State.GENERATING) {
                return new DriveResult(
                        control.state() == ExportFileLifecycle.State.GENERATED
                                ? "PENDING_DOWNLOAD_AND_INDEPENDENT_REFERENCE"
                                : "TERMINAL_" + control.state(),
                        control);
            }
            if (control.pollsUsed() >= control.maximumPolls()) {
                return new DriveResult("PENDING_POLL_BUDGET_EXHAUSTED", control);
            }
            final var now = clock.instant();
            require(!now.isBefore(control.lastResponseAt()), "EXPORT_CLOCK_MOVED_BACKWARD");
            final var eligible = control.lastResponseAt().plusSeconds(3);
            if (!now.isBefore(control.deadline()) || !eligible.isBefore(control.deadline())) {
                return new DriveResult("PENDING_POLL_DEADLINE_EXHAUSTED", control);
            }
            if (now.isBefore(eligible)) {
                waiter.await(Duration.between(now, eligible));
            }
            if (!clock.instant().isBefore(control.deadline())) {
                return new DriveResult("PENDING_POLL_DEADLINE_EXHAUSTED", control);
            }
            final var admission = issuer.nextAdmission(control);
            poll(admission);
        }
        return new DriveResult("PENDING_POLL_BUDGET_EXHAUSTED", control);
    }
}
