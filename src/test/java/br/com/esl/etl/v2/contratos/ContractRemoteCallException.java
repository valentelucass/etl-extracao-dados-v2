package br.com.esl.etl.v2.contratos;

/** Falha sanitizada de transporte remoto, sem URL, token ou corpo de resposta. */
public final class ContractRemoteCallException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ContractRemoteCallException(final String message) {
        super(message);
    }
}
