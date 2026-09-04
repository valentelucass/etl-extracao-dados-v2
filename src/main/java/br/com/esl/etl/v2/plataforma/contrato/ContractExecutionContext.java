package br.com.esl.etl.v2.plataforma.contrato;

import java.util.Objects;
import java.util.UUID;

/** Capability sanitizada que transporta a identidade vinculada da ocorrência até a travessia. */
public final class ContractExecutionContext {

    private final UUID executionId;
    private final ContractRunGuard runGuard;

    ContractExecutionContext(final UUID executionId, final ContractRunGuard runGuard) {
        this.executionId = Objects.requireNonNull(executionId, "A execução é obrigatória.");
        this.runGuard = Objects.requireNonNull(runGuard, "O guard de contrato é obrigatório.");
    }

    public UUID executionId() {
        return executionId;
    }

    /**
     * Marca a página terminal antes da auditoria de conclusão. O permit permanece bloqueado até a
     * confirmação separada de que essa auditoria retornou com sucesso.
     */
    public void dataExportTraversalCompleted(final int terminalPage) {
        if (runGuard.hasDataExportTraversal()) {
            runGuard.markDataExportTraversalTerminal(terminalPage);
        }
    }

    /** Confirma a auditoria terminal sem permitir que seu callback emita permit prematuramente. */
    public void dataExportCompletionAuditSucceeded(final int terminalPage) {
        if (runGuard.hasDataExportTraversal()) {
            runGuard.markDataExportCompletionAuditSucceeded(terminalPage);
        }
    }

    /** Registra {@code hasNextPage=false}; isso encerra a travessia, não prova completude. */
    public void graphQlTraversalCompleted() {
        runGuard.markGraphQlTraversalTerminal();
    }

    /** Confirma que a auditoria terminal GraphQL retornou sem erro. */
    public void graphQlCompletionAuditSucceeded() {
        runGuard.markGraphQlCompletionAuditSucceeded();
    }

    /** Compara a capability por identidade do guard, sem expor o binding interno. */
    public boolean sameOccurrenceAs(final ContractExecutionContext other) {
        return other != null && runGuard == other.runGuard && executionId.equals(other.executionId);
    }

    /** Invalida toda evidência parcial quando a travessia ou seu consumidor falha. */
    public void traversalFailed() {
        runGuard.invalidateEvidence();
    }

    @Override
    public String toString() {
        return "ContractExecutionContext";
    }
}
