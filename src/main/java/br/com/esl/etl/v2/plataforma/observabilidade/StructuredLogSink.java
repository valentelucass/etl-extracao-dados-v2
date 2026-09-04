package br.com.esl.etl.v2.plataforma.observabilidade;

@FunctionalInterface
public interface StructuredLogSink {

    void write(StructuredLogEvent event);
}
