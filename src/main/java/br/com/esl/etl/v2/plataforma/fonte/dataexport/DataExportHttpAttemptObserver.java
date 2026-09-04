package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.Objects;

/** Observa uma tentativa antes de a conexão ser aberta, para limites e telemetria sanitizada. */
@FunctionalInterface
public interface DataExportHttpAttemptObserver {

    void beforeAttempt(DataExportHttpAttempt attempt);

    static DataExportHttpAttemptObserver noop() {
        return attempt -> Objects.requireNonNull(attempt, "A tentativa HTTP é obrigatória.");
    }
}
