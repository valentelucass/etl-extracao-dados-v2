package br.com.esl.etl.v2.contratos;

/** Resposta GraphQL não compatível com o contrato de leitura, sem reter o payload recebido. */
public final class ContractGraphQlResponseException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ContractGraphQlResponseException(final String message) {
        super(message);
    }
}
