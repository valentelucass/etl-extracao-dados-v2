package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/** Bounded lateral relation selection, independent of source page production. */
@FunctionalInterface
public interface AnalyticExpansionRelations {
    void bind(
            JdbcExpansionRelations repository,
            UUID run,
            LocalDate date,
            int roots,
            CancellationToken token)
            throws SQLException;

    static AnalyticExpansionRelations laboratory() {
        return (repository, run, date, roots, token) -> {
            for (int first = 1; first <= roots; first += 14) {
                token.throwIfCancellationRequested();
                repository.bind(
                        run,
                        ExpansionLaboratoryRelationFixtures.bindingBatch(
                                date, first, Math.min(14, roots - first + 1)),
                        token);
            }
        };
    }
}
