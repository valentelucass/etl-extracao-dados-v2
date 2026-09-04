package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;

/** Terminalidade local não equivale a completude do snapshot ou da janela. */
public enum GraphQlTraversalVerification {
    LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED;

    public boolean provesCoverageOrSnapshot() {
        return false;
    }

    public SourceCompletenessStatus completenessStatus() {
        return SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF;
    }
}
