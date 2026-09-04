package br.com.esl.etl.v2.contratos;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Identidade GraphQL mínima de Coleta para cruzamentos exclusivamente em memória. */
public record ContractGraphQlColetaIdentity(
        Optional<String> sourceId,
        Optional<String> sequenceCode,
        List<Optional<String>> pickItemIds) {

    public ContractGraphQlColetaIdentity {
        sourceId = Objects.requireNonNull(sourceId, "O ID de origem é obrigatório.");
        sequenceCode = Objects.requireNonNull(sequenceCode, "O sequence code é obrigatório.");
        pickItemIds =
                List.copyOf(Objects.requireNonNull(pickItemIds, "Os pick items são obrigatórios."));
    }

    @Override
    public String toString() {
        return "ContractGraphQlColetaIdentity[redacted]";
    }
}
