package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Porta síncrona do staging tipado de uma página de Coletas. */
@FunctionalInterface
public interface ColetaStagingGateway {

    void stage(ColetaStageBatch batch);

    default void stage(final ColetaStageBatch batch, final CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.")
                .throwIfCancellationRequested();
        stage(batch);
    }
}
