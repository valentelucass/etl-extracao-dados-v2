package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageBatch;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Porta de staging limitada a um microbatch de no máximo 100 registros. */
public interface LocalizacaoCargaStagingGateway {
    void stage(LocalizacaoCargaStageBatch batch);

    default void stage(
            final LocalizacaoCargaStageBatch batch, final CancellationToken cancellationToken) {
        Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.")
                .throwIfCancellationRequested();
        stage(batch);
    }
}
