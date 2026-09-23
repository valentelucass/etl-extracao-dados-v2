package br.com.esl.etl.v2.modulos.coletas.domain;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Referência temporal separada da linha 6908; campos brutos são somente para persistência. */
public record ColetaTemporalObservation(
        UUID executionId,
        ScopedSourceIdentity identity,
        LocalDate queryDate,
        String contractVersion,
        ImmutableFingerprint selectionFingerprint,
        int pageNumber,
        int inputOrdinal,
        Instant observedAt,
        Field status,
        Field statusUpdatedAt,
        Field requestDate,
        Instant statusAtUtc) {

    public static final int MAXIMUM_PAGE_SIZE = 20;

    public ColetaTemporalObservation {
        Objects.requireNonNull(executionId, "A execução é obrigatória.");
        Objects.requireNonNull(identity, "A identidade é obrigatória.");
        if (identity.entity() != FirstWaveIdentityContract.Entity.COLETAS) {
            throw new IllegalArgumentException("A referência deve pertencer a Coletas.");
        }
        Objects.requireNonNull(queryDate, "A data consultada é obrigatória.");
        if (contractVersion == null || contractVersion.isBlank()) {
            throw new IllegalArgumentException("A versão do contrato é obrigatória.");
        }
        Objects.requireNonNull(selectionFingerprint, "A seleção é obrigatória.");
        if (pageNumber < 1 || inputOrdinal < 1 || inputOrdinal > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("A posição da referência é inválida.");
        }
        Objects.requireNonNull(observedAt, "A captura é obrigatória.");
        Objects.requireNonNull(status, "A presença do status é obrigatória.");
        Objects.requireNonNull(statusUpdatedAt, "A presença temporal é obrigatória.");
        Objects.requireNonNull(requestDate, "A presença da data é obrigatória.");
        if (statusAtUtc != null && statusUpdatedAt.text() == null) {
            throw new IllegalArgumentException("Instante sem texto de origem.");
        }
    }

    @Override
    public String toString() {
        return "ColetaTemporalObservation[provenance=<redacted>, fields=<redacted>]";
    }

    /** Ausente, nulo e valor inválido continuam distintos; JSON não é interpretado pelo domínio. */
    public record Field(ColetaAttributePresence presence, String rawJson, String text) {
        public Field {
            Objects.requireNonNull(presence, "A presença é obrigatória.");
            if ((presence == ColetaAttributePresence.VALUE) != (rawJson != null)
                    || presence != ColetaAttributePresence.VALUE && text != null) {
                throw new IllegalArgumentException("A presença e o valor são inconsistentes.");
            }
        }

        @Override
        public String toString() {
            return "Field[presence=" + presence + ", value=<redacted>]";
        }
    }
}
