package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import br.com.esl.etl.v2.modulos.fretes.domain.FreteStageBatch;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Porta de staging 6389 limitada a uma página. */
public interface FreteStagingGateway {
    void stage(FreteStageBatch batch);

    default void stage(final FreteStageBatch batch, final CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.")
                .throwIfCancellationRequested();
        stage(batch);
    }
}
