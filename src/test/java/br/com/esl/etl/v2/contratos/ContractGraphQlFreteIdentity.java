package br.com.esl.etl.v2.contratos;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Identidade, vínculo e campos financeiros mínimos de Frete, todos transitórios em memória. */
public record ContractGraphQlFreteIdentity(
        Optional<String> sourceId,
        Optional<String> corporationSequenceNumber,
        Optional<String> pickItemId,
        Optional<String> cteKey,
        Optional<BigDecimal> total,
        Optional<String> accountingCreditId,
        Optional<String> accountingCreditInstallmentId,
        Optional<String> referenceNumber) {

    public ContractGraphQlFreteIdentity {
        sourceId = Objects.requireNonNull(sourceId, "O ID de origem é obrigatório.");
        corporationSequenceNumber =
                Objects.requireNonNull(
                        corporationSequenceNumber, "O número corporativo é obrigatório.");
        pickItemId = Objects.requireNonNull(pickItemId, "O vínculo de pick item é obrigatório.");
        cteKey = Objects.requireNonNull(cteKey, "A chave CT-e é obrigatória.");
        total = Objects.requireNonNull(total, "O total é obrigatório.");
        accountingCreditId =
                Objects.requireNonNull(accountingCreditId, "A conta de crédito é obrigatória.");
        accountingCreditInstallmentId =
                Objects.requireNonNull(
                        accountingCreditInstallmentId,
                        "A parcela da conta de crédito é obrigatória.");
        referenceNumber = Objects.requireNonNull(referenceNumber, "A referência é obrigatória.");
    }

    @Override
    public String toString() {
        return "ContractGraphQlFreteIdentity[redacted]";
    }
}
