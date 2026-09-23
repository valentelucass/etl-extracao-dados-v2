package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations.Outcome;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Each bounded queue claim captures exactly its explicit target through the existing pipeline. */
public final class ExpansionLaboratoryHydrator {
    private final JdbcExpansionRelations relations;
    private final LocalExpansionDependencyRuntime runtime;

    public ExpansionLaboratoryHydrator(
            final JdbcExpansionRelations relations, final LocalExpansionDependencyRuntime runtime) {
        this.relations = Objects.requireNonNull(relations);
        this.runtime = Objects.requireNonNull(runtime);
    }

    public Result hydrate(final UUID run, final int maximum, final CancellationToken cancellation)
            throws SQLException {
        cancellation.throwIfCancellationRequested();
        final UUID owner = UUID.randomUUID();
        final var claims = relations.claimBatch(run, owner, maximum, 60);
        int completed = 0;
        long observations = 0;
        for (final var claim : claims) {
            cancellation.throwIfCancellationRequested();
            try {
                final var capture =
                        runtime.capture(
                                DataExportTemplate.FRETES,
                                claim.targetDate(),
                                ExecutionMode.BACKFILL,
                                null,
                                ExpansionDependencyFixtures.hydration(
                                                DataExportTemplate.FRETES, claim.targetKey())
                                        .withFinancialBindings(
                                                ExpansionDependencyFixtures::financialTerms),
                                cancellation);
                final Outcome outcome =
                        capture.receipt().quarantine() > 0
                                ? Outcome.CONFLICT
                                : capture.receipt().observed() == 0
                                        ? Outcome.EMPTY
                                        : Outcome.CAPTURED;
                relations.finish(run, claim, owner, outcome, capture.executionId());
                completed += outcome == Outcome.CAPTURED ? 1 : 0;
                observations += capture.receipt().observed();
            } catch (final IllegalArgumentException failure) {
                relations.finish(run, claim, owner, Outcome.INVALID, null);
            } catch (final SQLException failure) {
                relations.finish(run, claim, owner, Outcome.TEMPORARY, null);
                throw failure;
            }
        }
        return new Result(claims.size(), completed, observations, relations.resolve(run));
    }

    public record Result(
            int claimed,
            int completed,
            long capturedObservations,
            JdbcExpansionRelations.Resolution resolution) {}
}
