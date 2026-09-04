package br.com.esl.etl.v2.contratos;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Página de identidade GraphQL mantida apenas em memória durante a reconciliação de contrato. */
public record ContractGraphQlPage<T>(
        List<T> rows, boolean hasNextPage, Optional<String> endCursor) {

    public ContractGraphQlPage {
        rows = List.copyOf(Objects.requireNonNull(rows, "As linhas são obrigatórias."));
        endCursor = Objects.requireNonNull(endCursor, "O cursor final é obrigatório.");
        if (hasNextPage && endCursor.isEmpty()) {
            throw new IllegalArgumentException("Uma página GraphQL seguinte exige cursor final.");
        }
    }

    @Override
    public String toString() {
        return "ContractGraphQlPage[rows=" + rows.size() + ", pagination=redacted]";
    }
}
