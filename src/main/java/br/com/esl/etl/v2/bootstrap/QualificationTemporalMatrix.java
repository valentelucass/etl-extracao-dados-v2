package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalCoordinator;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuoteTariffs;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.controle.JdbcSqlServerTemporalPlan;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.relacional.RelationalCaptureContracts;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Current five-workload policies drive actual captures; terminal SQL alone determines frontier. */
public final class QualificationTemporalMatrix {
    private static final LocalDate DATE = LocalDate.of(2036, 4, 1);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final ColetaTemporalLaboratorySession session;
    private final AnalyticScenarioObserver observer;
    private final CancellationToken token;

    public QualificationTemporalMatrix(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioObserver observer,
            final CancellationToken token) {
        this.session = Objects.requireNonNull(session);
        this.observer = Objects.requireNonNull(observer);
        this.token = Objects.requireNonNull(token);
    }

    public ObjectNode execute() throws Exception {
        final var report = JsonNodeFactory.instance.objectNode();
        final var rows = report.putArray("workloads");
        final var policies = policies();
        for (final var item : policies) {
            rows.add(isolated(() -> captureWorkload(item)));
        }
        final var base =
                policies.stream()
                        .filter(p -> p.workload.equals("cotacoes"))
                        .findFirst()
                        .orElseThrow()
                        .policy;
        report.set("sourceWindows", isolated(() -> sourceWindows(base)));
        report.set("frontiers", isolated(() -> frontiers(base)));
        report.set("civilAndAdmission", isolated(() -> civilAndAdmission(base)));
        return report.put("passed", true).put("fixtureRecovery", "ROLLBACK_EACH_ISOLATED_PROOF");
    }

    private static List<RuntimeTemporalOperation> policies() throws Exception {
        try (var stream =
                QualificationTemporalMatrix.class.getResourceAsStream(
                        "/analytic-laboratory/temporal-matrix.synthetic.json")) {
            if (stream == null) {
                throw new IllegalArgumentException("QUAL_TEMPORAL_MATRIX_MISSING");
            }
            final var bytes = stream.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new IllegalArgumentException("QUAL_TEMPORAL_MATRIX_BOUND");
            }
            final var root = QualificationJson.parse(bytes, 16384);
            QualificationJson.fields(root, "version", "origin", "workloads");
            require(
                    root.path("version").asText().equals("qualification-temporal-matrix-v1")
                            && root.path("origin").asText().equals("CURRENT_FIVE_WORKLOAD_POLICIES")
                            && root.path("workloads").size() == 5,
                    "MATRIX_CONTRACT");
            final var result = new ArrayList<RuntimeTemporalOperation>();
            for (final var row : root.path("workloads")) {
                QualificationJson.fields(row, "path", "sha256", "document");
                require(
                        row.path("path")
                                        .asText()
                                        .matches(
                                                "config/laboratory/bloco5[45]-temporal-[a-z_]+\\.json")
                                && row.path("sha256").asText().matches("[a-f0-9]{64}"),
                        "MATRIX_ORIGIN");
                result.add(new RuntimeTemporalOperation(row.get("document")));
            }
            require(
                    result.stream().map(p -> p.workload).distinct().count() == 5,
                    "MATRIX_DUPLICATE");
            return List.copyOf(result);
        }
    }

    private ObjectNode captureWorkload(final RuntimeTemporalOperation operation) throws Exception {
        token.throwIfCancellationRequested();
        final var fixture = fixture(DATE);
        final var policy = operation.policy;
        final var tick =
                DATE.plusDays(1).atStartOfDay(ZONE).toInstant().plus(policy.stabilization());
        final String workload = operation.workload;
        final String source = source(workload), tenant = tenant(workload);
        final String namespace = namespace(source, tenant, workload, policy.mode());
        final var coordinator =
                new RuntimeTemporalCoordinator(new JdbcSqlServerTemporalPlan(session));
        final var plan =
                coordinator.persistCatchUp(UUID.randomUUID(), namespace, policy, DATE, tick);
        require(plan.windows().size() == 1, "MATRIX_WINDOW_COUNT");
        final var window = plan.windows().get(0);
        final var capture = LaboratoryCaptureWindow.planned(window, policy.zone());
        final var execution = execution(namespace, window);
        final String expected;
        int sourceCaptures = 1;
        if (workload.equals("cotacoes")) {
            quote(fixture, execution, DATE, policy.mode(), null, capture, false, false, 1);
            expected = "PUBLISHED";
        } else if (workload.equals("localizacao_cargas")) {
            new LocalExpansionDependencyRuntime(
                            session,
                            fixture.expansion(),
                            fixture.expansionPolicy(),
                            fixture.logical(),
                            Clock.systemUTC())
                    .capture(
                            execution,
                            DataExportTemplate.LOCALIZACAO_CARGAS,
                            DATE,
                            policy.mode(),
                            null,
                            ExpansionDependencyFixtures.source(
                                            DataExportTemplate.LOCALIZACAO_CARGAS, 1, 2)
                                    .observed(
                                            observer.forInput(AnalyticScenarioObserver.Input.LOC)),
                            token,
                            capture);
            verifyAuditWindow(execution, capture);
            expected = "DEGRADED";
        } else {
            final var template =
                    switch (workload) {
                        case "coletas" -> DataExportTemplate.COLETAS;
                        case "fretes" -> DataExportTemplate.FRETES;
                        case "manifestos" -> DataExportTemplate.MANIFESTOS;
                        default -> throw new IllegalArgumentException("QUAL_TEMPORAL_WORKLOAD");
                    };
            sourceCaptures = relational(fixture, execution, template, policy, capture);
            expected = "DEGRADED";
        }
        verifyExecution(execution, capture, expected);
        final var reconciliation =
                coordinator.reconcile(
                        namespace, policy, window.partitionStart(), List.of(execution));
        final var frontier =
                expected.equals("PUBLISHED") ? window.endExclusive() : window.partitionStart();
        require(reconciliation.contiguousEnd().equals(frontier), "MATRIX_FRONTIER");
        require(
                reconciliation.degraded() == (expected.equals("DEGRADED") ? 1 : 0),
                "MATRIX_TERMINAL");
        return JsonNodeFactory.instance
                .objectNode()
                .put("workload", workload)
                .put("matrixVersion", policy.version())
                .put("policySha256", policy.fingerprint().sha256())
                .put("logicalTick", tick.toString())
                .put("partitionStart", capture.start().toString())
                .put("partitionEnd", capture.endExclusive().toString())
                .put("extractionStart", window.extractionStart().toString())
                .put("sourceStart", capture.dates().startInclusive().toString())
                .put("sourceEnd", capture.dates().endInclusive().toString())
                .put("sourceCaptures", sourceCaptures)
                .put("execution", execution.toString())
                .put("terminal", expected)
                .put("contiguousEnd", frontier.toString())
                .put("testPassed", true);
    }

    private int relational(
            final Fixture fixture,
            final UUID planned,
            final DataExportTemplate template,
            final RuntimeTemporalPolicy policy,
            final LaboratoryCaptureWindow requested)
            throws SQLException {
        final var runtime =
                new LocalRelationalRuntime(
                        session,
                        fixture.relational(),
                        fixture.relationalPolicy(),
                        fixture.logical(),
                        Clock.systemUTC());
        int count = 0;
        // V034 seals one civil source day per capture. Lookback is decomposed, never truncated.
        for (var day = requested.dates().startInclusive();
                !day.isAfter(requested.dates().endInclusive());
                day = day.plusDays(1)) {
            final boolean primary = day.equals(DATE);
            final var id =
                    primary
                            ? planned
                            : UUID.nameUUIDFromBytes(
                                    (planned + "|lookback|" + day)
                                            .getBytes(StandardCharsets.UTF_8));
            final var capture =
                    new LaboratoryCaptureWindow(
                            primary
                                    ? requested.start()
                                    : day.atStartOfDay(policy.zone()).toInstant(),
                            primary
                                    ? requested.endExclusive()
                                    : day.plusDays(1).atStartOfDay(policy.zone()).toInstant(),
                            new br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange(
                                    day, day),
                            requested.strategy());
            runtime.capture(
                    id,
                    template,
                    day,
                    policy.mode(),
                    null,
                    RelationalLaboratoryFixtures.source(template, day, 1, 1, 2, true)
                            .observed(observer.forTemplate(template)),
                    token,
                    capture);
            verifyExecution(id, capture, "DEGRADED");
            verifyAuditWindow(id, capture);
            count++;
        }
        require(count >= 1 && count <= 2, "RELATIONAL_LOOKBACK_CAPTURE_COUNT");
        return count;
    }

    private void verifyAuditWindow(final UUID id, final LaboratoryCaptureWindow expected)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT business_window_start,business_window_end,status FROM ctl.execution_audit WHERE execution_id=?")) {
            sql.setString(1, id.toString());
            sql.setQueryTimeout(15);
            try (var row = sql.executeQuery()) {
                require(
                        row.next()
                                && expected.dates()
                                        .startInclusive()
                                        .equals(row.getObject(1, LocalDate.class))
                                && expected.dates()
                                        .endInclusive()
                                        .equals(row.getObject(2, LocalDate.class))
                                && row.getString(3).equals("COMPLETED")
                                && !row.next(),
                        "ACTUAL_SOURCE_REQUEST_AUDIT");
            }
        }
    }

    private ObjectNode sourceWindows(final RuntimeTemporalPolicy base) throws Exception {
        final var f = fixture(DATE);
        final var shortWindow = planned(base, DATE, ExecutionMode.BOOTSTRAP, 0);
        final var original = UUID.randomUUID();
        quote(f, original, DATE, ExecutionMode.BOOTSTRAP, null, shortWindow, false, false, 1);
        verifySourceDates(original, DATE, DATE, 3);
        final var overlap = planned(base, DATE, ExecutionMode.BACKFILL, 86400);
        final var late = UUID.randomUUID();
        quote(f, late, DATE, ExecutionMode.BACKFILL, null, overlap, true, false, 3);
        verifySourceDates(late, DATE.minusDays(1), DATE, 9);
        final var replay = UUID.randomUUID();
        quote(f, replay, DATE, ExecutionMode.REPLAY, original, shortWindow, false, false, 1);
        verifyExecution(replay, shortWindow, "PUBLISHED");
        return JsonNodeFactory.instance
                .objectNode()
                .put("shortRoots", 1)
                .put("shortPhysical", 3)
                .put("oldExcluded", true)
                .put("lateArrivalRoots", 1)
                .put("overlapRoots", 3)
                .put("overlapPhysical", 9)
                .put("replayRoots", 1)
                .put("sourceRequestChecked", true);
    }

    private ObjectNode frontiers(final RuntimeTemporalPolicy base) throws Exception {
        final var f = fixture(DATE);
        final var policy =
                changed(
                        base,
                        ExecutionMode.INCREMENTAL,
                        0,
                        LocalTime.MIDNIGHT,
                        Duration.ofDays(3),
                        List.of());
        final var namespace =
                namespace("LOCAL_V2", "LOCAL_V2", "cotacoes", ExecutionMode.INCREMENTAL);
        final var coordinator =
                new RuntimeTemporalCoordinator(new JdbcSqlServerTemporalPlan(session));
        final var tick = DATE.plusDays(2).atStartOfDay(ZONE).toInstant().plusSeconds(60);
        final var plan =
                coordinator.persistCatchUp(UUID.randomUUID(), namespace, policy, DATE, tick);
        require(plan.windows().size() == 2, "FRONTIER_PLAN");
        final var first = plan.windows().get(0);
        final var second = plan.windows().get(1);
        final var ids = plan.windows().stream().map(w -> execution(namespace, w)).toList();
        new JdbcSqlServerControlPlane(session)
                .registerIncrementalFrontier(
                        new ExecutionPartitionKey(
                                "LOCAL_SHADOW",
                                "LOCAL_V2",
                                "LOCAL_V2",
                                "cotacoes",
                                ExecutionMode.INCREMENTAL,
                                first.partitionStart(),
                                first.endExclusive()),
                        first.partitionStart(),
                        Instant.now());
        quote(
                f,
                ids.get(1),
                DATE.plusDays(1),
                ExecutionMode.INCREMENTAL,
                null,
                LaboratoryCaptureWindow.planned(second, ZONE),
                false,
                false,
                1);
        require(frontier().equals(first.partitionStart()), "NONCONTIGUOUS_WATERMARK_ADVANCED");
        require(
                coordinator
                        .reconcile(namespace, policy, first.partitionStart(), ids)
                        .contiguousEnd()
                        .equals(first.partitionStart()),
                "NONCONTIGUOUS_RECONCILIATION");
        final var failed = UUID.randomUUID();
        final var requests = new AtomicInteger();
        final var window = LaboratoryCaptureWindow.planned(first, ZONE);
        try {
            new LocalAnalyticQuotesRuntime(
                            session,
                            Clock.systemUTC(),
                            observer.forInput(AnalyticScenarioObserver.Input.COT))
                    .capture(
                            f.run(),
                            failed,
                            DATE,
                            ExecutionMode.INCREMENTAL,
                            null,
                            1,
                            f.tariff(),
                            2,
                            QualificationTemporalInputs.quotes(
                                    DATE, window.dates(), false, true, requests),
                            token,
                            window);
            throw new IllegalStateException("QUAL_TEMPORAL_INCOMPLETE_ACCEPTED");
        } catch (final SQLException expected) {
            require(
                    cause(expected, "QUAL_TEMPORAL_SYNTHETIC_PAGE_INCOMPLETE")
                            && requests.get() == 2,
                    "INCOMPLETE_WRONG_FAILURE");
        }
        require(frontier().equals(first.partitionStart()), "INCOMPLETE_WATERMARK_ADVANCED");
        require(countExecution(failed) == 0, "INCOMPLETE_CAPTURE_RETAINED");
        quote(f, ids.get(0), DATE, ExecutionMode.INCREMENTAL, null, window, false, false, 1);
        require(frontier().equals(second.endExclusive()), "CONTIGUOUS_WATERMARK_NOT_ADVANCED");
        require(
                coordinator
                        .reconcile(namespace, policy, first.partitionStart(), ids)
                        .contiguousEnd()
                        .equals(second.endExclusive()),
                "CONTIGUOUS_RECONCILIATION");
        quote(
                f,
                UUID.randomUUID(),
                DATE,
                ExecutionMode.REPLAY,
                ids.get(0),
                window,
                false,
                false,
                1);
        quote(f, UUID.randomUUID(), DATE, ExecutionMode.BOOTSTRAP, null, window, false, false, 1);
        quote(f, UUID.randomUUID(), DATE, ExecutionMode.BACKFILL, null, window, false, false, 1);
        require(frontier().equals(second.endExclusive()), "NONINCREMENTAL_WATERMARK_CHANGED");
        return JsonNodeFactory.instance
                .objectNode()
                .put("noncontiguousUnchanged", true)
                .put("incompleteUnchanged", true)
                .put("incompleteRequests", requests.get())
                .put("gapClosureAdvancesBoth", true)
                .put("replayBootstrapBackfillUnchanged", true)
                .put("before", first.partitionStart().toString())
                .put("after", second.endExclusive().toString());
    }

    private ObjectNode civilAndAdmission(final RuntimeTemporalPolicy base) throws Exception {
        final var dstDate = LocalDate.of(2018, 11, 3);
        try {
            new RuntimeTemporalPlanner().plan(base, dstDate, Instant.parse("2018-11-05T12:00:00Z"));
            throw new IllegalStateException("QUAL_TEMPORAL_MISSING_MIDNIGHT_ACCEPTED");
        } catch (final IllegalArgumentException expected) {
            require(
                    expected.getMessage().equals("CIVIL_BOUNDARY_AMBIGUOUS_OR_MISSING"),
                    "DST_WRONG_FAILURE");
        }
        final var valid =
                changed(
                        base,
                        ExecutionMode.BACKFILL,
                        0,
                        LocalTime.of(3, 0),
                        base.deadline(),
                        List.of());
        final var tick = Instant.parse("2018-11-04T05:01:00Z");
        final var ns = namespace("LOCAL_V2", "LOCAL_V2", "cotacoes", valid.mode());
        final var coordinator =
                new RuntimeTemporalCoordinator(new JdbcSqlServerTemporalPlan(session));
        final var plan = coordinator.persistCatchUp(UUID.randomUUID(), ns, valid, dstDate, tick);
        require(plan.windows().size() == 1, "DST_PLAN");
        final var w = plan.windows().get(0);
        require(
                Duration.between(w.partitionStart(), w.endExclusive()).toHours() == 23,
                "DST_DURATION");
        final var f = fixture(dstDate);
        final var id = execution(ns, w);
        final var capture = LaboratoryCaptureWindow.planned(w, ZONE);
        quote(f, id, dstDate, valid.mode(), null, capture, false, false, 1);
        verifyExecution(id, capture, "PUBLISHED");
        require(
                coordinator
                        .reconcile(ns, valid, w.partitionStart(), List.of(id))
                        .contiguousEnd()
                        .equals(w.endExclusive()),
                "DST_FRONTIER");
        final long before = session.preparedStatements() + session.createdStatements();
        final var blackout =
                changed(
                        base,
                        ExecutionMode.BACKFILL,
                        0,
                        LocalTime.MIDNIGHT,
                        base.deadline(),
                        List.of(new RuntimeTemporalPolicy.Blackout(DATE, DATE.plusDays(1))));
        final var blocked =
                new RuntimeTemporalPlanner()
                        .plan(
                                blackout,
                                DATE,
                                DATE.plusDays(1).atStartOfDay(ZONE).toInstant().plusSeconds(60));
        require(blocked.blockedByBlackout() && blocked.windows().isEmpty(), "BLACKOUT_NOT_BLOCKED");
        final var catchup =
                new RuntimeTemporalPlanner()
                        .plan(base, DATE, DATE.plusDays(8).atStartOfDay(ZONE).toInstant());
        require(
                catchup.backlogRemaining() && catchup.windows().size() == base.maximumBacklog(),
                "CATCHUP_LIMIT");
        require(
                catchup.windows().stream()
                        .allMatch(
                                value ->
                                        value.deadlineAt()
                                                .isBefore(
                                                        DATE.plusDays(8)
                                                                .atStartOfDay(ZONE)
                                                                .toInstant())),
                "DEADLINE_NOT_EXPIRED");
        // Admission rejects these windows: no persistence, capture, or dispatcher is invoked.
        require(
                before == session.preparedStatements() + session.createdStatements(),
                "REFUSED_WINDOW_USED_JDBC");
        final var bounded =
                coordinator.persistCatchUp(
                        UUID.randomUUID(),
                        ns,
                        base,
                        DATE,
                        DATE.plusDays(8).atStartOfDay(ZONE).toInstant());
        final var plannedIds =
                bounded.windows().stream().map(value -> execution(ns, value)).toList();
        final var pending =
                coordinator.reconcile(
                        ns, base, bounded.windows().get(0).partitionStart(), plannedIds);
        require(
                bounded.windows().size() == base.maximumBacklog()
                        && bounded.backlogRemaining()
                        && pending.contiguousEnd().equals(bounded.windows().get(0).partitionStart())
                        && pending.pending().get(0).action()
                                == RuntimeTemporalCoordinator.NextAction.START_ORIGINAL,
                "PERSISTED_CATCHUP_OR_FRONTIER");
        for (final var plannedId : plannedIds) {
            require(countExecution(plannedId) == 0, "EXPIRED_CATCHUP_EXECUTED");
        }
        return JsonNodeFactory.instance
                .objectNode()
                .put("missingMidnightRefused", true)
                .put("explicitBoundary", "03:00")
                .put("actualPartitionHours", 23)
                .put("actualPublished", true)
                .put("blackoutNoCapture", true)
                .put("catchupMaximum", base.maximumBacklog())
                .put("persistedCatchupWindows", bounded.windows().size())
                .put("backlogRemaining", true)
                .put("expiredWindowsNoCapture", true);
    }

    private void quote(
            final Fixture f,
            final UUID id,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replay,
            final LaboratoryCaptureWindow window,
            final boolean late,
            final boolean incomplete,
            final long roots)
            throws Exception {
        token.throwIfCancellationRequested();
        final var receipt =
                new LocalAnalyticQuotesRuntime(
                                session,
                                Clock.systemUTC(),
                                observer.forInput(AnalyticScenarioObserver.Input.COT))
                        .capture(
                                f.run(),
                                id,
                                date,
                                mode,
                                replay,
                                1,
                                f.tariff(),
                                2,
                                QualificationTemporalInputs.quotes(
                                        date,
                                        window.dates(),
                                        late,
                                        incomplete,
                                        new AtomicInteger()),
                                token,
                                window);
        require(
                receipt.roots() == roots
                        && receipt.physical() == roots * 3
                        && receipt.blocked() == 0,
                "QUOTE_EQUATION");
        verifyExecution(id, window, "PUBLISHED");
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT COUNT_BIG(*),SUM(physical_rows),SUM(CONVERT(int,terminal_empty_page)),"
                                        + "MIN(requested_page_size),MAX(requested_page_size) "
                                        + "FROM ctl.execution_page_audit WHERE execution_id=?")) {
            sql.setString(1, id.toString());
            sql.setQueryTimeout(15);
            try (var row = sql.executeQuery()) {
                require(
                        row.next()
                                && row.getLong(1) == (roots * 3 + 1) / 2 + 1
                                && row.getLong(2) == roots * 3
                                && row.getInt(3) == 1
                                && row.getInt(4) == 2
                                && row.getInt(5) == 2
                                && !row.next(),
                        "QUOTE_PAGE_AUDIT");
            }
        }
    }

    private Fixture fixture(final LocalDate date) throws SQLException {
        final var run = UUID.randomUUID();
        final var exp = UUID.randomUUID();
        final var rel = UUID.randomUUID();
        final var start = date.minusDays(2);
        final var end = date.plusDays(4);
        final var logical = Clock.fixed(end.atStartOfDay(ZONE).toInstant(), ZoneOffset.UTC);
        final var ep =
                new ExpansionPolicy(start, end, date, 2, 100, 1000, FiscalPolicy.SYNTHETIC_CTE);
        final var rp =
                new RelationalLaboratoryPolicy(
                        start, end.minusDays(1), 1000, 10, 3, 60, 2, 0, 2, 100);
        new JdbcRasterLaboratory(session, Clock.systemUTC())
                .start(run, start, end, ZONE, 10000, 1000);
        new JdbcExpansionLaboratory(session, logical).start(exp, ep);
        new JdbcRelationalLaboratory(session, logical)
                .start(
                        rel,
                        rp,
                        new RelationalCaptureContracts(
                                RelationalSyntheticSource.release(DataExportTemplate.MANIFESTOS)
                                        .contractFingerprint()
                                        .sha256(),
                                RelationalSyntheticSource.release(DataExportTemplate.COLETAS)
                                        .contractFingerprint()
                                        .sha256(),
                                RelationalSyntheticSource.release(DataExportTemplate.FRETES)
                                        .contractFingerprint()
                                        .sha256()));
        new JdbcAnalyticDimensions(session).associate(run, exp, rel);
        final var tariff =
                new JdbcAnalyticQuoteTariffs(session).importPackaged(run, 1, start, end).release();
        return new Fixture(run, exp, rel, ep, rp, logical, tariff);
    }

    private void verifyExecution(
            final UUID execution, final LaboratoryCaptureWindow expected, final String state)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT e.current_state,p.partition_start_utc,p.partition_end_exclusive_utc "
                                        + "FROM ctl.execution_attempt e JOIN ctl.execution_partition p ON p.partition_id=e.partition_id "
                                        + "WHERE e.execution_id=?")) {
            sql.setString(1, execution.toString());
            sql.setQueryTimeout(15);
            try (var row = sql.executeQuery()) {
                require(
                        row.next()
                                && state.equals(row.getString(1))
                                && expected.start().equals(row.getTimestamp(2, utc()).toInstant())
                                && expected.endExclusive()
                                        .equals(row.getTimestamp(3, utc()).toInstant())
                                && !row.next(),
                        "EXECUTION_WINDOW_OR_STATE");
            }
        }
    }

    private void verifySourceDates(
            final UUID execution, final LocalDate start, final LocalDate end, final long count)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT MIN(freshness_business_date),MAX(freshness_business_date),COUNT_BIG(*) "
                                        + "FROM stg.cotacao_record WHERE execution_id=?")) {
            sql.setString(1, execution.toString());
            sql.setQueryTimeout(15);
            try (var row = sql.executeQuery()) {
                require(
                        row.next()
                                && start.equals(row.getObject(1, LocalDate.class))
                                && end.equals(row.getObject(2, LocalDate.class))
                                && row.getLong(3) == count,
                        "SOURCE_DATES_OR_COUNT");
            }
        }
    }

    private Instant frontier() throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT contiguous_partition_end_utc FROM ctl.incremental_publication_watermark "
                                        + "WHERE environment_name='LOCAL_SHADOW' AND source_instance='LOCAL_V2' "
                                        + "AND tenant_scope='LOCAL_V2' AND entity_name='cotacoes'")) {
            sql.setQueryTimeout(15);
            try (var row = sql.executeQuery()) {
                require(row.next(), "FRONTIER_MISSING");
                final var value = row.getTimestamp(1, utc()).toInstant();
                require(!row.next(), "FRONTIER_DUPLICATED");
                return value;
            }
        }
    }

    private long countExecution(final UUID id) throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT COUNT_BIG(*) FROM ctl.execution_attempt WHERE execution_id=?")) {
            sql.setQueryTimeout(15);
            sql.setString(1, id.toString());
            try (var row = sql.executeQuery()) {
                require(row.next(), "COUNT_MISSING");
                return row.getLong(1);
            }
        }
    }

    private static LaboratoryCaptureWindow planned(
            final RuntimeTemporalPolicy base,
            final LocalDate date,
            final ExecutionMode mode,
            final int lookback) {
        final var policy =
                changed(base, mode, lookback, LocalTime.MIDNIGHT, base.deadline(), List.of());
        return LaboratoryCaptureWindow.planned(
                new RuntimeTemporalPlanner()
                        .plan(
                                policy,
                                date,
                                date.plusDays(1).atStartOfDay(ZONE).toInstant().plusSeconds(60))
                        .windows()
                        .get(0),
                ZONE);
    }

    private static RuntimeTemporalPolicy changed(
            final RuntimeTemporalPolicy base,
            final ExecutionMode mode,
            final int lookback,
            final LocalTime boundary,
            final Duration deadline,
            final List<RuntimeTemporalPolicy.Blackout> blackouts) {
        return new RuntimeTemporalPolicy(
                "qualification-temporal-v1",
                base.zone(),
                mode,
                base.strategy(),
                base.cadence(),
                boundary,
                Duration.ofSeconds(lookback),
                base.stabilization(),
                deadline.plus(Duration.ofHours(1)),
                deadline,
                1,
                base.maximumBacklog(),
                base.maximumReconciliation(),
                base.maximumDegraded(),
                blackouts);
    }

    private ObjectNode isolated(final Proof proof) throws Exception {
        try (var connection = session.getConnection()) {
            final var savepoint = SavepointScope.open(connection);
            try (savepoint) {
                return proof.run();
            }
        }
    }

    private static String source(final String workload) {
        return workload.equals("cotacoes")
                ? "LOCAL_V2"
                : workload.equals("localizacao_cargas")
                        ? JdbcExpansionLaboratory.SOURCE
                        : JdbcRelationalLaboratory.SOURCE;
    }

    private static String tenant(final String workload) {
        return workload.equals("cotacoes")
                ? "LOCAL_V2"
                : workload.equals("localizacao_cargas")
                        ? JdbcExpansionLaboratory.TENANT
                        : JdbcRelationalLaboratory.TENANT;
    }

    private static String namespace(
            final String source,
            final String tenant,
            final String workload,
            final ExecutionMode mode)
            throws Exception {
        return HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(
                                        ("LOCAL_SHADOW|"
                                                        + source
                                                        + "|"
                                                        + tenant
                                                        + "|"
                                                        + workload
                                                        + "|"
                                                        + mode)
                                                .getBytes(StandardCharsets.UTF_8)));
    }

    private static UUID execution(
            final String namespace, final RuntimeTemporalPlanner.Window window) {
        return UUID.nameUUIDFromBytes(
                (namespace + "|" + window.partitionStart() + "|" + window.endExclusive())
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static Calendar utc() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    private static boolean cause(final Throwable failure, final String code) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++, current = current.getCause()) {
            if (code.equals(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static void require(final boolean value, final String code) {
        if (!value) {
            throw new IllegalStateException("QUAL_TEMPORAL_" + code);
        }
    }

    @FunctionalInterface
    private interface Proof {
        ObjectNode run() throws Exception;
    }

    private record Fixture(
            UUID run,
            UUID expansion,
            UUID relational,
            ExpansionPolicy expansionPolicy,
            RelationalLaboratoryPolicy relationalPolicy,
            Clock logical,
            long tariff) {}
}
