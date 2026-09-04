package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.concurrent.ThreadLocalRandom;

/** Fonte injetável de jitter no intervalo fechado-aberto {@code [0, 1)}. */
@FunctionalInterface
interface DataExportJitterSource {

    double sample();

    static DataExportJitterSource threadLocal() {
        return () -> ThreadLocalRandom.current().nextDouble();
    }
}
