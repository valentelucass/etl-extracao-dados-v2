package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Bounded technical sequence; business state remains exclusively in the shared SQL session. */
public final class LocalArtifactSequence {
    public enum Operation {
        CAPTURE,
        RECOMPOSE
    }

    public record Step(
            String id,
            String predecessor,
            Operation operation,
            ExecutionMode mode,
            int executionRevision,
            int sourceRevision,
            int referenceRevision,
            int maximumSeconds,
            PinnedLocalJson input,
            PinnedLocalJson oracle,
            JsonNode schedule) {
        @Override
        public JsonNode schedule() {
            return schedule == null ? null : schedule.deepCopy();
        }
    }

    public record StageResult(
            String id,
            Operation operation,
            ExecutionMode mode,
            int executionRevision,
            int sourceRevision,
            int referenceRevision,
            int supplementRevision,
            String captureDate,
            String logicalClock,
            String sourceFrontier,
            QualificationScenarioVerifier.Result comparison,
            long elapsedMillis,
            long jdbcCalls,
            List<SequenceAgenda.Receipt> agenda) {}

    private final PinnedLocalJson manifest;
    private final List<Step> steps;
    private final String source;
    private final String tenant;
    private final LocalDate start;
    private final LocalDate end;
    private final int roots;
    private final int pageSize;
    private final int maximumSeconds;
    private final long admissionNanos = System.nanoTime();

    public LocalArtifactSequence(final Path path, final CancellationToken token) throws Exception {
        manifest = PinnedLocalJson.open(path, 65536);
        final var json = manifest.read();
        QualificationJson.fields(
                json,
                "version",
                "target",
                "source",
                "tenant",
                "windowStart",
                "windowEndExclusive",
                "roots",
                "pageSize",
                "maximumSeconds",
                "steps");
        final boolean recomposable =
                "local-artifact-sequence-v3".equals(QualificationJson.text(json, "version", 40));
        final boolean scheduled =
                recomposable
                        || "local-artifact-sequence-v2"
                                .equals(QualificationJson.text(json, "version", 40));
        if (!(scheduled
                        || "local-artifact-sequence-v1"
                                .equals(QualificationJson.text(json, "version", 40)))
                || !"localhost/ETL_SISTEMA_V2_SHADOW"
                        .equals(QualificationJson.text(json, "target", 64))) {
            throw new IllegalArgumentException("SEQUENCE_SCOPE");
        }
        source = DeclaredCapturePages.scope(QualificationJson.text(json, "source", 40));
        tenant = DeclaredCapturePages.scope(QualificationJson.text(json, "tenant", 40));
        start = LocalDate.parse(QualificationJson.text(json, "windowStart", 10));
        end = LocalDate.parse(QualificationJson.text(json, "windowEndExclusive", 10));
        if (!start.isBefore(end) || end.isAfter(start.plusDays(31))) {
            throw new IllegalArgumentException("SEQUENCE_WINDOW");
        }
        roots = QualificationJson.number(json, "roots", 2, 32);
        pageSize = QualificationJson.number(json, "pageSize", 1, 16);
        maximumSeconds = QualificationJson.number(json, "maximumSeconds", 1, 1800);
        QualificationJson.array(json.path("steps"), 2, 8);
        final var declared = new ArrayList<Step>();
        final var ids = new HashSet<String>();
        final var revisions = new HashSet<String>();
        Step previous = null;
        for (final var node : json.path("steps")) {
            token.throwIfCancellationRequested();
            final var fields =
                    new ArrayList<>(
                            List.of(
                                    "id",
                                    "predecessor",
                                    "mode",
                                    "executionRevision",
                                    "sourceRevision",
                                    "referenceRevision",
                                    "maximumSeconds",
                                    "input",
                                    "oracle"));
            if (scheduled) {
                fields.add("schedule");
            }
            if (recomposable) {
                fields.add("operation");
            }
            QualificationJson.fields(node, fields.toArray(String[]::new));
            final String id = identifier(node, "id");
            final String predecessor =
                    node.path("predecessor").isNull() ? null : identifier(node, "predecessor");
            final var mode = ExecutionMode.valueOf(QualificationJson.text(node, "mode", 32));
            final var step =
                    new Step(
                            id,
                            predecessor,
                            recomposable
                                    ? Operation.valueOf(
                                            QualificationJson.text(node, "operation", 16))
                                    : Operation.CAPTURE,
                            mode,
                            QualificationJson.number(node, "executionRevision", 1, 1000),
                            QualificationJson.number(node, "sourceRevision", 1, 1000),
                            QualificationJson.number(node, "referenceRevision", 1, 1000),
                            QualificationJson.number(node, "maximumSeconds", 1, 240),
                            PinnedLocalJson.reference(
                                    path.toAbsolutePath().getParent(), node.path("input"), 16384),
                            PinnedLocalJson.reference(
                                    path.toAbsolutePath().getParent(), node.path("oracle"), 16384),
                            scheduled && !node.path("schedule").isNull()
                                    ? node.path("schedule").deepCopy()
                                    : null);
            if (!ids.add(id)
                    || !revisions.add(mode + ":" + step.executionRevision())
                    || (previous == null
                            ? predecessor != null || mode != ExecutionMode.BOOTSTRAP
                            : !previous.id().equals(predecessor) || mode == ExecutionMode.BOOTSTRAP)
                    || mode == ExecutionMode.SWEEP
                    || step.maximumSeconds() > maximumSeconds) {
                throw new IllegalArgumentException("SEQUENCE_DAG_OR_MODE");
            }
            if (mode == ExecutionMode.REPLAY
                    && (previous.mode() != ExecutionMode.BOOTSTRAP
                            || previous.sourceRevision() != step.sourceRevision()
                            || previous.referenceRevision() != step.referenceRevision()
                            || !previous.input().sha256().equals(step.input().sha256()))) {
                throw new IllegalArgumentException("SEQUENCE_REPLAY_BINDING");
            }
            if (step.operation() == Operation.RECOMPOSE) {
                if (previous == null
                        || mode != ExecutionMode.BACKFILL
                        || step.schedule() != null
                        || previous.sourceRevision() != step.sourceRevision()
                        || previous.referenceRevision() != step.referenceRevision()) {
                    throw new IllegalArgumentException(
                            "SEQUENCE_RECOMPOSE_REQUIRES_UNCHANGED_CAPTURE_AND_TARIFF");
                }
                verifyRecompositionBinding(previous, step);
            } else if (scheduled && step.schedule() == null) {
                throw new IllegalArgumentException("SEQUENCE_CAPTURE_SCHEDULE_REQUIRED");
            }
            if (previous != null && previous.operation() == Operation.RECOMPOSE) {
                throw new IllegalArgumentException("SEQUENCE_RECOMPOSE_MUST_BE_FINAL");
            }
            declared.add(step);
            previous = step;
        }
        steps = List.copyOf(declared);
        verifyFiles(bounded(token));
    }

    private CancellationToken bounded(final CancellationToken token) {
        return () -> {
            token.throwIfCancellationRequested();
            if (System.nanoTime() - admissionNanos
                    >= Duration.ofSeconds(maximumSeconds).toNanos()) {
                throw new IllegalStateException("SEQUENCE_TOTAL_DEADLINE");
            }
            return false;
        };
    }

    private static void verifyRecompositionBinding(final Step previous, final Step step)
            throws Exception {
        final var before = previous.input().read();
        final var after = step.input().read();
        for (final String field :
                List.of("sources", "expansions", "raster", "relations", "references")) {
            if (!before.path(field).equals(after.path(field))) {
                throw new IllegalArgumentException("SEQUENCE_RECOMPOSE_SOURCE_CHANGED");
            }
        }
        final var directory = step.input().file().toPath().getParent();
        final var oldDirectory = previous.input().file().toPath().getParent();
        final var oldSupport =
                PinnedLocalJson.reference(oldDirectory, before.path("supplements"), 16384);
        final var newSupport =
                PinnedLocalJson.reference(directory, after.path("supplements"), 16384);
        final var oldTerms = oldSupport.read().path("batches").path("financial");
        final var newTerms = newSupport.read().path("batches").path("financial");
        if (oldTerms.size() != newTerms.size()) {
            throw new IllegalArgumentException("SEQUENCE_FINANCIAL_TERMS_REQUIRE_CAPTURE");
        }
        for (int index = 0; index < oldTerms.size(); index++) {
            final var left =
                    PinnedLocalJson.reference(
                                    oldSupport.file().toPath().getParent(),
                                    oldTerms.get(index),
                                    131072)
                            .read();
            final var right =
                    PinnedLocalJson.reference(
                                    newSupport.file().toPath().getParent(),
                                    newTerms.get(index),
                                    131072)
                            .read();
            left.forEach(
                    row ->
                            ((com.fasterxml.jackson.databind.node.ObjectNode) row)
                                    .remove("revision"));
            right.forEach(
                    row ->
                            ((com.fasterxml.jackson.databind.node.ObjectNode) row)
                                    .remove("revision"));
            if (!left.equals(right)) {
                throw new IllegalArgumentException("SEQUENCE_FINANCIAL_TERMS_REQUIRE_CAPTURE");
            }
        }
    }

    private static String identifier(final JsonNode node, final String key) {
        final String text = QualificationJson.text(node, key, 40);
        if (!text.matches("[a-z][a-z0-9-]{0,39}")) {
            throw new IllegalArgumentException("SEQUENCE_IDENTIFIER");
        }
        return text;
    }

    private LocalArtifactScenario load(final Step step, final CancellationToken token)
            throws Exception {
        step.input().verify();
        step.oracle().verify();
        final var scenario =
                new LocalArtifactScenario(
                        step.input().file().toPath(), step.oracle().file().toPath(), token);
        // Constructor reads and subsequent reads must all still match the admitted envelope.
        step.input().verify();
        step.oracle().verify();
        final var input = scenario.integralInputs();
        if (!source.equals(input.scope().source())
                || !tenant.equals(input.scope().tenant())
                || !start.equals(input.start())
                || !end.equals(input.end())
                || roots != input.roots()
                || pageSize != input.pageSize()
                || step.sourceRevision() != input.revision()
                || step.referenceRevision() != input.references().revision()) {
            throw new IllegalArgumentException("SEQUENCE_INPUT_BINDING");
        }
        if (step.schedule() != null) {
            new SequenceAgenda(step.schedule(), step.mode(), input);
        }
        return scenario;
    }

    public void verifyFiles(final CancellationToken token) throws Exception {
        manifest.verify();
        final var checked = new HashSet<String>();
        for (final var step : steps) {
            token.throwIfCancellationRequested();
            if (checked.add(
                    step.input().file().toPath().toString()
                            + "|"
                            + step.input().sha256()
                            + "|"
                            + step.oracle().file().toPath().toString()
                            + "|"
                            + step.oracle().sha256())) {
                load(step, token).verifyFiles(token);
            }
        }
    }

    public List<StageResult> execute(
            final ColetaTemporalLaboratorySession session,
            final UUID runId,
            final AnalyticScenarioObserver observer,
            final CancellationToken cancellation,
            final Consumer<StageResult> completed)
            throws Exception {
        final var deadline =
                ExecutionDeadlines.start(
                        Duration.ofSeconds(maximumSeconds),
                        MonotonicTicker.systemTicker(),
                        bounded(cancellation));
        final CancellationToken total =
                () -> {
                    deadline.checkpointCycle();
                    return false;
                };
        verifyFiles(total);
        new QualificationPhysicalMetadata().verify(session);
        final var cycles = new ArrayList<AnalyticScenarioRuntime.Cycle>(steps.size());
        final var results = new ArrayList<StageResult>(steps.size());
        final var recompositions = new ArrayList<SequenceRecomposition.Receipt>();
        AnalyticScenarioRuntime.Run run = null;
        final Instant started = Instant.now();
        for (final var step : steps) {
            final var stepDeadline =
                    ExecutionDeadlines.start(
                            Duration.ofSeconds(step.maximumSeconds()),
                            MonotonicTicker.systemTicker(),
                            total);
            final CancellationToken token =
                    () -> {
                        stepDeadline.checkpointCycle();
                        return false;
                    };
            final long nanos = System.nanoTime();
            final long calls = session.preparedStatements() + session.createdStatements();
            manifest.verify();
            final var scenario = load(step, token);
            final var input = scenario.integralInputs();
            final var agenda =
                    step.schedule() == null
                            ? null
                            : new SequenceAgenda(step.schedule(), step.mode(), input);
            final var runtime =
                    new AnalyticScenarioRuntime(session, Clock.systemUTC(), observer, input);
            if (run == null) {
                run = runtime.start(runId, roots, pageSize, AnalyticScenarioRuntime.Fault.NONE);
            }
            final AnalyticScenarioRuntime.Cycle cycle;
            if (step.operation() == Operation.RECOMPOSE) {
                verifyRecompositionBinding(steps.get(results.size() - 1), step);
                cycle = cycles.get(cycles.size() - 1);
                recompositions.add(scenario.recompose(session, run, cycle, token));
            } else {
                if (!cycles.isEmpty()
                        && step.referenceRevision()
                                != steps.get(results.size() - 1).referenceRevision()) {
                    final long tariff =
                            input.references()
                                    .importInto(
                                            session,
                                            run.id(),
                                            run.expansion(),
                                            input.clock(),
                                            token);
                    run =
                            new AnalyticScenarioRuntime.Run(
                                    run.id(),
                                    run.expansion(),
                                    run.relational(),
                                    run.roots(),
                                    run.pageSize(),
                                    tariff,
                                    run.fault(),
                                    run.variant());
                }
                cycle =
                        runtime.capture(
                                run,
                                step.mode(),
                                step.executionRevision(),
                                false,
                                cycles.isEmpty() ? null : cycles.get(cycles.size() - 1),
                                token,
                                agenda,
                                step.id());
                cycles.add(cycle);
            }
            final var verified =
                    scenario.compareRecomposition(
                            session, run, cycles, started, token, recompositions);
            final var comparison =
                    new QualificationScenarioVerifier.Result(
                            verified.scopes(),
                            verified.outputs(),
                            verified.selected(),
                            verified.retainedTechnicalRecords(),
                            verified.selected().state() == QualificationGate.State.PASS_LOCAL
                                    ? verifyPreviews(
                                            scenario.previewSequence(session, run, observer, token))
                                    : List.of());
            token.throwIfCancellationRequested();
            final var agendaReceipts =
                    agenda == null
                            ? List.<SequenceAgenda.Receipt>of()
                            : agenda.reconcile(session, run.id(), step.id());
            final String sourceFrontier =
                    new br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticScenario(
                                    session)
                            .status(cycle.intent().cycle())
                            .nextDate()
                            .toString();
            final var result =
                    new StageResult(
                            step.id(),
                            step.operation(),
                            step.mode(),
                            step.executionRevision(),
                            cycle.sourceRevision(),
                            step.referenceRevision(),
                            input.supplementRevision(),
                            input.captureDate().toString(),
                            input.clock().instant().toString(),
                            sourceFrontier,
                            comparison,
                            (System.nanoTime() - nanos) / 1_000_000,
                            session.preparedStatements() + session.createdStatements() - calls,
                            agendaReceipts);
            results.add(result);
            completed.accept(result);
            if (comparison.selected().state() != QualificationGate.State.PASS_LOCAL) {
                break;
            }
        }
        return List.copyOf(results);
    }

    public List<Step> steps() {
        return steps;
    }

    void verifyReport(final JsonNode report) throws Exception {
        QualificationJson.array(report.path("stages"), steps.size(), steps.size());
        LocalDate frontier = start;
        for (int index = 0; index < steps.size(); index++) {
            final var expected = steps.get(index);
            final var stage = report.path("stages").get(index);
            if (!expected.id().equals(stage.path("id").asText())
                    || !expected.operation().name().equals(stage.path("operation").asText())
                    || !expected.mode().name().equals(stage.path("mode").asText())
                    || expected.executionRevision()
                            != QualificationJson.number(stage, "executionRevision", 1, 1000)
                    || expected.sourceRevision()
                            != QualificationJson.number(stage, "sourceRevision", 1, 1000)
                    || expected.referenceRevision()
                            != QualificationJson.number(stage, "referenceRevision", 1, 1000)
                    || !expected.input()
                            .sha256()
                            .equals(QualificationJson.digest(stage, "inputSha256"))
                    || !expected.oracle()
                            .sha256()
                            .equals(QualificationJson.digest(stage, "oracleSha256"))
                    || !"PASS_LOCAL".equals(stage.path("selectedState").asText())
                    || stage.path("elapsedMillis").asLong(-1) < 0
                    || stage.path("elapsedMillis").asLong() > expected.maximumSeconds() * 1000L) {
                throw new IllegalArgumentException("QUAL_SEQUENCE_STAGE_BINDING");
            }
            br.com.esl.etl.v2.plataforma.qualificacao.QualificationProcessEvidence.coverage(stage);
            QualificationJson.array(stage.path("sweepPreview"), 33, 33);
            final var sweep = sweepIds();
            for (final var row : stage.path("sweepPreview")) {
                if (!sweep.remove(row.path("responsibility").path("id").asText())
                        || !row.path("assessment").path("disposition").asText().equals("BLOCKED")
                        || !row.path("assessment")
                                .path("reason")
                                .asText()
                                .equals("APPLICABILITY_NOT_ENABLED")) {
                    throw new IllegalArgumentException("QUAL_SEQUENCE_SWEEP_ORACLE");
                }
            }
            final var input = expected.input().read();
            final var date =
                    LocalDate.parse(
                            input.has("captureDate")
                                    ? input.path("captureDate").asText()
                                    : input.path("windowStart").asText());
            if (!date.toString().equals(stage.path("captureDate").asText())
                    || !input.path("logicalClock")
                            .asText()
                            .equals(stage.path("logicalClock").asText())
                    || (input.has("supplementRevision")
                                    ? input.path("supplementRevision").asInt()
                                    : input.path("revision").asInt())
                            != stage.path("supplementRevision").asInt(-1)) {
                throw new IllegalArgumentException("QUAL_SEQUENCE_STAGE_INPUT_METADATA");
            }
            frontier =
                    verifyFrontier(
                            expected.mode(),
                            date,
                            frontier,
                            LocalDate.parse(QualificationJson.text(stage, "sourceFrontier", 10)));
            final var agenda = stage.path("agenda");
            QualificationJson.array(
                    agenda,
                    expected.schedule() == null ? 0 : 4,
                    expected.schedule() == null ? 0 : 4);
            final var families = new HashSet<String>();
            for (final var receipt : agenda) {
                final String family = QualificationJson.text(receipt, "family", 4);
                final var begin = date.atStartOfDay(AnalyticScenarioRuntime.ZONE).toInstant();
                final var finish =
                        date.plusDays(1).atStartOfDay(AnalyticScenarioRuntime.ZONE).toInstant();
                if (!List.of("COL", "FRE", "MAN", "COT").contains(family)
                        || !families.add(family)
                        || !begin.toString().equals(receipt.path("start").asText())
                        || !finish.toString().equals(receipt.path("endExclusive").asText())
                        || !(family.equals("COT") ? finish : begin)
                                .toString()
                                .equals(receipt.path("contiguousEnd").asText())
                        || receipt.path("degraded").asInt(-1) != (family.equals("COT") ? 0 : 1)) {
                    throw new IllegalArgumentException("QUAL_SEQUENCE_AGENDA_FRONTIER");
                }
            }
        }
    }

    static LocalDate verifyFrontier(
            final ExecutionMode mode,
            final LocalDate date,
            final LocalDate prior,
            final LocalDate observed) {
        final var expected =
                mode == ExecutionMode.INCREMENTAL && date.equals(prior) ? date.plusDays(1) : prior;
        if (!expected.equals(observed)) {
            throw new IllegalArgumentException("QUAL_SEQUENCE_SOURCE_FRONTIER");
        }
        return expected;
    }

    private static java.util.Set<String> sweepIds() {
        final var ids = new HashSet<String>();
        new SweepResponsibilityPlanner()
                .bindings("0".repeat(64), "0".repeat(64))
                .forEach(row -> ids.add(row.id()));
        return ids;
    }

    static List<SweepResponsibilityPlanner.Preview> verifyPreviews(
            final List<SweepResponsibilityPlanner.Preview> rows) {
        final var expected = sweepIds();
        if (rows.size() != 33) {
            throw new IllegalArgumentException("SEQUENCE_SWEEP_ORACLE_COUNT");
        }
        for (final var row : rows) {
            if (!expected.remove(row.responsibility().id())
                    || !row.assessment().disposition().name().equals("BLOCKED")
                    || !row.assessment().reason().name().equals("APPLICABILITY_NOT_ENABLED")) {
                throw new IllegalArgumentException("SEQUENCE_SWEEP_ORACLE_DIVERGENCE");
            }
        }
        return rows;
    }

    public int maximumSeconds() {
        return maximumSeconds;
    }

    public int roots() {
        return roots;
    }

    public int pageSize() {
        return pageSize;
    }

    public LocalDate start() {
        return start;
    }

    public LocalDate endExclusive() {
        return end;
    }
}
