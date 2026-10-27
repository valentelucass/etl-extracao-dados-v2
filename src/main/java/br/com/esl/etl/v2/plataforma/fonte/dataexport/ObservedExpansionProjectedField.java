package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/** A mapper value plus explicit kind; no reflective or floating point serialization. */
public record ObservedExpansionProjectedField(String path, Kind kind, ExpansionValue<?> value) {
    public enum Kind {
        TEXT,
        INTEGER,
        IDENTIFIER,
        DECIMAL,
        BOOLEAN,
        DATE,
        INSTANT,
        TIME,
        STRINGS
    }

    public ObservedExpansionProjectedField {
        Objects.requireNonNull(path);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(value);
        if (!path.matches("[a-z][a-z0-9_]{0,127}")) {
            throw new IllegalArgumentException("OBS_CAPTURE_FIELD_PATH");
        }
        final Object typed = value.value();
        if (typed != null) {
            final boolean compatible =
                    switch (kind) {
                        case TEXT, INTEGER, IDENTIFIER -> typed instanceof String;
                        case DECIMAL -> typed instanceof BigDecimal;
                        case BOOLEAN -> typed instanceof Boolean;
                        case DATE -> typed instanceof LocalDate;
                        case INSTANT -> typed instanceof Instant;
                        case TIME -> typed instanceof LocalTime;
                        case STRINGS -> typed instanceof ExpansionStrings;
                    };
            if (!compatible) {
                throw new IllegalArgumentException("OBS_CAPTURE_TYPED_KIND");
            }
        }
    }

    @Override
    public String toString() {
        return "ObservedExpansionProjectedField[path="
                + path
                + ",kind="
                + kind
                + ",presence="
                + value.presence()
                + ",wire="
                + value.wire()
                + "]";
    }
}
