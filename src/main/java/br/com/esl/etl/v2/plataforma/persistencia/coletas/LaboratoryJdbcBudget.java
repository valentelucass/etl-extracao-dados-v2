package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

/** A case shares one finite allowance across its owned physical SQL sessions. */
public final class LaboratoryJdbcBudget {
    private final long maximum;
    private final AtomicLong used = new AtomicLong();

    public LaboratoryJdbcBudget(final long maximum) {
        if (maximum < 1 || maximum > 100000) {
            throw new IllegalArgumentException("COL_LAB_JDBC_CALL_POLICY");
        }
        this.maximum = maximum;
    }

    void reserve() throws SQLException {
        while (true) {
            final long previous = used.get();
            if (previous >= maximum) {
                throw new SQLException("COL_LAB_JDBC_CALL_LIMIT");
            }
            if (used.compareAndSet(previous, previous + 1)) {
                return;
            }
        }
    }

    public long used() {
        return used.get();
    }
}
