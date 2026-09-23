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
        this.session = Objects.requireNonNull(session);
        this.technicalClock = Objects.requireNonNull(technicalClock);
        this.observer = Objects.requireNonNull(observer);
        this.variant = Objects.requireNonNull(variant);
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
                .start(run.id(), START, END, ZONE, 100000, 1000);
        new JdbcExpansionLaboratory(session, LOGICAL_CLOCK)
                .start(
                        run.expansion(),
                        new ExpansionPolicy(
                                START,
                                END,
                                START,
                                pageSize,
                                1000,
                                100000,
                                FiscalPolicy.SYNTHETIC_CTE));
        new JdbcRelationalLaboratory(session, LOGICAL_CLOCK)
                .start(
                        run.relational(),
                        policy(pageSize),
                        RelationalSyntheticSource.analyticContracts());
        new JdbcAnalyticDimensions(session).associate(run.id(), run.expansion(), run.relational());
        if (variant == AnalyticScenarioVariant.VALUES_AND_NULLS) {
            new br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticSourceContracts(
                            session)
                    .freightPerformance(run.id());
        }
        new JdbcExpansionReferences(session, LOGICAL_CLOCK)
                .importPackaged(
                        run.expansion(), REFERENCE_REVISION, START.minusDays(7), END.plusDays(7));
        new JdbcAnalyticReferences(session, LOGICAL_CLOCK)
                .importManifestPackaged(
                        run.id(),
                        REFERENCE_REVISION,
                        START,
                        END,
                        new JdbcAnalyticReferences.Policies(
                                JdbcAnalyticReferences.Fiscal.CTE_FIRST_REAL_DOCUMENT,
                                JdbcAnalyticReferences.Branch.EXPLICIT_ASSIGNMENT,
                                JdbcAnalyticReferences.Driver.INCLUDE_ALL_BOUND));
        new JdbcAnalyticFleetReferences(session, LOGICAL_CLOCK)
                .importPackaged(run.id(), REFERENCE_REVISION, START, END);
        new JdbcAnalyticCollectionRegions(session)
                .importPackaged(run.id(), REFERENCE_REVISION, START, END);
        final long tariff =
                new JdbcAnalyticQuoteTariffs(session)
                        .importPackaged(run.id(), REFERENCE_REVISION, START, END)
                        .release();
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
        Objects.requireNonNull(run);
        if (run.variant() != variant) {
            throw new IllegalArgumentException("ANA_SCENARIO_VARIANT_BINDING");
        }
        Objects.requireNonNull(mode);
        Objects.requireNonNull(token).throwIfCancellationRequested();
        if (revision < 1
                || revision > 1000
                || mode == ExecutionMode.SWEEP
                || mode == ExecutionMode.REPLAY && previous == null) {
            throw new IllegalArgumentException("ANA_SCENARIO_CYCLE");
        }
        final boolean replay = mode == ExecutionMode.REPLAY;
        final var plans = new JdbcAnalyticScenario(session);
        final var intent =
                plans.begin(
                        run.id(),
                        mode,
                        revision,
                        START,
                        START.plusDays(1),
                        replay ? previous.intent().cycle() : null,
                        REFERENCE_REVISION,
                        run.roots(),
                        run.pageSize(),
                        correction);
        if (!intent.state().equals("PENDING")) {
            throw new IllegalStateException("ANA_SCENARIO_ALREADY_TERMINAL_USE_SQL_STATUS");
        }
        final var expanded =
                new AnalyticExpansionCapture(
                                session,
                                run.expansion(),
                                LOGICAL_CLOCK,
                                technicalClock,
                                observer,
                                variant)
                        .capture(
                                mode,
                                revision,
                                run.roots(),
                                true,
                                run.fault() == Fault.FINANCIAL_REFERENCE_MISSING,
                                token);
        final var relationalPolicy = policy(run.pageSize());
        final var manifest =
                new LocalAnalyticManifestRuntime(
                                session,
                                run.id(),
                                run.relational(),
                                relationalPolicy,
                                LOGICAL_CLOCK,
                                technicalClock)
                        .capture(
                                START,
                                mode,
                                replay ? previous.manifest() : null,
                                AnalyticScenarioFixtures.manifests(
                                                1,
                                                run.roots(),
                                                run.pageSize(),
                                                replay ? previous.sourceRevision() : revision,
                                                correction)
                                        .observed(
                                                observer.forInput(
                                                        AnalyticScenarioObserver.Input.MAN)),
                                token);
        final var collection =
                new LocalAnalyticCollectionRuntime(
                                session,
                                run.id(),
                                run.relational(),
                                relationalPolicy,
                                LOGICAL_CLOCK,
                                technicalClock)
                        .capture(
                                START,
                                mode,
                                replay ? previous.collection() : null,
                                AnalyticCollectionsFixtures.source(1, run.roots(), run.pageSize())
                                        .observed(
                                                observer.forInput(
                                                        AnalyticScenarioObserver.Input.COL)),
                                token);
        final var relationalFreight =
                new LocalRelationalRuntime(
                                session,
                                run.relational(),
                                relationalPolicy,
                                LOGICAL_CLOCK,
                                technicalClock)
                        .capture(
                                DataExportTemplate.FRETES,
                                START,
                                mode,
                                replay ? previous.relationalFreight() : null,
                                RelationalLaboratoryFixtures.source(
                                                DataExportTemplate.FRETES,
                                                START,
                                                1,
                                                run.roots(),
                                                run.pageSize(),
                                                true)
                                        .observed(
                                                observer.forInput(
                                                        AnalyticScenarioObserver.Input.FRE)),
                                token);
        final UUID users = UUID.randomUUID();
        new LocalAnalyticUsersRuntime(
                        session,
                        technicalClock,
                        observer.forInput(AnalyticScenarioObserver.Input.USER))
                .capture(
                        run.id(),
                        users,
                        START,
                        LocalAnalyticUsersRuntime.observationMode(mode),
                        replay ? previous.users() : null,
                        AnalyticUsersFixtures.source(
                                run.id(),
                                Math.min(run.roots(), 256),
                                variant == AnalyticScenarioVariant.VALUES_AND_NULLS),
                        token);
        final UUID quotes = UUID.randomUUID();
        if (mode == ExecutionMode.INCREMENTAL) {
            final var initial = START.atStartOfDay().toInstant(ZoneOffset.UTC);
            new JdbcSqlServerControlPlane(session)
                    .registerIncrementalFrontier(
                            new ExecutionPartitionKey(
                                    "LOCAL_SHADOW",
                                    "LOCAL_V2",
                                    "LOCAL_V2",
                                    "cotacoes",
                                    mode,
                                    initial,
                                    START.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)),
                            initial,
                            technicalClock.instant());
        }
        new LocalAnalyticQuotesRuntime(
                        session,
                        technicalClock,
                        observer.forInput(AnalyticScenarioObserver.Input.COT))
                .capture(
                        run.id(),
                        quotes,
                        START,
                        mode,
                        replay ? previous.quotes() : null,
                        REFERENCE_REVISION,
                        run.tariff(),
                        run.pageSize(),
                        AnalyticQuotesFixtures.source(10000, run.roots(), run.pageSize()),
                        token);
        final var raster = raster(run, mode, revision, token);
        if (run.fault() == Fault.COLLECTION_SNAPSHOT_INVALID) {
            AnalyticScenarioFaults.rejectPartialCollectionSnapshot(
                    session,
                    run,
                    technicalClock,
                    token,
                    observer.forInput(AnalyticScenarioObserver.Input.COL));
        }
        if (!replay) {
            new JdbcAnalyticFixtureBindings(session)
                    .bind(
                            run.id(),
                            revision,
                            correction,
                            correction && (previous == null || !previous.correction()),
                            run.fault() == Fault.MANIFEST_FLEET_MISSING,
                            token);
        }
        final var relations = new JdbcRelationalLaboratory(session, LOGICAL_CLOCK);
        for (int first = 1; first <= run.roots(); first += 32) {
            relations.bindBatch(
                    run.relational(),
                    RelationalLaboratoryFixtures.bindingBatch(
                            START, first, Math.min(32, run.roots() - first + 1)),
                    token);
        }
        relations.resolve(run.relational(), UUID.randomUUID(), token);
        if (!replay) {
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
        final var oldMaterial = new JdbcExpansionMaterializations(session, LOGICAL_CLOCK);
        oldMaterial.invoices(run.expansion(), intent.mat04(), 1, mode, true, START, END);
        oldMaterial.revenue(run.expansion(), intent.mat03(), 1, mode, true, START, END);
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
                replay ? previous.sourceRevision() : revision,
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
        final var source = AnalyticRasterFixtures.source(run.roots(), revision);
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
                                    new RasterWindow(START, END),
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

    private static AnalyticMaterializationRequest request(
            final UUID run, final UUID receipt, final ExecutionMode mode) {
        return new AnalyticMaterializationRequest(
                run, receipt, REFERENCE_REVISION, mode, true, START, END);
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
