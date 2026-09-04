package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.util.concurrent.ThreadLocalRandom;

@FunctionalInterface
interface GraphQlJitterSource {

    double sample();

    static GraphQlJitterSource threadLocal() {
        return () -> ThreadLocalRandom.current().nextDouble();
    }
}
