package br.com.esl.etl.v2.plataforma.orquestracao;

import java.util.Objects;

/** Associação explícita entre workload planejado e seu caso de uso. */
public record RuntimeDispatchBinding(RuntimeWorkloadId id, RuntimeWorkloadHandler handler) {
    public RuntimeDispatchBinding {
        Objects.requireNonNull(id, "O workload é obrigatório.");
        Objects.requireNonNull(handler, "O handler é obrigatório.");
    }
}
