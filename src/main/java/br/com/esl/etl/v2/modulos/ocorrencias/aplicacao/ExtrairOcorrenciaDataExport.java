package br.com.esl.etl.v2.modulos.ocorrencias.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;

/** Bounded streaming capture, with transport and storage supplied at the boundary. */
public final class ExtrairOcorrenciaDataExport {
    public ReportSnapshotExtractor.Result execute(
            final BusinessDateRange dates,
            final ReportSnapshotExtractor.Wire wire,
            final ReportSnapshotExtractor.Sink sink,
            final ReportSnapshotExtractor.Limits limits)
            throws Exception {
        return new ReportSnapshotExtractor()
                .extract(
                        ReportSnapshotTemplate.OCORRENCIAS,
                        dates,
                        wire,
                        sink,
                        new OcorrenciaDataExportMapper()::map,
                        limits);
    }
}
