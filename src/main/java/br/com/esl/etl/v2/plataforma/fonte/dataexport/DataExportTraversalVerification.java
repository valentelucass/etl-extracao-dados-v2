package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;

/**
 * Nível de prova associado ao término de uma travessia de páginas.
 *
 * <p>Enquanto o fornecedor não comprovar cursor, ordenação total ou cobertura, uma página vazia
 * encerra apenas a leitura local. Ela não autoriza snapshot, watermark, reconciliação ou expurgo.
 */
public enum DataExportTraversalVerification {
    LOCAL_TERMINAL_UNVERIFIED;

    public boolean provesCoverageOrSnapshot() {
        return false;
    }

    public SourceCompletenessStatus completenessStatus() {
        return SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF;
    }
}
