package br.com.esl.etl.v2.plataforma.configuracao;

import java.time.ZoneId;
import java.util.Objects;

/** Resultado sanitizado do preflight; não contém endpoint, conexão ou segredo. */
public record RuntimePreflightReport(
        RuntimeEnvironment environment,
        ZoneId businessZone,
        boolean dataExportEnabled,
        boolean shadowAuditEnabled) {

    public RuntimePreflightReport {
        environment = Objects.requireNonNull(environment, "O ambiente é obrigatório.");
        businessZone = Objects.requireNonNull(businessZone, "O timezone de negócio é obrigatório.");
    }

    public String summary() {
        return "Preflight válido: ambiente="
                + environment
                + ", timezone="
                + businessZone.getId()
                + ", dataExport="
                + (dataExportEnabled ? "habilitado" : "desabilitado")
                + ", auditoriaSombra="
                + (shadowAuditEnabled ? "habilitada" : "desabilitada")
                + ", autorizacaoOperacional=nao-configurada-deny-all";
    }
}
