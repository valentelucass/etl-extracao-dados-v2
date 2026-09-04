package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;

/** Fingerprint determinístico de filtros, paginação e demais semânticas fora do payload. */
public final class ContractSemanticsFingerprint {

    private ContractSemanticsFingerprint() {}

    /**
     * Codifica elementos ordenados com separação de domínio, tamanho prefixado e UTF-8 estrito.
     * Alterar a ordem ou qualquer elemento altera o fingerprint.
     */
    public static ImmutableFingerprint create(
            final String contractVersion, final String... orderedElements) {
        return ContractCanonicalizer.sourceSemantics(contractVersion, orderedElements);
    }
}
