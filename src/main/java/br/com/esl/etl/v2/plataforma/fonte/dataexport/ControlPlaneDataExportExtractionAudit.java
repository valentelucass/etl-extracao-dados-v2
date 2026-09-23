package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePage;
import java.util.Objects;
import java.util.UUID;

/** Auditoria sequencial no control plane. Bytes não medidos pelo evento antigo permanecem zero. */
public final class ControlPlaneDataExportExtractionAudit implements DataExportExtractionAudit {
    private final ControlPlane control;
    private final UUID executionId;
    private final DataExportTemplate template;
    private int pages;
    private long rows;
    private boolean started;
    private boolean terminal;
    private boolean failed;
    private DataExportExtractionResult completed;

    public ControlPlaneDataExportExtractionAudit(
            final ControlPlane control, final UUID executionId, final DataExportTemplate template) {
        this.control = Objects.requireNonNull(control);
        this.executionId = Objects.requireNonNull(executionId);
        this.template = Objects.requireNonNull(template);
    }

    @Override
    public void executionStarted(final ExecutionStarted event) {
        requireExecution(event.executionId());
        if (started || event.template() != template) {
            throw invalid();
        }
        started = true;
    }

    @Override
    public void pageRead(final PageRead event) {
        requireExecution(event.executionId());
        if (!started || terminal || failed || event.page() != pages + 1) {
            throw invalid();
        }
        control.recordPage(
                new ControlPlanePage(
                        executionId,
                        event.page(),
                        1,
                        event.requestedPageSize(),
                        event.recordCount(),
                        event.distinctEntityCount(),
                        0,
                        event.recordCount() == 0,
                        event.readAt()));
        pages = Math.incrementExact(pages);
        rows = Math.addExact(rows, event.recordCount());
        terminal = event.recordCount() == 0;
    }

    @Override
    public void executionCompleted(final DataExportExtractionResult result) {
        requireExecution(result.executionId());
        if (!started
                || !terminal
                || failed
                || completed != null
                || result.template() != template
                || result.pagesFetched() != pages
                || result.terminalPage() != pages
                || result.recordsDelivered() != rows) {
            throw invalid();
        }
        completed = result;
    }

    @Override
    public void executionFailed(final ExecutionFailed event) {
        requireExecution(event.executionId());
        failed = true;
    }

    public void verifyCompletion(final DataExportExtractionResult result) {
        if (failed || completed == null || !completed.equals(result)) {
            throw invalid();
        }
    }

    private void requireExecution(final UUID id) {
        if (!executionId.equals(id)) {
            throw invalid();
        }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("DATA_EXPORT_AUDIT_EVIDENCE_INVALID");
    }
}
