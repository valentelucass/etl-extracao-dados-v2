package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** Recusa um Retry-After válido que exceda o orçamento local, sem expor seu valor bruto. */
public final class DataExportRetryAfterLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataExportRetryAfterLimitExceededException() {
        super("O Retry-After informado pela origem excede o limite configurado.");
    }
}
