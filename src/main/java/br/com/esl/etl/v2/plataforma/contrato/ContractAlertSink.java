package br.com.esl.etl.v2.plataforma.contrato;

/** Boundary obrigatório para tornar visível uma mudança compatível aceita. */
@FunctionalInterface
public interface ContractAlertSink {

    void compatibleChangeAccepted(ContractCompatibilityAlert alert);
}
