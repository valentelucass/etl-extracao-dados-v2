package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationLineageEvidence;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationLocationOracle;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualifiedPackage;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A case rebuilds its disposable fixture and consumes the typed comparison, DAG and SQL planner.
 */
public final class QualificationCaseExecutor {
    public record Result(QualificationGate.State state, String reason, ObjectNode report) {}

    private final QualificationOracles oracles;
    private final QualificationComparator.Binding binding;
    private final QualificationConfiguration configuration;
    private final QualifiedPackage payload;

    public QualificationCaseExecutor(
            final QualifiedPackage payload, final QualificationConfiguration configuration)
            throws Exception {
        this.configuration = configuration;
        this.payload = payload;
        oracles =
                QualificationOracles.read(
                        payload.member("oracles/outputs.synthetic.json", "ORACLE"));
        binding =
                new QualificationComparator.Binding(
                        payload.revision(),
                        payload.members().get("fixtures/fixture-index.json").sha256(),
                        payload.members().get("oracles/outputs.synthetic.json").sha256(),
                        "INDEPENDENT_SYNTHETIC_RULES_V1");
    }

    public Result execute(
            final ColetaTemporalLaboratorySession session,
            final QualificationCampaign campaign,
            final QualificationCampaign.Case item,
            final UUID nonce,
            final Instant started,
            final Consumer<QualificationCampaign.Barrier> barrier,
            final CancellationToken token)
            throws Exception {
        return new SqlExecution().execute(session, campaign, item, nonce, started, barrier, token);
    }

    static void comparison(
            final ObjectNode report, final QualificationScenarioVerifier.Result comparison) {
        report.put("selectedState", comparison.selected().state().name());
        final var scopes = report.putArray("scopes");
        comparison.scopes().values().stream()
                .sorted(java.util.Comparator.comparing(QualificationGate::scope))
                .forEach(
                        gate ->
                                scopes.addObject()
                                        .put("scope", gate.scope())
                                        .put("state", gate.state().name())
                                        .put("reason", gate.reason())
                                        .put("layer", gate.layer()));
        final var outputs = report.putArray("outputs");
        comparison.outputs().forEach(result -> outputs.add(output(result)));
    }

    private static ObjectNode output(final QualificationComparator.Result result) {
        final var node =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("contract", result.contract())
                        .put("expectedRows", result.expectedRows())
                        .put("observedRows", result.observedRows())
                        .put("differences", result.differences())
                        .put("gate", result.gate().name());
        final var sample = node.putArray("sample");
        result.sample()
                .forEach(
                        diff ->
                                sample.addObject()
                                        .put("row", diff.row())
                                        .put("ordinal", diff.ordinal())
                                        .put("kind", diff.kind().name()));
        return node;
    }

    private final class SqlExecution {
        public Result execute(
                final ColetaTemporalLaboratorySession session,
                final QualificationCampaign campaign,
                final QualificationCampaign.Case item,
                final UUID nonce,
                final Instant started,
                final Consumer<QualificationCampaign.Barrier> barrier,
                final CancellationToken token)
                throws Exception {
            final var metrics =
                    new QualificationMetrics(
                            configuration,
                            () -> barrier.accept(QualificationCampaign.Barrier.DURING_CAPTURE),
                            QualificationArtifactCase.caseSeconds(item, configuration));
            if (item.action() == QualificationCampaign.Action.SEQUENCE) {
                return sequence(session, campaign, item, nonce, barrier, token, metrics);
            }
            if (item.action() == QualificationCampaign.Action.ARTIFACT) {
                return artifact(session, campaign, item, nonce, barrier, token, metrics);
            }
            final var concurrency =
                    item.action() == QualificationCampaign.Action.CONCURRENCY
                            ? QualificationConcurrency.execute(
                                    configuration, token, session.statementBudget(), metrics)
                            : null;
            final var temporal =
                    item.action() == QualificationCampaign.Action.TEMPORAL
                            ? new QualificationTemporalMatrix(session, metrics, token).execute()
                            : null;
            final var runtime =
                    new AnalyticScenarioRuntime(
                            session,
                            Clock.systemUTC(),
                            metrics,
                            item.action() == QualificationCampaign.Action.VARIANTS
                                    ? AnalyticScenarioVariant.VALUES_AND_NULLS
                                    : AnalyticScenarioVariant.BASELINE);
            final var run =
                    runtime.start(
                            nonce,
                            campaign.roots(),
                            campaign.pageSize(),
                            AnalyticScenarioRuntime.Fault.valueOf(item.fault().name()));
            final var cycles = new ArrayList<AnalyticScenarioRuntime.Cycle>();
            final var initial =
                    runtime.capture(run, ExecutionMode.BOOTSTRAP, 1, false, null, token);
            cycles.add(initial);
            if (item.mode() != ExecutionMode.BOOTSTRAP) {
                cycles.add(
                        runtime.capture(
                                run,
                                item.mode(),
                                2,
                                item.action() == QualificationCampaign.Action.RECOMPOSE,
                                initial,
                                token));
            }
            barrier.accept(QualificationCampaign.Barrier.AFTER_PREPARATION);
            final var report = JsonNodeFactory.instance.objectNode();
            report.put("variant", run.variant().name());
            if (temporal != null) {
                report.set("temporal", temporal);
            }
            if (concurrency != null) {
                report.putObject("concurrency")
                        .put("ownerSpid", concurrency.ownerSpid())
                        .put("contenderSpid", concurrency.contenderSpid())
                        .put("timeoutCode", concurrency.timeoutCode())
                        .put("cancelledStatements", concurrency.cancelledStatements())
                        .put("cancellationMillis", concurrency.cancellationMillis())
                        .put("consumed", concurrency.consumed())
                        .put("jdbcCalls", concurrency.jdbcCalls())
                        .put("rollback", concurrency.rollback());
            }
            final var verifier = new QualificationScenarioVerifier(oracles, binding, binding);
            final var comparison =
                    verifier.verify(session, run, cycles, started, item.outputs(), token);
            report.put("selectedState", comparison.selected().state().name());
            final var scopes = report.putArray("scopes");
            comparison.scopes().values().stream()
                    .sorted(java.util.Comparator.comparing(QualificationGate::scope))
                    .forEach(
                            gate ->
                                    scopes.addObject()
                                            .put("scope", gate.scope())
                                            .put("state", gate.state().name())
                                            .put("reason", gate.reason())
                                            .put("layer", gate.layer()));
            final var outputs = report.putArray("outputs");
            comparison.outputs().forEach(result -> outputs.add(output(result)));
            QualificationGate.State state = comparison.selected().state();
            String reason = comparison.selected().reason();
            if (item.action() == QualificationCampaign.Action.ABSENCE) {
                final var absence =
                        absence(
                                session,
                                run,
                                started,
                                token,
                                metrics.forInput(AnalyticScenarioObserver.Input.COL));
                report.set("absence", absence);
                if (!absence.path("passed").booleanValue()) {
                    state = QualificationGate.State.FAILED;
                    reason = "ABSENCE_ORACLE_DIVERGENCE";
                }
            }
            final var windows =
                    new QualificationWindowExecutor(session, Clock.systemUTC())
                            .execute(run, item, token);
            final var applied = report.putArray("windows");
            windows.applied()
                    .forEach(
                            row ->
                                    applied.addObject()
                                            .put("fact", row.fact())
                                            .put("receipt", row.receipt().toString())
                                            .put("start", row.start().toString())
                                            .put("endExclusive", row.endExclusive().toString())
                                            .put("candidates", row.candidates())
                                            .put("inserts", row.inserts())
                                            .put("updates", row.updates())
                                            .put("noops", row.noops())
                                            .put("ready", row.ready())
                                            .put("blocked", row.blocked()));
            report.put("sourceFrontierBefore", String.valueOf(windows.sourceBefore()));
            report.put("sourceFrontierAfter", String.valueOf(windows.sourceAfter()));
            if (state != QualificationGate.State.FAILED
                    && windows.gate().state() != QualificationGate.State.PASS_LOCAL) {
                state = windows.gate().state();
                reason = windows.gate().reason();
            }
            final var snapshot = metrics.snapshot();
            report.putObject("metrics")
                    .put("pages", snapshot.pages())
                    .put("bytes", snapshot.bytes())
                    .put("records", snapshot.records())
                    .put("batches", snapshot.batches())
                    .put("largestBatch", snapshot.largestBatch())
                    .put("inFlight", snapshot.inFlight())
                    .put("heapBefore", snapshot.heapBefore())
                    .put("heapAfter", snapshot.heapAfter())
                    .put("elapsedMillis", snapshot.elapsedMillis())
                    .put("jdbcPrepared", session.preparedStatements())
                    .put("jdbcCreated", session.createdStatements())
                    .put("retainedTechnicalRecords", comparison.retainedTechnicalRecords());
            final var inputs = report.withObject("/metrics").putArray("inputs");
            snapshot.inputs()
                    .forEach(
                            input ->
                                    inputs.addObject()
                                            .put("input", input.input().name())
                                            .put("pages", input.pages())
                                            .put("bytes", input.bytes())
                                            .put("records", input.records())
                                            .put("batches", input.batches())
                                            .put("largestBatch", input.largestBatch())
                                            .put("largestPageBytes", input.largestPageBytes())
                                            .put("inFlight", input.inFlight())
                                            .put("retainedPageBytes", input.retainedPageBytes())
                                            .put("elapsedMillis", input.elapsedMillis())
                                            .put("rowLimit", input.rowLimit())
                                            .put("byteLimit", input.byteLimit())
                                            .put("pageLimit", input.pageLimit()));
            if (session.preparedStatements()
                                    + session.createdStatements()
                                    + (concurrency == null ? 0 : concurrency.jdbcCalls())
                            > configuration.maximumJdbcCalls()
                    || comparison.outputs().stream()
                            .anyMatch(value -> value.observedRows() > configuration.maximumRows())
                    || snapshot.inFlight() != 0
                    || snapshot.inputs().stream()
                            .anyMatch(value -> value.retainedPageBytes() != 0)) {
                throw new IllegalStateException("QUAL_EXECUTION_LIMIT");
            }
            report.put("selectedState", state.name());
            return new Result(state, reason, report);
        }

        private Result sequence(
                final ColetaTemporalLaboratorySession session,
                final QualificationCampaign campaign,
                final QualificationCampaign.Case item,
                final UUID nonce,
                final Consumer<QualificationCampaign.Barrier> barrier,
                final CancellationToken token,
                final QualificationMetrics metrics)
                throws Exception {
            final var sequence =
                    QualificationArtifactCase.loadSequence(payload, campaign, item, token);
            final var report =
                    JsonNodeFactory.instance.objectNode().put("variant", "LOCAL_ARTIFACT_SEQUENCE");
            final var stages = report.putArray("stages");
            final var measurements = new SequenceMeasurements(metrics);
            final List<LocalArtifactSequence.StageResult> results;
            try {
                results =
                        sequence.execute(
                                session,
                                nonce,
                                measurements,
                                token,
                                result -> {
                                    measurements.sample("STAGE");
                                    final var declared = sequence.steps().get(stages.size());
                                    final var stage =
                                            stages.addObject()
                                                    .put("id", result.id())
                                                    .put("operation", result.operation().name())
                                                    .put("physicalColumns", 971)
                                                    .put("inputSha256", declared.input().sha256())
                                                    .put("oracleSha256", declared.oracle().sha256())
                                                    .put("mode", result.mode().name())
                                                    .put(
                                                            "executionRevision",
                                                            result.executionRevision())
                                                    .put("sourceRevision", result.sourceRevision())
                                                    .put(
                                                            "supplementRevision",
                                                            result.supplementRevision())
                                                    .put("captureDate", result.captureDate())
                                                    .put("logicalClock", result.logicalClock())
                                                    .put("sourceFrontier", result.sourceFrontier())
                                                    .put(
                                                            "referenceRevision",
                                                            result.referenceRevision())
                                                    .put("elapsedMillis", result.elapsedMillis())
                                                    .put("jdbcCalls", result.jdbcCalls());
                                    comparison(stage, result.comparison());
                                    stage.set(
                                            "sweepPreview",
                                            new com.fasterxml.jackson.databind.ObjectMapper()
                                                    .valueToTree(
                                                            result.comparison().sweepPreview()));
                                    final var agenda = stage.putArray("agenda");
                                    for (final var receipt : result.agenda()) {
                                        agenda.addObject()
                                                .put("family", receipt.family())
                                                .put("start", receipt.start().toString())
                                                .put(
                                                        "endExclusive",
                                                        receipt.endExclusive().toString())
                                                .put(
                                                        "extractionStart",
                                                        receipt.extractionStart().toString())
                                                .put(
                                                        "contiguousEnd",
                                                        receipt.contiguousEnd().toString())
                                                .put("degraded", receipt.degraded())
                                                .put("backlog", receipt.backlog());
                                    }
                                });
                barrier.accept(QualificationCampaign.Barrier.AFTER_PREPARATION);
            } catch (final Exception failure) {
                report.put("completedStages", stages.size())
                        .put("declaredStages", sequence.steps().size())
                        .put("failureClass", failure.getClass().getSimpleName());
                final String code = failure.getMessage();
                if (code != null && code.matches("[A-Z][A-Z0-9_]{1,80}")) {
                    report.put("failureCode", code);
                }
                throw new SequenceExecutionFailure(failure, report);
            }
            final var last = results.get(results.size() - 1).comparison();
            comparison(report, last);
            final var snapshot = metrics.snapshot();
            report.set(
                    "sequenceMeasurements",
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .valueToTree(measurements.snapshot()));
            report.set(
                    "metrics",
                    new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(snapshot));
            report.withObject("/metrics")
                    .put("jdbcPrepared", session.preparedStatements())
                    .put("jdbcCreated", session.createdStatements());
            if (snapshot.inFlight() != 0
                    || snapshot.inputs().stream().anyMatch(value -> value.retainedPageBytes() != 0)
                    || session.preparedStatements() + session.createdStatements()
                            > configuration.maximumJdbcCalls()) {
                throw new IllegalStateException("QUAL_SEQUENCE_LIMIT");
            }
            if (results.size() != sequence.steps().size()
                    && last.selected().state() == QualificationGate.State.PASS_LOCAL) {
                throw new IllegalStateException("QUAL_SEQUENCE_INCOMPLETE");
            }
            return new Result(last.selected().state(), last.selected().reason(), report);
        }

        private Result artifact(
                final ColetaTemporalLaboratorySession session,
                final QualificationCampaign campaign,
                final QualificationCampaign.Case item,
                final UUID nonce,
                final Consumer<QualificationCampaign.Barrier> barrier,
                final CancellationToken token,
                final QualificationMetrics metrics)
                throws Exception {
            final var input = QualificationArtifactCase.load(payload, campaign, item, token);
            final var comparison =
                    input.execute(
                            session,
                            token,
                            metrics,
                            nonce,
                            () -> barrier.accept(QualificationCampaign.Barrier.AFTER_PREPARATION));
            final var report =
                    JsonNodeFactory.instance
                            .objectNode()
                            .put("variant", "LOCAL_ARTIFACT_ROLLBACK")
                            .put("selectedState", comparison.selected().state().name());
            final var scopes = report.putArray("scopes");
            comparison.scopes().values().stream()
                    .sorted(java.util.Comparator.comparing(QualificationGate::scope))
                    .forEach(
                            gate ->
                                    scopes.addObject()
                                            .put("scope", gate.scope())
                                            .put("state", gate.state().name())
                                            .put("reason", gate.reason())
                                            .put("layer", gate.layer()));
            final var outputs = report.putArray("outputs");
            comparison.outputs().forEach(result -> outputs.add(output(result)));
            final var snapshot = metrics.snapshot();
            report.set(
                    "metrics",
                    new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(snapshot));
            report.withObject("/metrics")
                    .put("jdbcPrepared", session.preparedStatements())
                    .put("jdbcCreated", session.createdStatements())
                    .put("retainedTechnicalRecords", comparison.retainedTechnicalRecords());
            if (session.preparedStatements() + session.createdStatements()
                            > configuration.maximumJdbcCalls()
                    || comparison.outputs().stream()
                            .anyMatch(value -> value.observedRows() > configuration.maximumRows())
                    || snapshot.inFlight() != 0
                    || snapshot.inputs().stream()
                            .anyMatch(value -> value.retainedPageBytes() != 0)) {
                throw new IllegalStateException("QUAL_EXECUTION_LIMIT");
            }
            return new Result(
                    comparison.selected().state(), comparison.selected().reason(), report);
        }

        private ObjectNode absence(
                final ColetaTemporalLaboratorySession session,
                final AnalyticScenarioRuntime.Run run,
                final Instant started,
                final CancellationToken token,
                final AnalyticScenarioObserver observer)
                throws Exception {
            final var report = JsonNodeFactory.instance.objectNode();
            final var results = report.putArray("stages");
            final var sweep =
                    new LocalAnalyticCollectionSweep(
                            session,
                            run.id(),
                            run.relational(),
                            AnalyticScenarioRuntime.policy(run.pageSize()),
                            AnalyticScenarioRuntime.LOGICAL_CLOCK,
                            Clock.systemUTC(),
                            observer);
            final var absent =
                    new SyntheticCollectionSnapshot(
                            run.id(), AnalyticScenarioRuntime.START, run.roots(), true);
            final UUID confirmation = UUID.randomUUID();
            boolean passed = true;
            for (final int stage : List.of(1, 2, 0)) {
                final var capture =
                        sweep.observe(
                                stage == 0
                                        ? new SyntheticCollectionSnapshot(
                                                run.id(),
                                                AnalyticScenarioRuntime.START,
                                                run.roots(),
                                                false)
                                        : absent,
                                stage == 2 ? confirmation : UUID.randomUUID(),
                                token);
                passed &=
                        switch (stage) {
                            case 1 -> capture.result().candidates() == 1;
                            case 2 -> capture.result().confirmations() == 1;
                            default -> capture.result().reactivated() == 1;
                        };
                final var evidence =
                        new AbsenceEvidence(
                                new QualificationLineageEvidence(
                                        session, run.id(), run.expansion(), run.roots(), 1, false),
                                new QualificationLocationOracle());
                final var context =
                        new QualificationOracles.Context(
                                run.id(),
                                run.roots(),
                                1,
                                false,
                                stage,
                                stage == 2 ? confirmation : null,
                                started,
                                Instant.now(),
                                evidence);
                for (final var contract :
                        List.of(AnalyticSqlContract.SQL_03, AnalyticSqlContract.SQL_04)) {
                    final var comparator =
                            new QualificationComparator(
                                    contract,
                                    binding,
                                    binding,
                                    AnalyticSqlCatalog.columns(contract),
                                    oracles.expected(contract, context));
                    new JdbcAnalyticQueries(session)
                            .read(
                                    run.id(),
                                    contract,
                                    2,
                                    configuration.maximumRows(),
                                    token,
                                    comparator::metadata,
                                    comparator);
                    final var result = comparator.finish();
                    passed &= result.gate() == QualificationGate.State.PASS_LOCAL;
                    results.add(output(result).put("stage", stage));
                }
            }
            return report.put("passed", passed);
        }

        private record AbsenceEvidence(
                QualificationLineageEvidence source, QualificationLocationOracle location)
                implements QualificationOracles.Evidence {
            @Override
            public List<AnalyticSqlValue> monitor(final long row) {
                throw new IllegalStateException("QUAL_ABSENCE_MONITOR_SCOPE");
            }

            @Override
            public int monitorCount() {
                return 0;
            }

            @Override
            public boolean lineage(
                    final String entity, final int root, final int component, final JsonNode json) {
                return source.compare(entity, root, component, json);
            }

            @Override
            public String locationHash(final int root) {
                return location.hash(root);
            }
        }
    }
}
