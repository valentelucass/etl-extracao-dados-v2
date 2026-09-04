package br.com.esl.etl.v2.contratos;

/** Impede novas conexões depois que uma execução de contrato foi interrompida. */
public final class ContractRunStoppedException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ContractRunStoppedException() {
        super("A execução de contrato já foi interrompida e não pode abrir novas conexões.");
    }
}
