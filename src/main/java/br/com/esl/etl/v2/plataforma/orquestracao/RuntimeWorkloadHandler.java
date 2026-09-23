package br.com.esl.etl.v2.plataforma.orquestracao;

/** Handler interno: o retorno do callback nunca constitui evidência de publicação. */
@FunctionalInterface
public interface RuntimeWorkloadHandler {
    void execute(RuntimeExecutionSession session);
}
