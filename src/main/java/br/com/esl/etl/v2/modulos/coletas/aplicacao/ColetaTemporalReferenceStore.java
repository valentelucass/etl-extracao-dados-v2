package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalIdentityBinding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalObservation;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionResult;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.UUID;

/** Staging e qualificação observacional; não concede permissão de promoção. */
public interface ColetaTemporalReferenceStore {
    void stage(ColetaTemporalObservation observation, CancellationToken cancellation);

    void complete(GraphQlExtractionResult result, CancellationToken cancellation);

    void bind(ColetaTemporalIdentityBinding binding, CancellationToken cancellation);

    Qualification qualify(
            UUID dataExportExecutionId, UUID referenceExecutionId, CancellationToken cancellation);

    /** Contagens calculadas no SQL, sem transportar o conjunto de candidatos à JVM. */
    record Qualification(long consideredRows, long candidateRows, long blockedRows) {
        public Qualification {
            if (consideredRows < 1
                    || candidateRows < 0
                    || blockedRows < 0
                    || candidateRows > consideredRows
                    || blockedRows != consideredRows - candidateRows) {
                throw new IllegalArgumentException("Contagens temporais inconsistentes.");
            }
        }
    }
}
