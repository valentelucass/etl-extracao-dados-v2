package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Instant;
import java.util.UUID;

/** Consome sincronamente um lote imutável de até 100 linhas físicas; não retém a travessia. */
@FunctionalInterface
public interface DataExportBatchStaging<T> {

    void stage(
            UUID executionId,
            int batchNumber,
            Iterable<T> records,
            Instant observedAt,
            CancellationToken cancellationToken);
}
