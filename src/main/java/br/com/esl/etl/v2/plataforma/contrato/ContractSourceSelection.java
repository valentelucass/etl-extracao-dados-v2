package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.Objects;

/** Read-only selection already bound to a guard; carries no storage or source permission. */
public record ContractSourceSelection(
        ContractSourceKind sourceKind,
        String documentReference,
        String contractVersion,
        ImmutableFingerprint contractFingerprint,
        ImmutableFingerprint runtimeConfigurationFingerprint) {

    public ContractSourceSelection {
        Objects.requireNonNull(sourceKind, "A origem é obrigatória.");
        documentReference = ContractText.documentReference(documentReference);
        contractVersion = ContractText.version(contractVersion);
        Objects.requireNonNull(contractFingerprint, "O fingerprint do contrato é obrigatório.");
        Objects.requireNonNull(
                runtimeConfigurationFingerprint, "O fingerprint runtime é obrigatório.");
    }
}
