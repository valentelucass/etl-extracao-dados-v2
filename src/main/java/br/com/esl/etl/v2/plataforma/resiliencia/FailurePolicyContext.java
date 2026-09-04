package br.com.esl.etl.v2.plataforma.resiliencia;

/** Budgets ainda disponíveis no instante da decisão. */
public record FailurePolicyContext(boolean retryAvailable, boolean repartitionAvailable) {

    public static FailurePolicyContext exhausted() {
        return new FailurePolicyContext(false, false);
    }
}
