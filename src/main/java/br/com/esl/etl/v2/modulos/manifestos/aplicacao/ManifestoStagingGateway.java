package br.com.esl.etl.v2.modulos.manifestos.aplicacao;

import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoStageBatch;

/** Porta do staging físico append-only de Manifestos. */
@FunctionalInterface
public interface ManifestoStagingGateway {

    void stage(ManifestoStageBatch batch);

    default void stage(
            final ManifestoStageBatch batch,
            final br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken cancellation) {
        java.util.Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        stage(batch);
    }
}
