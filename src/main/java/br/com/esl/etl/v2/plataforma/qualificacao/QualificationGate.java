package br.com.esl.etl.v2.plataforma.qualificacao;

import java.util.List;
import java.util.Map;

/** The test verdict and the data gate are separate; a successful refusal never approves data. */
public record QualificationGate(String scope, State state, String reason, String layer) {
    public enum State {
        PASS_LOCAL,
        FAILED,
        BLOCKED_DEPENDENCY,
        CANCELLED,
        OUTCOME_UNKNOWN
    }

    public QualificationGate {
        if (scope == null
                || !scope.matches("[A-Z0-9_-]{1,40}")
                || state == null
                || reason == null
                || !reason.matches("[A-Z0-9_]{1,80}")
                || !List.of("CONTRACT", "PLANNER", "JDBC", "ORACLE", "PROCESS").contains(layer)) {
            throw new IllegalArgumentException("QUAL_GATE_INVALID");
        }
    }

    public static QualificationGate aggregate(
            final String scope,
            final List<String> dependencies,
            final Map<String, QualificationGate> observed) {
        if (dependencies.isEmpty() || dependencies.size() > 35) {
            throw new IllegalArgumentException("QUAL_GATE_DEPENDENCIES");
        }
        for (final String id : dependencies) {
            final var gate = observed.get(id);
            if (gate == null || gate.state() != State.PASS_LOCAL) {
                return new QualificationGate(
                        scope, State.BLOCKED_DEPENDENCY, "REQUIRED_SCOPE_NOT_APPROVED", "CONTRACT");
            }
        }
        return new QualificationGate(
                scope, State.PASS_LOCAL, "ALL_REQUIRED_SCOPES_PROVEN", "CONTRACT");
    }
}
