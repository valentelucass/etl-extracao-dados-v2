package br.com.esl.etl.v2.modulos.usuarios.aplicacao;

import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageBatch;

/** Porta síncrona de um microbatch tipado de Usuários. */
@FunctionalInterface
public interface UsuarioStagingGateway {

    void stage(UsuarioStageBatch batch);
}
