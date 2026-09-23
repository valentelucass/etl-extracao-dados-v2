package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticMaterializationRequest;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionRegions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFixtureBindings;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFleetReferences;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuoteTariffs;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticScenario;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionReferences;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** All eleven inputs and five facts execute in the caller's local rollback-only SQL session. */
public final class AnalyticScenarioRuntime {
    public static final LocalDate START = LocalDate.of(2036, 4, 1);
    public static final LocalDate END = START.plusDays(3);
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    public static final Clock LOGICAL_CLOCK =
            Clock.fixed(Instant.parse("2036-04-15T12:00:00Z"), ZONE);
    public static final int REFERENCE_REVISION = 2;
    private final ColetaTemporalLaboratorySession session;
    private final Clock technicalClock;
    private final AnalyticScenarioObserver observer;
    private final AnalyticScenarioVariant variant;
    private final AnalyticExpansionSources expansionSources;
    private final AnalyticRasterSources rasterSources;
    private final AnalyticExpansionRelations relationInputs;
    private final DeclaredIntegralInputs integral;
    private final LocalDate start;
    private final LocalDate end;
    private final Clock logicalClock;
    private final int referenceRevision;

    public AnalyticScenarioRuntime(
            final ColetaTemporalLaboratorySession session, final Clock technicalClock) {
        this(session, technicalClock, AnalyticScenarioObserver.NONE);
    }

    public AnalyticScenarioRuntime(
            final ColetaTemporalLaboratorySession session,
            final Clock technicalClock,
            final AnalyticScenarioObserver observer) {
        this(session, technicalClock, observer, AnalyticScenarioVariant.BASELINE);
    }

    public AnalyticScenarioRuntime(
            final ColetaTemporalLaboratorySession session,
            final Clock technicalClock,
            final AnalyticScenarioObserver observer,
            final AnalyticScenarioVariant variant) {
        this(session, technicalClock, observer, variant, AnalyticExpansionSources.laboratory());
    }

    public AnalyticScenarioRuntime(
            final ColetaTemporalLaboratorySession session,
            final Clock technicalClock,
            final AnalyticScenarioObserver observer,
            final AnalyticScenarioVariant variant,
            final AnalyticExpansionSources expansionSources) {
        this(
                session,
                technicalClock,
                observer,
                variant,
                expansionSources,
                AnalyticRasterSources.laboratory());
    }

    public AnalyticScenarioRuntime(
            final ColetaTemporalLaboratorySession session,
            final Clock technicalClock,
            final AnalyticScenarioObserver observer,
            final AnalyticScenarioVariant variant,
            final AnalyticExpansionSources expansionSources,
            final AnalyticRasterSources rasterSources) {
        this(
                session,
                technicalClock,
                observer,
                variant,
                expansionSources,
                rasterSources,
                AnalyticExpansionRelations.laboratory());
    }

    public AnalyticScenarioRuntime(
            final ColetaTemporalLaboratorySession session,
            final Clock technicalClock,
            final AnalyticScenarioObserver observer,
            final AnalyticScenarioVariant variant,
            final AnalyticExpansionSources expansionSources,
            final AnalyticRasterSources rasterSources,
            final AnalyticExpansionRelations relationInputs) {
        this(
                session,
                technicalClock,
                observer,
                variant,
                expansionSources,
                rasterSources,
                relationInputs,
                null);
    }

    public AnalyticScenarioRuntime(
            final ColetaTemporalLaboratorySession session,
            final Clock technicalClock,
            final AnalyticScenarioObserver observer,
            final DeclaredIntegralInputs integral) {
        this(
                session,
                technicalClock,
                observer,
                AnalyticScenarioVariant.BASELINE,
                integral.expansions(),
                (count, revision) -> {
                    if (count != integral.roots() || revision != integral.revision()) {
                        throw new IllegalArgumentException("INTEGRAL_RASTER_SELECTION");
                    }
                    return integral.raster().gateway();
                },
                integral.relations(),
                integral);
    }

    private AnalyticScenarioRuntime(
            final ColetaTemporalLaboratorySession session,
            final Clock technicalClock,
            final AnalyticScenarioObserver observer,
            final AnalyticScenarioVariant variant,
            final AnalyticExpansionSources expansionSources,
            final AnalyticRasterSources rasterSources,
            final AnalyticExpansionRelations relationInputs,
            final DeclaredIntegralInputs integral) {
        this.integral = integral;
        start = integral == null ? START : integral.start();
        end = integral == null ? END : integral.end();
        logicalClock = integral == null ? LOGICAL_CLOCK : integral.clock();
        referenceRevision =
                integral == null ? REFERENCE_REVISION : integral.references().revision();
        this.session = Objects.requireNonNull(session);
        this.technicalClock = Objects.requireNonNull(technicalClock);
        this.observer = Objects.requireNonNull(observer);
        this.variant = Objects.requireNonNull(variant);
        this.expansionSources = Objects.requireNonNull(expansionSources);
        this.rasterSources = Objects.requireNonNull(rasterSources);
        this.relationInputs = Objects.requireNonNull(relationInputs);
    }

    public Run start(final int roots, final int pageSize) throws SQLException {
        return start(roots, pageSize, Fault.NONE);
    }

    public Run start(final int roots, final int pageSize, final Fault fault) throws SQLException {
        return start(UUID.randomUUID(), roots, pageSize, fault);
    }

    /** An explicit technical run permits proving recovery of the same lock scope after rollback. */
    public Run start(final UUID runId, final int roots, final int pageSize, final Fault fault)
            throws SQLException {
        Objects.requireNonNull(runId);
        Objects.requireNonNull(fault);
        if (roots < 2 || roots > 480 || pageSize < 1 || pageSize > 16) {
            throw new IllegalArgumentException("ANA_SCENARIO_BOUND");
        }
        if (integral != null && (roots != integral.roots() || pageSize != integral.pageSize())) {
            throw new IllegalArgumentException("INTEGRAL_RUN_INPUT_SELECTION");
        }
        if (integral != null && fault != Fault.NONE && fault != Fault.RASTER_INCOMPLETE) {
            throw new IllegalArgumentException("INTEGRAL_FAULT_REQUIRES_DECLARED_INPUT");
        }
        final var run =
                new Run(
                        runId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        roots,
                        pageSize,
                        0,
                        fault,
                        variant);
        new JdbcRasterLaboratory(session, technicalClock)
                .start(
                        run.id(),
                        start,
                        end,
                        ZONE,
                        100000,
                        1000,
                        integral == null
                                ? new br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope(
                                        JdbcRasterLaboratory.SOURCE, JdbcRasterLaboratory.TENANT)
                                : integral.scope());
        new JdbcExpansionLaboratory(session, logicalClock)
                .start(
                        run.expansion(),
                        new ExpansionPolicy(
                                start,
                                end,
                                start,
                                pageSize,
                                1000,
                                100000,
                                integral == null
                                        ? FiscalPolicy.SYNTHETIC_CTE
                                        : integral.fiscalPolicy()),
                        integral == null
                                ? new br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope(
                                        JdbcExpansionLaboratory.SOURCE,
                                        JdbcExpansionLaboratory.TENANT)
                                : integral.scope());
        new JdbcRelationalLaboratory(session, logicalClock)
                .start(
                        run.relational(),
                        relationalPolicy(pageSize),
                        integral == null
                                ? RelationalSyntheticSource.analyticContracts()
                                : integral.contracts(),
                        integral == null
                                ? new br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope(
                                        JdbcRelationalLaboratory.SOURCE,
                                        JdbcRelationalLaboratory.TENANT)
                                : integral.scope());
        new JdbcAnalyticDimensions(session).associate(run.id(), run.expansion(), run.relational());
        if (integral != null) {
            new br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticSourceContracts(
                            session)
                    .integralProtocols(run.id());
            new br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticSourceContracts(
                            session)
                    .integralFreight(
                            run.id(),
                            DeclaredCapturePages.release("FRE").contractFingerprint().sha256());
        }
        if (variant == AnalyticScenarioVariant.VALUES_AND_NULLS) {
            new br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticSourceContracts(
                            session)
                    .freightPerformance(run.id());
        }
        final long tariff;
        if (integral != null) {
            try {
                tariff =
                        integral.references()
                                .importInto(
                                        session,
                                        run.id(),
                                        run.expansion(),
                                        logicalClock,
                                        CancellationToken.none());
            } catch (final java.io.IOException failure) {
                throw new SQLException("INTEGRAL_REFERENCES_CHANGED", failure);
            }
        } else {
            new JdbcExpansionReferences(session, logicalClock)
                    .importPackaged(
                            run.expansion(),
                            referenceRevision,
                            start.minusDays(7),
                            end.plusDays(7));
            new JdbcAnalyticReferences(session, logicalClock)
                    .importManifestPackaged(
                            run.id(),
                            referenceRevision,
                            start,
                            end,
                            new JdbcAnalyticReferences.Policies(
                                    JdbcAnalyticReferences.Fiscal.CTE_FIRST_REAL_DOCUMENT,
                                    JdbcAnalyticReferences.Branch.EXPLICIT_ASSIGNMENT,
                                    JdbcAnalyticReferences.Driver.INCLUDE_ALL_BOUND));
            new JdbcAnalyticFleetReferences(session, logicalClock)
                    .importPackaged(run.id(), referenceRevision, start, end);
            new JdbcAnalyticCollectionRegions(session)
                    .importPackaged(run.id(), referenceRevision, start, end);
            tariff =
                    new JdbcAnalyticQuoteTariffs(session)
                            .importPackaged(run.id(), referenceRevision, start, end)
                            .release();
        }
        return new Run(
                run.id(),
                run.expansion(),
                run.relational(),
                roots,
                pageSize,
                tariff,
                fault,
                variant);
    }

    public Cycle capture(
            final Run run,
            final ExecutionMode mode,
            final int revision,
            final boolean correction,
            final Cycle previous,
            final CancellationToken token)
            throws Exception {
        return capture(run, mode, revision, correction, previous, token, null, null);
    }

    Cycle capture(
            final Run run,
            final ExecutionMode mode,
            final int revision,
            final boolean correction,
            final Cycle previous,
            final CancellationToken token,
            final SequenceAgenda agenda,
            final String stage)
            throws Exception {
        Objects.requireNonNull(run);
        if (integral != null
                && run.fault() != Fault.NONE
                && run.fault() != Fault.RASTER_INCOMPLETE) {
            throw new IllegalArgumentException("INTEGRAL_FAULT_REQUIRES_DECLARED_INPUT");
        }
        if (run.variant() != variant) {
            throw new IllegalArgumentException("ANA_SCENARIO_VARIANT_BINDING");
        }
        Objects.requireNonNull(mode);
        Objects.requireNonNull(token).throwIfCancellationRequested();
        if (integral != null
                && (run.roots() != integral.roots()
                        || run.pageSize() != integral.pageSize()
                        || correction
                        || mode == ExecutionMode.REPLAY
                                && (previous == null
                                        || previous.sourceRevision() != integral.revision()))) {
            throw new IllegalArgumentException("INTEGRAL_CAPTURE_REVISION");
        }
        if (revision < 1
                || revision > 1000
                || mode == ExecutionMode.SWEEP
                || mode == ExecutionMode.REPLAY && previous == null) {
            throw new IllegalArgumentException("ANA_SCENARIO_CYCLE");
        }
        final boolean replay = mode == ExecutionMode.REPLAY;
        final int bootstrapRevision =
                bootstrapRevision(
                        mode, revision, previous == null ? null : previous.bootstrapRevision());
        final LocalDate captureDate = integral == null ? start : integral.captureDate();
        if (integral != null) {
            new br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticSourceContracts(
                            session)
                    .requireIntegralContext(
                            run.id(),
                            run.expansion(),
                            run.relational(),
                            integral.scope(),
                            start,
                            end);
        }
        final var plans = new JdbcAnalyticScenario(session);
        if (agenda != null) {
            agenda.persist(session, run.id(), stage, captureDate);
        }
        final var intent =
                plans.begin(
                        run.id(),
                        mode,
                        revision,
                        captureDate,
                        captureDate.plusDays(1),
                        replay ? previous.intent().cycle() : null,
                        referenceRevision,
                        run.roots(),
                        run.pageSize(),
                        correction);
        if (!intent.state().equals("PENDING")) {
            throw new IllegalStateException("ANA_SCENARIO_ALREADY_TERMINAL_USE_SQL_STATUS");
        }
        final var relationalPolicy = relationalPolicy(run.pageSize());
        // Existing scenarios preserve their qualified order. Declared sequences add the explicit
        // COL -> FRE dependency before either freight consumer can execute.
        AnalyticExpansionCapture.Result expanded =
                stage == null
                        ? expand(run, mode, revision, bootstrapRevision, token, agenda)
                        : null;
        final var manifest =
                new LocalAnalyticManifestRuntime(
                                session,
                                run.id(),
                                run.relational(),
                                relationalPolicy,
                                logicalClock,
                                technicalClock)
                        .capture(
                                agenda == null
                                        ? UUID.randomUUID()
                                        : agenda.execution(run.id(), stage, "MAN"),
                                captureDate,
                                mode,
                                replay ? previous.manifest() : null,
                                integral != null
                                        ? integral.capture("MAN")
                                                .relational(
                                                        observer.forInput(
                                                                AnalyticScenarioObserver.Input.MAN))
                                        : AnalyticScenarioFixtures.manifests(
                                                        1,
                                                        run.roots(),
                                                        run.pageSize(),
                                                        replay
                                                                ? previous.sourceRevision()
                                                                : revision,
                                                        correction)
                                                .observed(
                                                        observer.forInput(
                                                                AnalyticScenarioObserver.Input
                                                                        .MAN)),
                                token,
                                sourceWindow(agenda, "MAN", captureDate));
        final var collection =
                new LocalAnalyticCollectionRuntime(
                                session,
                                run.id(),
                                run.relational(),
                                relationalPolicy,
                                logicalClock,
                                technicalClock)
                        .capture(
                                agenda == null
                                        ? UUID.randomUUID()
                                        : agenda.execution(run.id(), stage, "COL"),
                                captureDate,
                                mode,
                                replay ? previous.collection() : null,
                                integral != null
                                        ? integral.capture("COL")
                                                .relational(
                                                        observer.forInput(
                                                                AnalyticScenarioObserver.Input.COL))
                                        : AnalyticCollectionsFixtures.source(
                                                        1, run.roots(), run.pageSize())
                                                .observed(
                                                        observer.forInput(
                                                                AnalyticScenarioObserver.Input
                                                                        .COL)),
                                token,
                                sourceWindow(agenda, "COL", captureDate));
        // Both freight consumers depend on a successful collection capture in this stage.
        if (stage != null && collection.preparation().roots() != run.roots()) {
            throw new IllegalStateException("SEQUENCE_COLLECTION_DEPENDENCY_INCOMPLETE");
        }
        if (expanded == null) {
            expanded = expand(run, mode, revision, bootstrapRevision, token, agenda);
        }
        final var relationalFreight =
                new LocalRelationalRuntime(
                                session,
                                run.relational(),
                                relationalPolicy,
                                logicalClock,
                                technicalClock)
                        .capture(
                                agenda == null
                                        ? UUID.randomUUID()
                                        : agenda.execution(run.id(), stage, "FRE"),
                                DataExportTemplate.FRETES,
                                captureDate,
                                mode,
                                replay ? previous.relationalFreight() : null,
                                integral != null
                                        ? integral.capture("FRE")
                                                .relational(
                                                        observer.forInput(
                                                                AnalyticScenarioObserver.Input.FRE))
                                        : RelationalLaboratoryFixtures.source(
                                                        DataExportTemplate.FRETES,
                                                        start,
                                                        1,
                                                        run.roots(),
                                                        run.pageSize(),
                                                        true)
                                                .observed(
                                                        observer.forInput(
                                                                AnalyticScenarioObserver.Input
                                                                        .FRE)),
                                token,
                                sourceWindow(agenda, "FRE", captureDate));
        final UUID users = UUID.randomUUID();
        new LocalAnalyticUsersRuntime(
                        session,
                        technicalClock,
                        observer.forInput(AnalyticScenarioObserver.Input.USER),
                        sqlScope())
                .capture(
                        run.id(),
                        users,
                        captureDate,
                        LocalAnalyticUsersRuntime.observationMode(mode),
                        replay ? previous.users() : null,
                        integral != null
                                ? integral.capture("USER").users()
                                : AnalyticUsersFixtures.source(
                                        run.id(),
                                        Math.min(run.roots(), 256),
                                        variant == AnalyticScenarioVariant.VALUES_AND_NULLS),
                        token);
        final UUID quotes =
                agenda == null ? UUID.randomUUID() : agenda.execution(run.id(), stage, "COT");
        final var quoteWindow = sourceWindow(agenda, "COT", captureDate);
        if (mode == ExecutionMode.INCREMENTAL) {
            final var initial = quoteWindow.start();
            new JdbcSqlServerControlPlane(session)
                    .registerIncrementalFrontier(
                            new ExecutionPartitionKey(
                                    "LOCAL_SHADOW",
                                    sqlScope().source(),
                                    sqlScope().tenant(),
                                    "cotacoes",
                                    mode,
                                    initial,
                                    quoteWindow.endExclusive()),
                            initial,
                            technicalClock.instant());
        }
        new LocalAnalyticQuotesRuntime(
                        session,
                        technicalClock,
                        observer.forInput(AnalyticScenarioObserver.Input.COT),
                        sqlScope())
                .capture(
                        run.id(),
                        quotes,
                        captureDate,
                        mode,
                        replay ? previous.quotes() : null,
                        referenceRevision,
                        run.tariff(),
                        run.pageSize(),
                        integral != null
                                ? integral.capture("COT").quotes()
                                : AnalyticQuotesFixtures.source(10000, run.roots(), run.pageSize()),
                        token,
                        quoteWindow);
        final var raster = raster(run, mode, revision, token);
        if (run.fault() == Fault.COLLECTION_SNAPSHOT_INVALID) {
            AnalyticScenarioFaults.rejectPartialCollectionSnapshot(
                    session,
                    run,
                    technicalClock,
                    token,
                    observer.forInput(AnalyticScenarioObserver.Input.COL));
        }
        if (!replay && integral != null) {
            integral.support()
                    .apply(
                            session,
                            run.id(),
                            run.expansion(),
                            run.relational(),
                            relationalFreight.executionId(),
                            logicalClock,
                            token);
        }
        if (!replay && integral == null) {
            new JdbcAnalyticFixtureBindings(session)
                    .bind(
                            run.id(),
                            revision,
                            correction,
                            correction && (previous == null || !previous.correction()),
                            run.fault() == Fault.MANIFEST_FLEET_MISSING,
                            token);
        }
        final var relations = new JdbcRelationalLaboratory(session, logicalClock);
        for (int first = 1; integral == null && first <= run.roots(); first += 32) {
            relations.bindBatch(
                    run.relational(),
                    RelationalLaboratoryFixtures.bindingBatch(
                            start, first, Math.min(32, run.roots() - first + 1)),
                    token);
        }
        relations.resolve(run.relational(), UUID.randomUUID(), token);
        if (!replay && integral == null) {
            new AnalyticScenarioEnrichment(session)
                    .apply(
                            run.id(),
                            run.expansion(),
                            manifest.source().executionId(),
                            relationalFreight.executionId(),
                            revision,
                            correction,
                            token);
        }
        final var material = new JdbcAnalyticMaterializations(session, technicalClock);
        final var freightFact = material.freight(request(run.id(), intent.mat01(), mode), token);
        final var collectors = material.collectors(request(run.id(), intent.mat02(), mode), token);
        final var manifests = material.manifests(request(run.id(), intent.mat05(), mode), token);
        final var oldMaterial = new JdbcExpansionMaterializations(session, logicalClock);
        oldMaterial.invoices(
                run.expansion(),
                intent.mat04(),
                integral == null ? 1 : referenceRevision,
                mode,
                true,
                start,
                end);
        oldMaterial.revenue(
                run.expansion(),
                intent.mat03(),
                integral == null ? 1 : referenceRevision,
                mode,
                true,
                start,
                end);
        final var sources = new ArrayList<JdbcAnalyticScenario.Source>(11);
        for (final var step : expanded.steps()) {
            sources.add(new JdbcAnalyticScenario.Source(step.entity(), step.execution()));
        }
        sources.add(new JdbcAnalyticScenario.Source("MAN", manifest.source().executionId()));
        sources.add(new JdbcAnalyticScenario.Source("COL", collection.source().executionId()));
        sources.add(new JdbcAnalyticScenario.Source("USUARIO", users));
        sources.add(new JdbcAnalyticScenario.Source("COT", quotes));
        sources.add(
                new JdbcAnalyticScenario.Source(
                        "RASTER", raster == null ? null : raster.capture()));
        final var status =
                plans.complete(
                        intent.cycle(),
                        sources,
                        run.fault() == Fault.NONE ? null : run.fault().name(),
                        token);
        return new Cycle(
                mode,
                revision,
                integral != null
                        ? integral.revision()
                        : replay ? previous.sourceRevision() : revision,
                bootstrapRevision,
                correction,
                manifest.source().executionId(),
                collection.source().executionId(),
                relationalFreight.executionId(),
                users,
                quotes,
                raster,
                expanded,
                freightFact,
                collectors,
                manifests,
                intent,
                sources,
                status);
    }

    private RelationalLaboratoryPolicy relationalPolicy(final int pageSize) {
        return integral == null ? policy(pageSize) : integral.policy();
    }

    private AnalyticExpansionCapture.Result expand(
            final Run run,
            final ExecutionMode mode,
            final int revision,
            final int bootstrapRevision,
            final CancellationToken token,
            final SequenceAgenda agenda)
            throws SQLException {
        return new AnalyticExpansionCapture(
                        session,
                        run.expansion(),
                        logicalClock,
                        technicalClock,
                        observer,
                        variant,
                        expansionSources,
                        relationInputs,
                        integral)
                .capture(
                        mode,
                        revision,
                        run.roots(),
                        integral == null,
                        run.fault() == Fault.FINANCIAL_REFERENCE_MISSING,
                        token,
                        agenda,
                        mode == ExecutionMode.REPLAY ? bootstrapRevision : null);
    }

    static int bootstrapRevision(
            final ExecutionMode mode, final int revision, final Integer previousBootstrapRevision) {
        return switch (Objects.requireNonNull(mode)) {
            case BOOTSTRAP -> revision;
            case INCREMENTAL, BACKFILL, REPLAY -> {
                if (previousBootstrapRevision == null) {
                    throw new IllegalArgumentException("ANA_SCENARIO_BOOTSTRAP_LINEAGE");
                }
                yield previousBootstrapRevision;
            }
            default -> throw new IllegalArgumentException("ANA_SCENARIO_BOOTSTRAP_LINEAGE");
        };
    }

    private static LaboratoryCaptureWindow sourceWindow(
            final SequenceAgenda agenda, final String family, final LocalDate date) {
        return agenda == null
                ? LaboratoryCaptureWindow.day(
                        date,
                        ZoneOffset.UTC,
                        br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy.FULL)
                : agenda.window(family);
    }

    private br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope sqlScope() {
        return integral == null
                ? new br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope(
                        "LOCAL_V2", "LOCAL_V2")
                : integral.scope();
    }

    public static RelationalLaboratoryPolicy policy(final int pageSize) {
        return new RelationalLaboratoryPolicy(
                START, END.minusDays(1), 100000, 64, 3, 60, 2, 0, pageSize, 1000);
    }

    private LocalRasterRuntime.Capture raster(
            final Run run,
            final ExecutionMode mode,
            final int revision,
            final CancellationToken token)
            throws Exception {
        final var source =
                rasterSources.open(run.roots(), integral == null ? revision : integral.revision());
        try {
            final var capture =
                    new LocalRasterRuntime(
                                    session,
                                    technicalClock,
                                    ZONE,
                                    observer.forInput(AnalyticScenarioObserver.Input.RASTER))
                            .capture(
                                    run.id(),
                                    mode,
                                    new RasterWindow(start, end),
                                    run.fault() == Fault.RASTER_INCOMPLETE
                                            ? AnalyticScenarioFaults.incompleteRaster(source)
                                            : source,
                                    1000,
                                    100000,
                                    token);
            if (run.fault() == Fault.RASTER_INCOMPLETE) {
                throw new IllegalStateException("ANA_SCENARIO_INCOMPLETE_RASTER_ACCEPTED");
            }
            return capture;
        } catch (final IllegalArgumentException failure) {
            if (run.fault() == Fault.RASTER_INCOMPLETE
                    && "RAS_TERMINAL_UNPROVEN".equals(failure.getMessage())) {
                return null;
            }
            throw failure;
        }
    }

    private AnalyticMaterializationRequest request(
            final UUID run, final UUID receipt, final ExecutionMode mode) {
        return new AnalyticMaterializationRequest(
                run, receipt, referenceRevision, mode, true, start, end);
    }

    public record Run(
            UUID id,
            UUID expansion,
            UUID relational,
            int roots,
            int pageSize,
            long tariff,
            Fault fault,
            AnalyticScenarioVariant variant) {
        public Run(
                final UUID id,
                final UUID expansion,
                final UUID relational,
                final int roots,
                final int pageSize,
                final long tariff,
                final Fault fault) {
            this(
                    id,
                    expansion,
                    relational,
                    roots,
                    pageSize,
                    tariff,
                    fault,
                    AnalyticScenarioVariant.BASELINE);
        }
    }

    public enum Fault {
        NONE,
        RASTER_INCOMPLETE,
        MANIFEST_FLEET_MISSING,
        FINANCIAL_REFERENCE_MISSING,
        COLLECTION_SNAPSHOT_INVALID
    }

    public record Cycle(
            ExecutionMode mode,
            int revision,
            int sourceRevision,
            int bootstrapRevision,
            boolean correction,
            UUID manifest,
            UUID collection,
            UUID relationalFreight,
            UUID users,
            UUID quotes,
            LocalRasterRuntime.Capture raster,
            AnalyticExpansionCapture.Result expanded,
            JdbcAnalyticMaterializations.Receipt freight,
            JdbcAnalyticMaterializations.Receipt collectors,
            JdbcAnalyticMaterializations.Receipt manifests,
            JdbcAnalyticScenario.Intent intent,
            List<JdbcAnalyticScenario.Source> sources,
            JdbcAnalyticScenario.Status status) {
        public Cycle {
            if (sources == null || sources.size() != 11) {
                throw new IllegalArgumentException("ANA_SCENARIO_SOURCE_BOUND");
            }
            sources = List.copyOf(sources);
        }
    }
}
