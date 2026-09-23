package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.util.Objects;

/** Observes each transport submission, including failed submissions and retries, without data. */
@FunctionalInterface
public interface GraphQlHttpAttemptObserver {
    void beforeAttempt(GraphQlReadOperation operation);

    static GraphQlHttpAttemptObserver noop() {
        return operation -> Objects.requireNonNull(operation, "A operação é obrigatória.");
    }
}
