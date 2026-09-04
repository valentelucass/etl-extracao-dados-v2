package br.com.esl.etl.v2.contratos;

/** Declarações formais booleanas do dono do contrato, sem texto ou contato sensível. */
public record ContractTemplateConfirmation(
        boolean orderTotalOrCursorConfirmed, boolean coverageConfirmed) {}
