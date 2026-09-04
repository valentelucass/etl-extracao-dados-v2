package br.com.esl.etl.v2.plataforma.autorizacao;

/** Porta síncrona: retornar confirma a gravação; falhar impede a operação. */
@FunctionalInterface
public interface RuntimeAuthorizationAudit {

    void record(RuntimeAuthorizationEvent event);
}
