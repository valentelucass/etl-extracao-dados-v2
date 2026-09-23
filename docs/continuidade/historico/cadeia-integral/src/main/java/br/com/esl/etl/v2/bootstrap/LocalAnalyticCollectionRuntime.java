package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionPreparation;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Atomic composition of the existing collection pipeline and its typed analytical preparation. */
public final class LocalAnalyticCollectionRuntime {
    private final ColetaTemporalLaboratorySession session;
    private final UUID run;
    private final UUID relationalRun;
    private final LocalRelationalRuntime relational;
    private final JdbcAnalyticCollectionPreparation preparation;

    public LocalAnalyticCollectionRuntime(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID relationalRun,
            final RelationalLaboratoryPolicy policy,
            final Clock logicalClock,
            final Clock technicalClock) {
        this.session = Objects.requireNonNull(session);
        this.run = Objects.requireNonNull(run);
        this.relationalRun = Objects.requireNonNull(relationalRun);
        relational =
                new LocalRelationalRuntime(
                        session, relationalRun, policy, logicalClock, technicalClock);
        preparation = new JdbcAnalyticCollectionPreparation(session);
    }

    public Capture capture(
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final RelationalSyntheticSource source,
            final CancellationToken cancellation)
            throws SQLException {
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        preparation.requireAssociation(run, relationalRun);
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try {
                final var capture =
                        relational.capture(
                                DataExportTemplate.COLETAS,
                                date,
                                mode,
                                replayOf,
                                Objects.requireNonNull(source).withAnalyticCollectionDetails(),
                                cancellation);
                final var prepared = preparation.prepare(run, capture.executionId(), cancellation);
                cancellation.throwIfCancellationRequested();
                return new Capture(capture, prepared);
            } catch (final SQLException | RuntimeException failure) {
                try {
                    connection.rollback(savepoint);
                } catch (final SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }

    public record Capture(
            LocalRelationalRuntime.Capture source,
            JdbcAnalyticCollectionPreparation.Receipt preparation) {}
}
