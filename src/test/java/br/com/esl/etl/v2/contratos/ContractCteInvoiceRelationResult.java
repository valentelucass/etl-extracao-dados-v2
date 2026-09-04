package br.com.esl.etl.v2.contratos;

/** Resultado agregado da comparação de CT-e normalizada entre Frete, Fatura e GraphQL. */
public record ContractCteInvoiceRelationResult(
        int dataExportFreteCount,
        int invoiceRecordCount,
        int graphQlFreteCount,
        boolean dataExportFreteCtesComplete,
        boolean invoiceCtesComplete,
        boolean graphQlFreteCtesComplete,
        boolean dataExportFreteCtesMatchGraphQl,
        boolean invoiceCtesCoverDataExportFretes,
        boolean relationConfirmed) {

    public ContractCteInvoiceRelationResult {
        if (dataExportFreteCount < 0 || invoiceRecordCount < 0 || graphQlFreteCount < 0) {
            throw new IllegalArgumentException("As contagens de CT-e não podem ser negativas.");
        }
    }

    public static ContractCteInvoiceRelationResult notObserved() {
        return new ContractCteInvoiceRelationResult(
                0, 0, 0, false, false, false, false, false, false);
    }
}
