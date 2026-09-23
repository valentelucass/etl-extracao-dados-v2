package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.resiliencia.FailureKind;
import java.util.Objects;
import java.util.Optional;

/** Resultado local; RECOVERY_REQUIRED não afirma qualquer transição terminal no banco. */
public record RuntimeExecutionResult(
        RuntimeWorkloadId workload,
        Status status,
        Optional<StagingPublicationResult> publication,
        Optional<FailureKind> failure,
        Optional<RuntimeExecutionSession> recovery) {
    public enum Status {
        PUBLISHED,
        FAILED,
        BLOCKED,
        CANCELLED,
        RECOVERY_REQUIRED
    }

    public RuntimeExecutionResult {
        Objects.requireNonNull(workload, "O workload é obrigatório.");
        Objects.requireNonNull(status, "O status é obrigatório.");
        Objects.requireNonNull(publication, "A publicação é obrigatória.");
        Objects.requireNonNull(failure, "A falha é obrigatória.");
        Objects.requireNonNull(recovery, "A recuperação é obrigatória.");
        if ((status == Status.PUBLISHED) != publication.isPresent()
                || (status == Status.PUBLISHED) == failure.isPresent()
                || (status == Status.RECOVERY_REQUIRED) != recovery.isPresent()) {
            throw new IllegalArgumentException("Resultado local incoerente.");
        }
    }

    @Override
    public String toString() {
        return "RuntimeExecutionResult[status=" + status + "]";
    }
}
