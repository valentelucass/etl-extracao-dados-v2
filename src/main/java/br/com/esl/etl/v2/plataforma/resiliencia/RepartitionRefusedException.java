package br.com.esl.etl.v2.plataforma.resiliencia;

import java.util.Objects;

/** Recusa tipada sem incluir janela, filtro ou identificador na mensagem. */
public final class RepartitionRefusedException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final RepartitionRefusalReason reason;

    public RepartitionRefusedException(final RepartitionRefusalReason reason) {
        super(
                "O reparticionamento foi recusado: "
                        + Objects.requireNonNull(reason, "O motivo é obrigatório.").name()
                        + ".");
        this.reason = reason;
    }

    public RepartitionRefusalReason reason() {
        return reason;
    }
}
