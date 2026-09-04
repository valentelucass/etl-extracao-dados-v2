package br.com.esl.etl.v2.plataforma.contrato;

import java.util.Objects;

/** Objeto-mapa estrutural explicitamente tratado como opaco pela política versionada. */
public record ContractOpaquePath(ContractResponse.FieldScope scope, String path) {

    public ContractOpaquePath {
        scope = Objects.requireNonNull(scope, "O escopo do path opaco é obrigatório.");
        path = ContractText.pointer(path);
    }

    @Override
    public String toString() {
        return "ContractOpaquePath[scope=" + scope + "]";
    }
}
