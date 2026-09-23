package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalCoordinator;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.controle.JdbcSqlServerTemporalPlan;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Five explicit policies feed the existing capture windows; snapshots retain their own rules. */
public final class SequenceAgenda {
    private static final List<String> FAMILIES = List.of("COL", "FRE", "MAN", "COT", "LOC");

    public record Plan(
            RuntimeTemporalPolicy policy,
            RuntimeTemporalPlanner.Window window,
            Instant tick,
            boolean backlog) {}

    public record Receipt(
            String family,
            Instant start,
            Instant endExclusive,
            Instant extractionStart,
            Instant contiguousEnd,
            int degraded,
            boolean backlog) {}

    private final Map<String, Plan> plans;

    SequenceAgenda(
            final JsonNode json, final ExecutionMode mode, final DeclaredIntegralInputs input) {
        QualificationJson.fields(json, FAMILIES.toArray(String[]::new));
        final var values = new LinkedHashMap<String, Plan>();
        for (final var family : FAMILIES) {
            final var node = json.path(family);
            QualificationJson.fields(
                    node,
                    "tick",
                    "lookbackSeconds",
                    "deadlineSeconds",
                    "maximumCatchUp",
                    "blackouts");
            final var tick = Instant.parse(QualificationJson.text(node, "tick", 40));
            final int lookback = QualificationJson.number(node, "lookbackSeconds", 0, 259200);
            final int deadline = QualificationJson.number(node, "deadlineSeconds", 1, 2678400);
            final int catchUp = QualificationJson.number(node, "maximumCatchUp", 1, 3);
            QualificationJson.array(node.path("blackouts"), 0, 8);
            final var blackouts = new ArrayList<RuntimeTemporalPolicy.Blackout>();
            for (final var blackout : node.path("blackouts")) {
                if (!blackout.isTextual()) {
                    throw new IllegalArgumentException("SEQUENCE_BLACKOUT_TYPE");
                }
                final var date = LocalDate.parse(blackout.textValue());
                blackouts.add(new RuntimeTemporalPolicy.Blackout(date, date.plusDays(1)));
            }
            final var policy =
                    new RuntimeTemporalPolicy(
                            "sequence-" + family.toLowerCase(java.util.Locale.ROOT) + "-v1",
                            AnalyticScenarioRuntime.ZONE,
                            mode,
                            RuntimeWindowStrategy.INTERVAL,
                            RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                            LocalTime.MIDNIGHT,
                            Duration.ofSeconds(lookback),
                            Duration.ZERO,
                            Duration.ofSeconds(deadline),
                            Duration.ofSeconds(deadline),
                            1,
                            catchUp,
                            64,
                            64,
                            blackouts);
            final var planned =
                    new RuntimeTemporalPlanner().plan(policy, input.captureDate(), tick);
            if (planned.windows().isEmpty()) {
                throw new IllegalArgumentException(
                        planned.blockedByBlackout() ? "SEQUENCE_BLACKOUT" : "SEQUENCE_NOT_DUE");
            }
            if (planned.windows().size() != 1) {
                throw new IllegalArgumentException("SEQUENCE_CATCHUP_REQUIRES_DECLARED_STAGES");
            }
            final var window = planned.windows().get(0);
            if (tick.isAfter(window.deadlineAt())) {
                throw new IllegalArgumentException("SEQUENCE_LOGICAL_DEADLINE");
            }
            final var capture = LaboratoryCaptureWindow.planned(window, policy.zone());
            if (capture.dates().startInclusive().isBefore(input.start())
                    || !capture.dates().endInclusive().isBefore(input.end())) {
                throw new IllegalArgumentException("SEQUENCE_LOOKBACK_OUTSIDE_RUN");
            }
            if (List.of("COL", "FRE", "MAN").contains(family)
                    && !capture.dates().startInclusive().equals(input.captureDate())) {
                throw new IllegalArgumentException("SEQUENCE_LOOKBACK_DAY_INPUT_MISSING");
            }
            values.put(family, new Plan(policy, window, tick, planned.backlogRemaining()));
        }
        plans = Map.copyOf(values);
    }

    LaboratoryCaptureWindow window(final String family) {
        final var plan = plans.get(family);
        if (plan == null) {
            throw new IllegalArgumentException("SEQUENCE_AGENDA_FAMILY");
        }
        return LaboratoryCaptureWindow.planned(plan.window(), plan.policy().zone());
    }

    private static String namespace(final UUID run, final String stage, final String family) {
        return QualificationJson.sha256(
                ("sequence-plan-v1|" + run + "|" + stage + "|" + family)
                        .getBytes(StandardCharsets.UTF_8));
    }

    UUID execution(final UUID run, final String stage, final String family) {
        final var window = plans.get(family).window();
        return UUID.nameUUIDFromBytes(
                (namespace(run, stage, family)
                                + "|"
                                + window.partitionStart()
                                + "|"
                                + window.endExclusive())
                        .getBytes(StandardCharsets.UTF_8));
    }

    void persist(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final String stage,
            final LocalDate captureDate) {
        final var coordinator =
                new RuntimeTemporalCoordinator(new JdbcSqlServerTemporalPlan(session));
        for (final var family : List.of("COL", "FRE", "MAN", "COT")) {
            final var plan = plans.get(family);
            coordinator.persistCatchUp(
                    UUID.randomUUID(),
                    namespace(run, stage, family),
                    plan.policy(),
                    captureDate,
                    plan.tick());
        }
        // LOC uses the expansion's existing persisted six-source plan and reserved execution ID.
    }

    List<Receipt> reconcile(
            final ColetaTemporalLaboratorySession session, final UUID run, final String stage) {
        final var coordinator =
                new RuntimeTemporalCoordinator(new JdbcSqlServerTemporalPlan(session));
        final var receipts = new ArrayList<Receipt>();
        for (final var family : List.of("COL", "FRE", "MAN", "COT")) {
            final var plan = plans.get(family);
            final var result =
                    coordinator.reconcile(
                            namespace(run, stage, family),
                            plan.policy(),
                            plan.window().partitionStart(),
                            List.of(execution(run, stage, family)));
            receipts.add(
                    new Receipt(
                            family,
                            plan.window().partitionStart(),
                            plan.window().endExclusive(),
                            plan.window().extractionStart(),
                            result.contiguousEnd(),
                            result.degraded(),
                            plan.backlog()));
        }
        return List.copyOf(receipts);
    }
}
