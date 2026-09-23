package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalCaptureSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticManifestPreparation;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Atomic composition of the existing manifest pipeline and its typed analytical preparation. */
public final class LocalAnalyticManifestRuntime {
    private final ColetaTemporalLaboratorySession session;
    private final UUID run;
    private final UUID relationalRun;
    private final LocalRelationalRuntime relational;
    private final JdbcAnalyticManifestPreparation preparation;

    public LocalAnalyticManifestRuntime(
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
        preparation = new JdbcAnalyticManifestPreparation(session, technicalClock);
    }

    public Capture capture(
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final RelationalCaptureSource source,
            final CancellationToken cancellation)
            throws SQLException {
        return capture(
                UUID.randomUUID(),
                date,
                mode,
                replayOf,
                source,
                cancellation,
                LaboratoryCaptureWindow.day(
                        date,
                        java.time.ZoneOffset.UTC,
                        br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy.FULL));
    }

    public Capture capture(
            final UUID execution,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final RelationalCaptureSource source,
            final CancellationToken cancellation,
            final LaboratoryCaptureWindow window)
            throws SQLException {
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        preparation.requireAssociation(run, relationalRun);
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try {
                final var capture =
                        relational.capture(
                                execution,
                                DataExportTemplate.MANIFESTOS,
                                date,
                                mode,
                                replayOf,
                                Objects.requireNonNull(source).withAnalyticManifestDetails(),
                                cancellation,
                                window);
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
            JdbcAnalyticManifestPreparation.Receipt preparation) {}
}
