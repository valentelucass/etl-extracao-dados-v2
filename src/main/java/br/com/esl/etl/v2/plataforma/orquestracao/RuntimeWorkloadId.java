package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.SqlText;
import java.util.Objects;
import java.util.regex.Pattern;

/** Identificador estável e limitado de um workload no DAG de execução. */
public record RuntimeWorkloadId(String value) implements Comparable<RuntimeWorkloadId> {

    private static final Pattern VALID_VALUE = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    public RuntimeWorkloadId {
        Objects.requireNonNull(value, "O identificador do workload é obrigatório.");
        value = SqlText.trimAsciiSpace(value);
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("O identificador do workload é inválido.");
        }
    }

    @Override
    public int compareTo(final RuntimeWorkloadId other) {
        return value.compareTo(
                Objects.requireNonNull(other, "O workload comparado é obrigatório.").value);
    }
}
