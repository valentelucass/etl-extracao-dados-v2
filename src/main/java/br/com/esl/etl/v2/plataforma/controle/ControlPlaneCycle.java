package br.com.esl.etl.v2.plataforma.controle;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ciclo de plano durável, separado das ocorrências por entidade/partição. {@code plannedAt} é
 * metadado de compatibilidade do comando; a criação persiste o relógio autoritativo do SQL Server e
 * retries idempotentes não o usam como identidade.
 */
public record ControlPlaneCycle(UUID cycleId, ImmutableFingerprint plan, Instant plannedAt) {

    public ControlPlaneCycle {
        cycleId = Objects.requireNonNull(cycleId, "O ciclo é obrigatório.");
        plan = Objects.requireNonNull(plan, "O fingerprint do plano é obrigatório.");
        plannedAt = Objects.requireNonNull(plannedAt, "O horário de planejamento é obrigatório.");
    }
}
