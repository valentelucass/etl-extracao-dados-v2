package br.com.esl.etl.v2.plataforma.analitico;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Null is explicit; decimal, civil time and instant retain their native types at the boundary. */
public sealed interface AnalyticSqlValue {
    record Missing() implements AnalyticSqlValue {}

    record Text(String value) implements AnalyticSqlValue {
        public Text {
            Objects.requireNonNull(value);
        }
    }

    record Decimal(BigDecimal value) implements AnalyticSqlValue {
        public Decimal {
            Objects.requireNonNull(value);
        }
    }

    record IntegerValue(long value) implements AnalyticSqlValue {}

    record Flag(boolean value) implements AnalyticSqlValue {}

    record Date(LocalDate value) implements AnalyticSqlValue {
        public Date {
            Objects.requireNonNull(value);
        }
    }

    record Time(LocalTime value) implements AnalyticSqlValue {
        public Time {
            Objects.requireNonNull(value);
        }
    }

    record CivilDateTime(LocalDateTime value) implements AnalyticSqlValue {
        public CivilDateTime {
            Objects.requireNonNull(value);
        }
    }

    record OffsetDateTimeValue(OffsetDateTime value) implements AnalyticSqlValue {
        public OffsetDateTimeValue {
            Objects.requireNonNull(value);
        }
    }

    record Identifier(UUID value) implements AnalyticSqlValue {
        public Identifier {
            Objects.requireNonNull(value);
        }
    }
}
