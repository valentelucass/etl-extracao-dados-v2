package br.com.esl.etl.v2.plataforma.controle;

import br.com.esl.etl.v2.plataforma.SqlText;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Transição explícita e auditável. A publicação é exclusiva do protocolo de publicação. {@code
 * transitionedAt} é observação do caller; evento, terminal e release usam o relógio do banco após o
 * fencing.
 */
public record ControlPlaneTransition(
        UUID executionId,
        ExecutionState expectedCurrentState,
        ExecutionState nextState,
        String reasonCode,
        Instant transitionedAt) {

    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    public ControlPlaneTransition {
        executionId = Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        expectedCurrentState =
                Objects.requireNonNull(
                        expectedCurrentState, "O estado atual esperado é obrigatório.");
        nextState = Objects.requireNonNull(nextState, "O próximo estado é obrigatório.");
        transitionedAt =
                Objects.requireNonNull(transitionedAt, "O horário da transição é obrigatório.");
        Objects.requireNonNull(reasonCode, "O código do motivo é obrigatório.");
        if (reasonCode.length() > 64) {
            throw new IllegalArgumentException("O código do motivo da transição é inválido.");
        }
        reasonCode = SqlText.trimAsciiSpace(reasonCode);
        if (!REASON_CODE.matcher(reasonCode).matches()) {
            throw new IllegalArgumentException("O código do motivo da transição é inválido.");
        }
        if (!expectedCurrentState.canTransitionTo(nextState)) {
            throw new IllegalArgumentException("A transição de estado solicitada não é permitida.");
        }
    }
}
