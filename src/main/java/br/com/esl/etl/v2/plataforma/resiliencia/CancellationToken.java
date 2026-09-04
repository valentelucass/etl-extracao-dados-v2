package br.com.esl.etl.v2.plataforma.resiliencia;

/** Sinal cooperativo de cancelamento, sem criar worker ou thread auxiliar. */
@FunctionalInterface
public interface CancellationToken {

    boolean isCancellationRequested();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new ResilienceCancelledException();
        }
    }

    static CancellationToken none() {
        return NeverCancelled.INSTANCE;
    }

    enum NeverCancelled implements CancellationToken {
        INSTANCE;

        @Override
        public boolean isCancellationRequested() {
            return false;
        }
    }
}
