package br.com.esl.etl.v2.modulos.cotacoes.aplicacao;

import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoStageBatch;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Porta de staging de uma página 6906, sem acoplar o domínio ao JDBC. */
@FunctionalInterface
public interface CotacaoStagingGateway {
    void stage(CotacaoStageBatch batch);

    default void stage(final CotacaoStageBatch batch, final CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.")
                .throwIfCancellationRequested();
        stage(batch);
    }
}
