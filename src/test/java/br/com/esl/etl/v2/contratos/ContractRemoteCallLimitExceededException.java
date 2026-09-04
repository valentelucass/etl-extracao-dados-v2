package br.com.esl.etl.v2.contratos;

/** Sinaliza que uma sonda de contrato não pode emitir outra chamada remota autorizada. */
public final class ContractRemoteCallLimitExceededException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ContractRemoteCallLimitExceededException(final int maximumCalls) {
        super("O teto autorizado de chamadas remotas foi atingido: " + maximumCalls + ".");
    }
}
