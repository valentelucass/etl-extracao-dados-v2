package br.com.esl.etl.v2.plataforma.controle;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Falha SQL com operação sanitizada e causa preservada para diagnóstico controlado.
 *
 * <p>Chamadores não devem registrar a causa bruta em logs de aplicação; a camada de observabilidade
 * de V2-023 é responsável por redigir detalhes do driver.
 */
public final class ControlPlanePersistenceException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    ControlPlanePersistenceException(final String operation, final SQLException cause) {
        super(
                "O control plane não pôde concluir "
                        + Objects.requireNonNull(operation, "A operação é obrigatória.")
                        + ".",
                Objects.requireNonNull(cause, "A causa SQL é obrigatória."));
    }
}
