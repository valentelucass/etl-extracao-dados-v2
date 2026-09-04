package br.com.esl.etl.v2.plataforma.contrato;

import java.util.Objects;

/** Evidência sanitizada de terminalidade da travessia; não prova completude do dataset. */
public record ContractTraversalTerminalEvidence(ContractSourceKind sourceKind, Mode mode) {

    public ContractTraversalTerminalEvidence {
        sourceKind = Objects.requireNonNull(sourceKind, "A origem terminal é obrigatória.");
        mode = Objects.requireNonNull(mode, "O modo terminal é obrigatório.");
        if (mode.sourceKind != sourceKind) {
            throw new IllegalArgumentException("A evidência terminal não corresponde à origem.");
        }
    }

    public static ContractTraversalTerminalEvidence dataExportEmptyPage() {
        return new ContractTraversalTerminalEvidence(
                ContractSourceKind.DATA_EXPORT, Mode.DATA_EXPORT_SEQUENTIAL_EMPTY_PAGE);
    }

    public static ContractTraversalTerminalEvidence graphQlPageInfoTerminal() {
        return new ContractTraversalTerminalEvidence(
                ContractSourceKind.GRAPHQL, Mode.GRAPHQL_PAGE_INFO_NO_NEXT_PAGE);
    }

    public enum Mode {
        DATA_EXPORT_SEQUENTIAL_EMPTY_PAGE(ContractSourceKind.DATA_EXPORT),
        GRAPHQL_PAGE_INFO_NO_NEXT_PAGE(ContractSourceKind.GRAPHQL);

        private final ContractSourceKind sourceKind;

        Mode(final ContractSourceKind sourceKind) {
            this.sourceKind = sourceKind;
        }
    }
}
