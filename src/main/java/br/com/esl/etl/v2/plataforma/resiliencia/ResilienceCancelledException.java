package br.com.esl.etl.v2.plataforma.resiliencia;

/** Cancelamento cooperativo tipado; não deve ser convertido em indisponibilidade da fonte. */
public final class ResilienceCancelledException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ResilienceCancelledException() {
        super("A operação foi cancelada cooperativamente.");
    }

    public ResilienceCancelledException(final Throwable cause) {
        super("A operação foi cancelada cooperativamente.", cause);
    }
}
