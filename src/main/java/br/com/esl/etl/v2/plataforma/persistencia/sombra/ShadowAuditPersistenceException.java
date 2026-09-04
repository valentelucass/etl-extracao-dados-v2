package br.com.esl.etl.v2.plataforma.persistencia.sombra;

/** Falha sanitizada de persistência de auditoria, sem propagar detalhe do driver ou da origem. */
public final class ShadowAuditPersistenceException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    ShadowAuditPersistenceException(final String operation, final Throwable cause) {
        super("A auditoria de sombra não pôde registrar " + operation + ".", cause);
    }
}
