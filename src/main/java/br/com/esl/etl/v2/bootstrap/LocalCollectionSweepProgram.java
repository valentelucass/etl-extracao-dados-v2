package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicit four-phase synthetic observation program, executed only in a rollback session. */
public final class LocalCollectionSweepProgram {
    private final PinnedLocalJson manifest;
    private final LocalDate start;
    private final LocalDate end;
    private final List<CollectionSweepArtifact> artifacts;

    public LocalCollectionSweepProgram(final Path path, final CancellationToken token)
            throws IOException {
        manifest = PinnedLocalJson.open(path, 16384);
        final var root = manifest.read();
        QualificationJson.fields(
                root,
                "version",
                "mode",
                "target",
                "windowStart",
                "windowEndExclusive",
                "artifacts");
        if (!"local-collection-sweep-program-v1".equals(QualificationJson.text(root, "version", 40))
                || !"LOCAL_ARTIFACT_ROLLBACK".equals(QualificationJson.text(root, "mode", 40))
                || !"localhost/ETL_SISTEMA_V2_SHADOW"
                        .equals(QualificationJson.text(root, "target", 80))) {
            throw new IllegalArgumentException("LOCAL_SWEEP_PROGRAM_MODE_TARGET");
        }
        start = LocalDate.parse(QualificationJson.text(root, "windowStart", 10));
        end = LocalDate.parse(QualificationJson.text(root, "windowEndExclusive", 10));
        if (!start.isBefore(end) || start.plusDays(3).isBefore(end)) {
            throw new IllegalArgumentException("LOCAL_SWEEP_PROGRAM_WINDOW");
        }
        QualificationJson.array(root.path("artifacts"), 4, 4);
        final var list = new ArrayList<CollectionSweepArtifact>(4);
        final UUID validationRun = UUID.randomUUID();
        for (final var entry : root.path("artifacts")) {
            token.throwIfCancellationRequested();
            final var pin =
                    PinnedLocalJson.reference(path.toAbsolutePath().getParent(), entry, 524288);
            final var artifact = new CollectionSweepArtifact(pin, token);
            if (artifact.date().isBefore(start) || !artifact.date().isBefore(end)) {
                throw new IllegalArgumentException("LOCAL_SWEEP_PROGRAM_DATE");
            }
            if (!list.isEmpty()
                    && (!artifact.date().equals(list.get(0).date())
                            || artifact.pageSize() != list.get(0).pageSize()
                            || artifact.snapshot(validationRun).universeRoots()
                                    != list.get(0).snapshot(validationRun).universeRoots())) {
                throw new IllegalArgumentException("LOCAL_SWEEP_PROGRAM_CONTEXT");
            }
            if (artifact.snapshot(validationRun).omitFirst()
                    != (list.size() == 1 || list.size() == 2)) {
                throw new IllegalArgumentException("LOCAL_SWEEP_PROGRAM_SEQUENCE");
            }
            list.add(artifact);
        }
        artifacts = List.copyOf(list);
    }

    List<LocalAnalyticCollectionSweep.Observation> execute(
            final ColetaTemporalLaboratorySession session, final CancellationToken token)
            throws SQLException {
        token.throwIfCancellationRequested();
        try {
            manifest.read();
        } catch (final IOException failure) {
            throw new SQLException("LOCAL_SWEEP_INPUT_CHANGED", failure);
        }
        for (final var artifact : artifacts) {
            try {
                artifact.verifyFiles(token);
            } catch (final IOException failure) {
                throw new SQLException("LOCAL_SWEEP_INPUT_CHANGED", failure);
            }
        }
        final var zone = ZoneId.of("America/Sao_Paulo");
        final var logical = Clock.fixed(end.plusDays(1).atStartOfDay(zone).toInstant(), zone);
        final UUID run = UUID.randomUUID(),
                expansion = UUID.randomUUID(),
                relational = UUID.randomUUID();
        final int pageSize = artifacts.get(0).pageSize();
        final var policy =
                new RelationalLaboratoryPolicy(
                        start, end.minusDays(1), 1000, 10, 3, 60, 2, 0, pageSize, 1000);
        new JdbcRasterLaboratory(session, Clock.systemUTC())
                .start(run, start, end, zone, 100000, 1000);
        new JdbcExpansionLaboratory(session, logical)
                .start(
                        expansion,
                        new ExpansionPolicy(
                                start,
                                end,
                                start,
                                pageSize,
                                1000,
                                100000,
                                FiscalPolicy.SYNTHETIC_CTE));
        new JdbcRelationalLaboratory(session, logical)
                .start(relational, policy, RelationalSyntheticSource.analyticContracts());
        new JdbcAnalyticDimensions(session).associate(run, expansion, relational);
        final var sweep =
                new LocalAnalyticCollectionSweep(
                        session, run, relational, policy, logical, Clock.systemUTC());
        final var result = new ArrayList<LocalAnalyticCollectionSweep.Observation>(4);
        for (final var artifact : artifacts) {
            token.throwIfCancellationRequested();
            result.add(
                    sweep.observe(
                            artifact.snapshot(run),
                            UUID.randomUUID(),
                            artifact.inputs(run, token),
                            token));
        }
        return List.copyOf(result);
    }
}
