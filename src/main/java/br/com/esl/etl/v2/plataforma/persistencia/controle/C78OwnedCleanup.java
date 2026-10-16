package br.com.esl.etl.v2.plataforma.persistencia.controle;

import java.sql.SQLException;

/** Preserves fatal failures while independent cleanup attempts still run. */
final class C78OwnedCleanup {
    private C78OwnedCleanup() {}

    static Throwable failure(Throwable failure, String code) {
        return failure instanceof SQLException || failure instanceof Error
                ? failure
                : new SQLException(code, failure);
    }

    static Throwable combine(Throwable primary, Throwable cleanup) {
        if (primary == null) {
            return cleanup;
        }
        if (primary != cleanup) {
            primary.addSuppressed(cleanup);
        }
        return primary;
    }

    static void rethrow(Throwable failure) throws SQLException {
        if (failure instanceof Error fatal) {
            throw fatal;
        }
        if (failure instanceof SQLException sql) {
            throw sql;
        }
    }
}
