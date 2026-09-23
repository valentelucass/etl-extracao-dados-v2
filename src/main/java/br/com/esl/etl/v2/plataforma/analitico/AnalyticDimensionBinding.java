package br.com.esl.etl.v2.plataforma.analitico;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** A declared temporal assignment. Display names, documents and plates are never matching keys. */
public record AnalyticDimensionBinding(
        Entity entity,
        String sourceKey,
        UUID sourceExecution,
        Role role,
        String entityKey,
        int revision,
        LocalDate from,
        LocalDate toExclusive,
        boolean active,
        String previousEntityKey,
        String evidence) {
    public AnalyticDimensionBinding {
        Objects.requireNonNull(entity);
        Objects.requireNonNull(sourceExecution);
        Objects.requireNonNull(role);
        Objects.requireNonNull(from);
        Objects.requireNonNull(toExclusive);
        if (sourceKey == null
                || sourceKey.isBlank()
                || sourceKey.length() > 256
                || !sourceKey.equals(sourceKey.strip())
                || !synthetic(entityKey)
                || revision < 1
                || revision > 100000
                || !from.isBefore(toExclusive)
                || previousEntityKey != null && !synthetic(previousEntityKey)
                || !synthetic(evidence)) {
            throw new IllegalArgumentException("ANA_DIMENSION_BINDING_CONTRACT");
        }
    }

    private static boolean synthetic(final String value) {
        return value != null && value.matches("synthetic-[A-Za-z0-9-]{1,54}");
    }

    public enum Entity {
        FRETE,
        LOC,
        MAN,
        COL,
        CAP,
        FAT,
        INV,
        SIN,
        RAS,
        USUARIO,
        COT
    }

    public enum Role {
        BRANCH("FILIAL"),
        DEST_BRANCH("FILIAL"),
        PERFORMANCE_BRANCH("FILIAL"),
        CURRENT_BRANCH("FILIAL"),
        UNLOADING_BRANCH("FILIAL"),
        PAYER("CLIENTE"),
        SENDER("CLIENTE"),
        RECIPIENT("CLIENTE"),
        CLIENT("CLIENTE"),
        TRACTOR("VEICULO"),
        TRAILER1("VEICULO"),
        TRAILER2("VEICULO"),
        DRIVER("MOTORISTA"),
        ACCOUNT("PLANOCONTAS"),
        USER("USUARIO"),
        CANCEL_USER("USUARIO"),
        DESTROY_USER("USUARIO");

        private final String dimension;

        Role(final String dimension) {
            this.dimension = dimension;
        }

        public String dimension() {
            return dimension;
        }
    }
}
