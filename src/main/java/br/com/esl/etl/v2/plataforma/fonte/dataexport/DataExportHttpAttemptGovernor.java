package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.time.Duration;
import java.util.Objects;

/** Boundary de admissão, orçamento e deadline aplicado a cada tentativa HTTP ESL. */
public interface DataExportHttpAttemptGovernor {

    AttemptPermit acquire(DataExportHttpAttempt attempt);

    void awaitRetry(Duration delay);

    void imposeRateLimitEmbargo(Duration delay);

    /** Indica se uma resposta terminal ainda precisa proteger os demais workloads da origem. */
    default boolean appliesTerminalEmbargo() {
        return false;
    }

    Duration maximumRetryAfter();

    /** Permit obrigatório por tentativa; seu fechamento libera a admissão global. */
    interface AttemptPermit extends AutoCloseable {

        Duration remainingTime();

        void checkpoint();

        @Override
        void close();
    }

    static DataExportHttpAttemptGovernor ungoverned(
            final Duration requestTimeout,
            final Duration maximumRetryAfter,
            final DataExportSleeper sleeper) {
        Objects.requireNonNull(requestTimeout, "O timeout de request é obrigatório.");
        Objects.requireNonNull(maximumRetryAfter, "O teto de retry é obrigatório.");
        Objects.requireNonNull(sleeper, "O sleeper é obrigatório.");
        return new DataExportHttpAttemptGovernor() {
            @Override
            public AttemptPermit acquire(final DataExportHttpAttempt attempt) {
                Objects.requireNonNull(attempt, "A tentativa HTTP é obrigatória.");
                return new AttemptPermit() {
                    @Override
                    public Duration remainingTime() {
                        return requestTimeout;
                    }

                    @Override
                    public void checkpoint() {
                        // Sem escopo superior no adaptador de compatibilidade.
                    }

                    @Override
                    public void close() {
                        // Sem recurso compartilhado no adaptador de compatibilidade.
                    }
                };
            }

            @Override
            public void awaitRetry(final Duration delay) {
                try {
                    sleeper.sleep(delay);
                } catch (final InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new ResilienceCancelledException(exception);
                }
            }

            @Override
            public void imposeRateLimitEmbargo(final Duration delay) {
                // Compatibilidade local: o executor ainda espera o atraso calculado.
            }

            @Override
            public Duration maximumRetryAfter() {
                return maximumRetryAfter;
            }
        };
    }
}
