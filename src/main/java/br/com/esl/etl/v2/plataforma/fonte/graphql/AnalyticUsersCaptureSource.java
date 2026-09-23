package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.fonte.SyntheticCaptureObserver;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;

/** A bounded USERS_SNAPSHOT observation; it supplies no incremental time filter. */
public interface AnalyticUsersCaptureSource {
    AnalyticUsersCaptureSource observed(SyntheticCaptureObserver observer);

    GraphQlGateway bind(
            GraphQlContractObservationConfiguration observation,
            ContractRunGuard guard,
            CancellationToken cancellation);
}
