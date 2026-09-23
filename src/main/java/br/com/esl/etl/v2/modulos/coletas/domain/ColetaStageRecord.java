package br.com.esl.etl.v2.modulos.coletas.domain;

import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Envelope tipado de uma linha física 6908. JSON preservado nunca é incluído em {@code toString}.
 */
public final class ColetaStageRecord {

    public static final int MAXIMUM_PAGE_SIZE = 100;
    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    private final int inputOrdinal;
    private final ScopedSourceIdentity.SourceKey sourceKey;
    private final ColetaAttributePresence sequenceCodePresence;
    private final String sequenceCodeJson;
    private final String payloadJson;
    private final String fieldPresenceJson;
    private final String relationCandidatesJson;
    private final ColetaStatus.Resolved status;
    private final String freshnessRaw;
    private final Instant freshnessAtUtc;
    private final ColetaFreshnessOrigin freshnessOrigin;
    private final ColetaStageDisposition disposition;
    private final String quarantineReasonCode;

    private ColetaStageRecord(
            final int inputOrdinal,
            final ScopedSourceIdentity.SourceKey sourceKey,
            final ColetaAttributePresence sequenceCodePresence,
            final String sequenceCodeJson,
            final String payloadJson,
            final String fieldPresenceJson,
            final String relationCandidatesJson,
            final ColetaStatus.Resolved status,
            final String freshnessRaw,
            final Instant freshnessAtUtc,
            final ColetaFreshnessOrigin freshnessOrigin,
            final ColetaStageDisposition disposition,
            final String quarantineReasonCode) {
        if (inputOrdinal < 1 || inputOrdinal > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("O ordinal de Coletas está fora da página.");
        }
        this.inputOrdinal = inputOrdinal;
        this.disposition = Objects.requireNonNull(disposition, "A disposição é obrigatória.");
        if (disposition == ColetaStageDisposition.VALID) {
            this.sourceKey = Objects.requireNonNull(sourceKey, "A source key é obrigatória.");
            if (sourceKey.wireType() != ScopedSourceIdentity.WireType.INTEGER) {
                throw new IllegalArgumentException("Coletas aceita somente source key integral.");
            }
            this.sequenceCodePresence =
                    Objects.requireNonNull(
                            sequenceCodePresence, "A presença do alias é obrigatória.");
            validateJson(sequenceCodePresence, sequenceCodeJson, "O alias de Coletas é inválido.");
            this.sequenceCodeJson = sequenceCodeJson;
            this.payloadJson = requiredJson(payloadJson, "O payload de Coletas é obrigatório.");
            this.fieldPresenceJson =
                    requiredJson(fieldPresenceJson, "A presença de campos é obrigatória.");
            this.relationCandidatesJson =
                    requiredJson(
                            relationCandidatesJson, "Os candidatos relacionais são obrigatórios.");
            this.status = Objects.requireNonNull(status, "O status resolvido é obrigatório.");
            this.freshnessRaw = freshnessRaw;
            this.freshnessAtUtc = freshnessAtUtc;
            this.freshnessOrigin =
                    Objects.requireNonNull(freshnessOrigin, "A origem de frescor é obrigatória.");
            if ((freshnessAtUtc == null)
                    != (freshnessOrigin == ColetaFreshnessOrigin.UNAVAILABLE)) {
                throw new IllegalArgumentException("O frescor tipado de Coletas é inconsistente.");
            }
            if (quarantineReasonCode != null) {
                throw new IllegalArgumentException(
                        "Registro válido de Coletas não aceita motivo de quarantine.");
            }
            this.quarantineReasonCode = null;
            return;
        }
        this.sourceKey = sourceKey;
        this.sequenceCodePresence = null;
        this.sequenceCodeJson = null;
        this.payloadJson = null;
        this.fieldPresenceJson = null;
        this.relationCandidatesJson = null;
        this.status = null;
        this.freshnessRaw = null;
        this.freshnessAtUtc = null;
        this.freshnessOrigin = null;
        if (quarantineReasonCode == null || !REASON_CODE.matcher(quarantineReasonCode).matches()) {
            throw new IllegalArgumentException("Registro em quarantine exige motivo sanitizado.");
        }
        this.quarantineReasonCode = quarantineReasonCode;
    }

    public static ColetaStageRecord valid(
            final int inputOrdinal,
            final ScopedSourceIdentity.SourceKey sourceKey,
            final ColetaAttributePresence sequenceCodePresence,
            final String sequenceCodeJson,
            final String payloadJson,
            final String fieldPresenceJson,
            final String relationCandidatesJson,
            final ColetaStatus.Resolved status,
            final String freshnessRaw,
            final Instant freshnessAtUtc,
            final ColetaFreshnessOrigin freshnessOrigin) {
        return new ColetaStageRecord(
                inputOrdinal,
                sourceKey,
                sequenceCodePresence,
                sequenceCodeJson,
                payloadJson,
                fieldPresenceJson,
                relationCandidatesJson,
                status,
                freshnessRaw,
                freshnessAtUtc,
                freshnessOrigin,
                ColetaStageDisposition.VALID,
                null);
    }

    public static ColetaStageRecord quarantine(
            final int inputOrdinal,
            final ScopedSourceIdentity.SourceKey sourceKey,
            final String quarantineReasonCode) {
        return new ColetaStageRecord(
                inputOrdinal,
                sourceKey,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ColetaStageDisposition.QUARANTINE,
                quarantineReasonCode);
    }

    public int inputOrdinal() {
        return inputOrdinal;
    }

    public ScopedSourceIdentity.SourceKey sourceKey() {
        return sourceKey;
    }

    public ColetaAttributePresence sequenceCodePresence() {
        return sequenceCodePresence;
    }

    public String sequenceCodeJson() {
        return sequenceCodeJson;
    }

    public String payloadJson() {
        return payloadJson;
    }

    public String fieldPresenceJson() {
        return fieldPresenceJson;
    }

    public String relationCandidatesJson() {
        return relationCandidatesJson;
    }

    public ColetaStatus.Resolved status() {
        return status;
    }

    public String freshnessRaw() {
        return freshnessRaw;
    }

    public Instant freshnessAtUtc() {
        return freshnessAtUtc;
    }

    public ColetaFreshnessOrigin freshnessOrigin() {
        return freshnessOrigin;
    }

    public ColetaStageDisposition disposition() {
        return disposition;
    }

    public String quarantineReasonCode() {
        return quarantineReasonCode;
    }

    @Override
    public String toString() {
        return "ColetaStageRecord[inputOrdinal="
                + inputOrdinal
                + ", disposition="
                + disposition
                + ", sourceKey=<redacted>, payload=<redacted>]";
    }

    private static void validateJson(
            final ColetaAttributePresence presence, final String value, final String message) {
        if ((presence == ColetaAttributePresence.VALUE) != (value != null)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String requiredJson(final String value, final String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
