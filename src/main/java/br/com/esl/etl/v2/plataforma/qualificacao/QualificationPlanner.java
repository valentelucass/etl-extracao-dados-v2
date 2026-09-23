package br.com.esl.etl.v2.plataforma.qualificacao;

import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

/** The existing civil planner determines dispatch windows and explicit deferral reasons. */
public final class QualificationPlanner {
    private QualificationPlanner() {}

    public record Plan(
            List<RuntimeTemporalPlanner.Window> windows,
            boolean backlog,
            QualificationGate.State state,
            String reason) {
        public Plan {
            if (windows == null || windows.size() > 3) {
                throw new IllegalArgumentException("QUAL_PLANNER_WINDOWS_BOUND");
            }
            windows = List.copyOf(windows);
        }
    }

    public static Plan plan(final QualificationCampaign.Case item) {
        final var policy =
                new RuntimeTemporalPolicy(
                        "qualification-temporal-v1",
                        item.zone(),
                        item.mode(),
                        RuntimeWindowStrategy.INTERVAL,
                        RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                        LocalTime.MIDNIGHT,
                        Duration.ofSeconds(item.lookbackSeconds()),
                        Duration.ZERO,
                        Duration.ofSeconds(item.deadlineSeconds()),
                        Duration.ofSeconds(item.deadlineSeconds()),
                        1,
                        item.maximumCatchUp(),
                        3,
                        3,
                        item.blackouts().stream()
                                .map(
                                        day ->
                                                new RuntimeTemporalPolicy.Blackout(
                                                        day, day.plusDays(1)))
                                .toList());
        final var result = new RuntimeTemporalPlanner().plan(policy, item.start(), item.tick());
        final var selected =
                result.windows().stream()
                        .filter(
                                window ->
                                        !window.endExclusive()
                                                .isAfter(
                                                        item.endExclusive()
                                                                .atStartOfDay(item.zone())
                                                                .toInstant()))
                        .toList();
        if (selected.isEmpty()) {
            return new Plan(
                    selected,
                    result.backlogRemaining(),
                    QualificationGate.State.BLOCKED_DEPENDENCY,
                    result.blockedByBlackout() ? "BLACKOUT" : "WINDOW_NOT_DUE");
        }
        if (selected.stream().anyMatch(window -> item.tick().isAfter(window.deadlineAt()))) {
            return new Plan(
                    selected,
                    result.backlogRemaining(),
                    QualificationGate.State.BLOCKED_DEPENDENCY,
                    "LOGICAL_DEADLINE_EXCEEDED");
        }
        return new Plan(
                selected, result.backlogRemaining(), QualificationGate.State.PASS_LOCAL, "DUE");
    }
}
