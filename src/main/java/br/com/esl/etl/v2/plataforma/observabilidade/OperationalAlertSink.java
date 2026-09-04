package br.com.esl.etl.v2.plataforma.observabilidade;

@FunctionalInterface
public interface OperationalAlertSink {

    void raise(OperationalAlert alert);
}
