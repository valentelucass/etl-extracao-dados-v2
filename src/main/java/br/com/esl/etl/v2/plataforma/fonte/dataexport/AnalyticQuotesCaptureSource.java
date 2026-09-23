package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.SyntheticCaptureObserver;

/** Source release and bounded pages supplied to the quotation dispatcher. */
public interface AnalyticQuotesCaptureSource {
    SourceContractRelease contractRelease();

    AnalyticQuotesCaptureSource observed(SyntheticCaptureObserver observer);

    DataExportHttpGatewayBundle bundle(ContractRunGuard guard, ImmutableFingerprint configuration);
}
