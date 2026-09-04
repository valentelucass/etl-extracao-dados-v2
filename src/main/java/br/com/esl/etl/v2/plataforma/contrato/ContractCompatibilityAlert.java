package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.Objects;
import java.util.UUID;

/** Evento sanitizado e limitado para uma mudança compatível aceita pela política. */
public record ContractCompatibilityAlert(
        UUID executionId,
        ContractChange.Component component,
        ContractChange.Kind kind,
        ImmutableFingerprint changeSignature) {

    public ContractCompatibilityAlert {
        Objects.requireNonNull(executionId, "O execution_id do alerta é obrigatório.");
        component = Objects.requireNonNull(component, "O componente do alerta é obrigatório.");
        kind = Objects.requireNonNull(kind, "A categoria do alerta é obrigatória.");
        if (!kind.compatible()) {
            throw new IllegalArgumentException("O alerta exige uma mudança compatível.");
        }
        changeSignature =
                Objects.requireNonNull(changeSignature, "A assinatura do alerta é obrigatória.");
    }
}
