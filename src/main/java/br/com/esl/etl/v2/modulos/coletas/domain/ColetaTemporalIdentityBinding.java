package br.com.esl.etl.v2.modulos.coletas.domain;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Correspondência unitária explícita a ser fornecida pelo cruzamento SQL. O fingerprint identifica
 * a evidência; não é aprovação nem autorização operacional.
 */
public record ColetaTemporalIdentityBinding(
        UUID dataExportExecutionId,
        UUID referenceExecutionId,
        ScopedSourceIdentity dataExportIdentity,
        ScopedSourceIdentity referenceIdentity,
        LocalDate requestDate,
        ImmutableFingerprint identityEvidence) {
    public ColetaTemporalIdentityBinding {
        Objects.requireNonNull(dataExportExecutionId, "A execução Data Export é obrigatória.");
        Objects.requireNonNull(referenceExecutionId, "A execução da referência é obrigatória.");
        Objects.requireNonNull(dataExportIdentity, "A identidade Data Export é obrigatória.");
        Objects.requireNonNull(referenceIdentity, "A identidade da referência é obrigatória.");
        Objects.requireNonNull(requestDate, "A janela é obrigatória.");
        Objects.requireNonNull(identityEvidence, "A evidência da correspondência é obrigatória.");
        if (dataExportIdentity.entity() != FirstWaveIdentityContract.Entity.COLETAS
                || referenceIdentity.entity() != FirstWaveIdentityContract.Entity.COLETAS
                || !dataExportIdentity.sourceInstance().equals(referenceIdentity.sourceInstance())
                || !dataExportIdentity.tenantScope().equals(referenceIdentity.tenantScope())) {
            throw new IllegalArgumentException(
                    "A correspondência exige a mesma entidade e escopo.");
        }
        if (dataExportIdentity.sourceKey().wireType() != ScopedSourceIdentity.WireType.INTEGER) {
            throw new IllegalArgumentException("A identidade 6908 deve ser integral.");
        }
    }

    @Override
    public String toString() {
        return "ColetaTemporalIdentityBinding[identities=<redacted>, evidence=<redacted>]";
    }
}
