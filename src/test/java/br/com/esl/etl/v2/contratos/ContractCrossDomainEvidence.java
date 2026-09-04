package br.com.esl.etl.v2.contratos;

import java.util.Objects;

/** Bloco sanitizado de evidência que liga os comparadores de domínio sem carregar payload. */
public record ContractCrossDomainEvidence(
        ContractFreteColetaRelationResult freteColeta,
        ContractCteInvoiceRelationResult cteInvoice,
        ContractFinancialMatrixEvidence financialMatrix) {

    public ContractCrossDomainEvidence {
        freteColeta = Objects.requireNonNull(freteColeta, "O vínculo Frete–Coleta é obrigatório.");
        cteInvoice = Objects.requireNonNull(cteInvoice, "O vínculo CT-e–fatura é obrigatório.");
        financialMatrix =
                Objects.requireNonNull(financialMatrix, "A matriz financeira é obrigatória.");
    }

    public static ContractCrossDomainEvidence notObserved() {
        return new ContractCrossDomainEvidence(
                ContractFreteColetaRelationResult.notObserved(),
                ContractCteInvoiceRelationResult.notObserved(),
                ContractFinancialMatrixEvidence.notObserved());
    }
}
