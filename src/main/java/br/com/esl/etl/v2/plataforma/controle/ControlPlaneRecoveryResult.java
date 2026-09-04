package br.com.esl.etl.v2.plataforma.controle;

/** Resumo agregado e sanitizado de uma recuperação de leases vencidas. */
public record ControlPlaneRecoveryResult(long recoveredExecutions) {

    public ControlPlaneRecoveryResult {
        if (recoveredExecutions < 0) {
            throw new IllegalArgumentException(
                    "A quantidade de execuções recuperadas não pode ser negativa.");
        }
    }
}
