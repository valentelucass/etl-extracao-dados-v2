package br.com.esl.etl.v2.contratos;

import java.util.Objects;

/** Janelas fechadas autorizadas para uma entidade durante uma execução de contrato. */
public record ContractEntityWindows(
        ContractDateWindow populated,
        ContractDateWindow empty,
        ContractDateWindow lateChangeBusiness,
        ContractInstantWindow lateChangeUpdatedAt) {

    public ContractEntityWindows {
        populated = Objects.requireNonNull(populated, "A janela populada é obrigatória.");
        empty = Objects.requireNonNull(empty, "A janela vazia é obrigatória.");
        lateChangeBusiness =
                Objects.requireNonNull(
                        lateChangeBusiness,
                        "A janela de negócio da alteração tardia é obrigatória.");
        lateChangeUpdatedAt =
                Objects.requireNonNull(
                        lateChangeUpdatedAt, "A janela de atualização tardia é obrigatória.");
    }
}
