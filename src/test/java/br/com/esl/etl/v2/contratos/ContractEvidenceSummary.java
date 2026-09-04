package br.com.esl.etl.v2.contratos;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Documento versionável apenas em {@code target/}, sem destino, segredo, payload ou identidade. */
public record ContractEvidenceSummary(
        String runId,
        String generatedAtUtc,
        List<ContractTemplateEvidence> templates,
        ContractCrossDomainEvidence crossDomainEvidence) {

    public ContractEvidenceSummary(
            final String runId,
            final String generatedAtUtc,
            final List<ContractTemplateEvidence> templates) {
        this(runId, generatedAtUtc, templates, ContractCrossDomainEvidence.notObserved());
    }

    public ContractEvidenceSummary {
        if (runId == null || !runId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException(
                    "O identificador da execução de evidência é inválido.");
        }
        if (generatedAtUtc == null || !generatedAtUtc.endsWith("Z")) {
            throw new IllegalArgumentException("O horário da evidência é obrigatório.");
        }
        try {
            Instant.parse(generatedAtUtc);
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException("O horário da evidência é inválido.");
        }
        templates =
                List.copyOf(
                        Objects.requireNonNull(
                                templates, "As evidências por template são obrigatórias."));
        crossDomainEvidence =
                Objects.requireNonNull(
                        crossDomainEvidence, "A evidência cruzada de domínio é obrigatória.");
    }
}
