package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRecomposition;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionReferences;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Composes the six existing expansion pipelines, persisted plan, hydrator and MAT03/MAT04. */
public final class AnalyticExpansionCapture {
    private final ColetaTemporalLaboratorySession session;
    private final UUID run;
    private final Clock logicalClock;
    private final Clock technicalClock;
    private final AnalyticScenarioObserver observer;

    public AnalyticExpansionCapture(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final Clock logicalClock,
            final Clock technicalClock) {
        this(session, run, logicalClock, technicalClock, AnalyticScenarioObserver.NONE);
    }

    public AnalyticExpansionCapture(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final Clock logicalClock,
            final Clock technicalClock,
            final AnalyticScenarioObserver observer) {
        this.session = Objects.requireNonNull(session);
        this.run = Objects.requireNonNull(run);
        this.logicalClock = Objects.requireNonNull(logicalClock);
        this.technicalClock = Objects.requireNonNull(technicalClock);
        this.observer = Objects.requireNonNull(observer);
    }

    public Result capture(
            final ExecutionMode mode,
            final int revision,
            final int roots,
            final boolean hydrate,
            final CancellationToken cancellation)
            throws SQLException {
        return capture(mode, revision, roots, hydrate, false, cancellation);
    }

    public Result capture(
            final ExecutionMode mode,
            final int revision,
            final int roots,
            final boolean hydrate,
            final boolean shortFinancialValidity,
            final CancellationToken cancellation)
            throws SQLException {
        if (roots < 2 || roots > 1024 || revision < 1 || revision > 1000) {
            throw new IllegalArgumentException("ANA_EXPANSION_SCENARIO_BOUND");
        }
        final var policy = new JdbcExpansionLaboratory(session, logicalClock).policy(run);
        final var plans = new JdbcExpansionRecomposition(session, logicalClock);
        final int sourceRevision = mode == ExecutionMode.REPLAY ? 1 : revision;
        final boolean missing = mode == ExecutionMode.BOOTSTRAP || mode == ExecutionMode.REPLAY;
        plans.plan(
                run,
                mode,
                revision,
                mode == ExecutionMode.REPLAY ? 1 : null,
                List.of(
                        new JdbcExpansionRecomposition.Slot(
                                1, policy.start(), 1, roots, missing, sourceRevision, 1)));
        final var partition = plans.partition(run, mode, revision, 1);
        if (!partition.state().equals("PENDING")) {
            return new Result(
                    partition.id(),
                    plans.stepsBatch(partition.id(), 6),
                    0,
                    plans.progress(run, mode, revision));
        }
        final var runtime =
                new LocalExpansionRuntime(session, run, policy, logicalClock, technicalClock);
        final var dependencies =
                new LocalExpansionDependencyRuntime(
                        session, run, policy, logicalClock, technicalClock);
        for (final var step : plans.stepsBatch(partition.id(), 6)) {
            cancellation.throwIfCancellationRequested();
            if (!step.captured()) {
                final var template = template(step.entity());
                if (template.syntheticOccurrenceCapture()) {
                    runtime.capture(
                            step.execution(),
                            template,
                            policy.start(),
                            mode,
                            step.original(),
                            AnalyticScenarioFixtures.expansion(
                                            template, 1, roots, policy.pageSize(), sourceRevision)
                                    .observed(observer),
                            cancellation);
                } else {
                    var source =
                            ExpansionDependencyFixtures.source(
                                    template,
                                    1,
                                    roots
                                            - (missing && template == DataExportTemplate.FRETES
                                                    ? 1
                                                    : 0),
                                    policy.pageSize());
                    if (template == DataExportTemplate.FRETES) {
                        source =
                                source.withFinancialBindings(
                                        ExpansionDependencyFixtures::financialTerms);
                    }
                    dependencies.capture(
                            step.execution(),
                            template,
                            policy.start(),
                            mode,
                            step.original(),
                            source.observed(observer),
                            cancellation);
                }
            }
            plans.attach(partition.id(), step.entity());
        }
        final var relations = new JdbcExpansionRelations(session, logicalClock);
        for (int first = 1; first <= roots; first += 14) {
            cancellation.throwIfCancellationRequested();
            relations.bind(
                    run,
                    ExpansionLaboratoryRelationFixtures.bindingBatch(
                            policy.start(), first, Math.min(14, roots - first + 1)),
                    cancellation);
        }
        relations.resolve(run);
        long hydrated = 0;
        if (hydrate) {
            hydrated =
                    new ExpansionLaboratoryHydrator(relations, dependencies)
                            .hydrate(run, 1, cancellation)
                            .completed();
        }
        new JdbcExpansionReferences(session, logicalClock)
                .importPackaged(
                        run,
                        1,
                        shortFinancialValidity ? policy.start() : policy.start().minusDays(7),
                        shortFinancialValidity
                                ? policy.endExclusive()
                                : policy.endExclusive().plusDays(7));
        final var material = new JdbcExpansionMaterializations(session, logicalClock);
        material.invoices(
                run,
                partition.invoiceReceipt(),
                1,
                mode,
                mode == ExecutionMode.BOOTSTRAP,
                policy.start(),
                policy.start().plusDays(1));
        material.revenue(
                run,
                partition.revenueReceipt(),
                1,
                mode,
                mode == ExecutionMode.BOOTSTRAP,
                policy.start(),
                policy.start().plusDays(1));
        plans.complete(partition.id());
        return new Result(
                partition.id(),
                plans.stepsBatch(partition.id(), 6),
                hydrated,
                plans.progress(run, mode, revision));
    }

    private static DataExportTemplate template(final String entity) {
        return switch (entity) {
            case "CAP" -> DataExportTemplate.CONTAS_A_PAGAR;
            case "FAT" -> DataExportTemplate.FATURAS_POR_CLIENTE;
            case "INV" -> DataExportTemplate.INVENTARIO;
            case "SIN" -> DataExportTemplate.SINISTROS;
            case "FRETE" -> DataExportTemplate.FRETES;
            case "LOC" -> DataExportTemplate.LOCALIZACAO_CARGAS;
            default -> throw new IllegalArgumentException("ANA_EXPANSION_ENTITY");
        };
    }

    public record Result(
            UUID partition,
            List<JdbcExpansionRecomposition.Step> steps,
            long hydrated,
            JdbcExpansionRecomposition.Progress progress) {
        public Result {
            if (steps == null || steps.size() != 6 || hydrated < 0 || hydrated > 1) {
                throw new IllegalArgumentException("ANA_EXPANSION_RESULT_BOUND");
            }
            steps = List.copyOf(steps);
        }

        public UUID execution(final String entity) {
            return steps.stream()
                    .filter(step -> step.entity().equals(entity))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("ANA_EXPANSION_ENTITY"))
                    .execution();
        }
    }
}
