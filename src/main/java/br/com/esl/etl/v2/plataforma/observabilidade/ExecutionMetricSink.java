package br.com.esl.etl.v2.plataforma.observabilidade;

@FunctionalInterface
public interface ExecutionMetricSink {

    void record(ExecutionMetricsSnapshot snapshot);
}
