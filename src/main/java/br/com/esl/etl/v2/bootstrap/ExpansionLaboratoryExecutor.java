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
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Six pipelines and two SQL loads share a persisted plan; no retained execution ledger in Java. */
public final class ExpansionLaboratoryExecutor {
    private final ColetaTemporalLaboratorySession session;
    private final UUID run;
    private final Clock clock;
    private final Clock technicalClock;
    private final JdbcExpansionRecomposition plans;

    public ExpansionLaboratoryExecutor(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final Clock clock,
            final Clock technicalClock) {
        this.session = Objects.requireNonNull(session);
        this.run = Objects.requireNonNull(run);
        this.clock = Objects.requireNonNull(clock);
        this.technicalClock = Objects.requireNonNull(technicalClock);
        plans = new JdbcExpansionRecomposition(session, clock);
    }

    public JdbcExpansionRecomposition.Progress execute(
            final ExecutionMode mode,
            final int revision,
            final int ordinal,
            final boolean hydrate,
            final CancellationToken cancellation,
            final BoundaryObserver observer)
            throws SQLException {
        final var partition = plans.partition(run, mode, revision, ordinal);
        if (!partition.state().equals("PENDING")) {
            return plans.progress(run, mode, revision);
        }
        final var policy = new JdbcExpansionLaboratory(session, clock).policy(run);
        final var slot = partition.slot();
        final var runtime = new LocalExpansionRuntime(session, run, policy, clock, technicalClock);
        final var dependencies =
                new LocalExpansionDependencyRuntime(session, run, policy, clock, technicalClock);
        Boundary boundary = Boundary.BEFORE_CAPTURE;
        try {
            for (final var step : plans.stepsBatch(partition.id(), 6)) {
                boundary = Boundary.BEFORE_CAPTURE;
                cancellation.throwIfCancellationRequested();
                observer.at(boundary);
                final var template = template(step.entity());
                if (!step.captured()) {
                    if (template.syntheticOccurrenceCapture()) {
                        runtime.capture(
                                step.execution(),
                                template,
                                slot.date(),
                                mode,
                                step.original(),
                                ExpansionLaboratoryFixtures.source(
                                        template,
                                        slot.firstRoot(),
                                        slot.roots(),
                                        policy.pageSize(),
                                        slot.sourceRevision()),
                                cancellation);
                    } else {
                        final int count =
                                slot.roots()
                                        - (template == DataExportTemplate.FRETES
                                                        && slot.missingLastFreight()
                                                        && slot.roots() > 0
                                                ? 1
                                                : 0);
                        var source =
                                ExpansionDependencyFixtures.source(
                                        template, slot.firstRoot(), count, policy.pageSize());
                        if (template == DataExportTemplate.FRETES) {
                            source =
                                    source.withFinancialBindings(
                                            ExpansionDependencyFixtures::financialTerms);
                        }
                        dependencies.capture(
                                step.execution(),
                                template,
                                slot.date(),
                                mode,
                                step.original(),
                                source,
                                cancellation);
                    }
                }
                boundary = Boundary.AFTER_CAPTURE;
                observer.at(boundary);
                plans.attach(partition.id(), step.entity());
                boundary = Boundary.AFTER_ATTACH;
                observer.at(boundary);
            }
            final var relations = new JdbcExpansionRelations(session, clock);
            boundary = Boundary.BEFORE_BIND;
            observer.at(boundary);
            for (int first = slot.firstRoot();
                    first < slot.firstRoot() + slot.roots();
                    first += 14) {
                cancellation.throwIfCancellationRequested();
                relations.bind(
                        run,
                        ExpansionLaboratoryRelationFixtures.bindingBatch(
                                slot.date(),
                                first,
                                Math.min(14, slot.firstRoot() + slot.roots() - first)),
                        cancellation);
            }
            relations.resolve(run);
            boundary = Boundary.AFTER_RESOLVE;
            observer.at(boundary);
            if (hydrate) {
                // The plan deliberately omits at most one target per partition. Claim remains
                // bounded.
                new ExpansionLaboratoryHydrator(relations, dependencies)
                        .hydrate(run, 31, cancellation);
            }
            boundary = Boundary.AFTER_HYDRATE;
            observer.at(boundary);
            new JdbcExpansionReferences(session, clock)
                    .importPackaged(
                            run,
                            slot.referenceRevision(),
                            policy.start().minusDays(7),
                            policy.endExclusive().plusDays(7));
            final var material = new JdbcExpansionMaterializations(session, clock);
            cancellation.throwIfCancellationRequested();
            material.invoices(
                    run,
                    partition.invoiceReceipt(),
                    slot.referenceRevision(),
                    mode,
                    mode == ExecutionMode.BOOTSTRAP,
                    slot.date(),
                    slot.date().plusDays(1));
            boundary = Boundary.AFTER_INVOICES;
            observer.at(boundary);
            cancellation.throwIfCancellationRequested();
            material.revenue(
                    run,
                    partition.revenueReceipt(),
                    slot.referenceRevision(),
                    mode,
                    mode == ExecutionMode.BOOTSTRAP,
                    slot.date(),
                    slot.date().plusDays(1));
            boundary = Boundary.AFTER_REVENUE;
            observer.at(boundary);
            plans.complete(partition.id());
            boundary = Boundary.AFTER_COMPLETE;
            observer.at(boundary);
        } catch (final SQLException | RuntimeException failure) {
            final String category =
                    failure instanceof SQLException
                            ? "SQL"
                            : failure instanceof ResilienceCancelledException
                                    ? "CANCELLED"
                                    : failure instanceof IllegalArgumentException
                                            ? "CONTRACT"
                                            : "INJECTED";
            try {
                plans.failure(partition.id(), boundary.name(), category);
            } catch (final SQLException recordingFailure) {
                failure.addSuppressed(recordingFailure);
            }
            throw failure;
        }
        return plans.progress(run, mode, revision);
    }

    private static DataExportTemplate template(final String entity) {
        return switch (entity) {
            case "CAP" -> DataExportTemplate.CONTAS_A_PAGAR;
            case "FAT" -> DataExportTemplate.FATURAS_POR_CLIENTE;
            case "INV" -> DataExportTemplate.INVENTARIO;
            case "SIN" -> DataExportTemplate.SINISTROS;
            case "FRETE" -> DataExportTemplate.FRETES;
            case "LOC" -> DataExportTemplate.LOCALIZACAO_CARGAS;
            default -> throw new IllegalArgumentException("EXP_PLAN_ENTITY");
        };
    }

    public enum Boundary {
        BEFORE_CAPTURE,
        AFTER_CAPTURE,
        AFTER_ATTACH,
        BEFORE_BIND,
        AFTER_RESOLVE,
        AFTER_HYDRATE,
        AFTER_INVOICES,
        AFTER_REVENUE,
        AFTER_COMPLETE
    }

    @FunctionalInterface
    public interface BoundaryObserver {
        void at(Boundary boundary);
    }
}
