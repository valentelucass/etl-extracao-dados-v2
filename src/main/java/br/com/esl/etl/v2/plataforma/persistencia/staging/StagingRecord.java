package br.com.esl.etl.v2.plataforma.persistencia.staging;

import br.com.esl.etl.v2.plataforma.SqlText;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Registro minimizado de uma única resposta, sem corpo, URL, token ou campo de domínio. */
public record StagingRecord(
        int inputOrdinal,
        String sourceKey,
        ImmutableFingerprint rowFingerprint,
        ImmutableFingerprint presenceFingerprint,
        Instant sourceFreshnessAt,
        StagingDisposition disposition,
        String quarantineReasonCode) {

    static final int MAXIMUM_SOURCE_KEY_LENGTH = 256;
    static final int MAXIMUM_INPUT_ORDINAL = 10_000;
    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    public StagingRecord {
        if (inputOrdinal < 1 || inputOrdinal > MAXIMUM_INPUT_ORDINAL) {
            throw new IllegalArgumentException(
                    "O ordinal do registro de staging está fora do limite do contrato.");
        }
        disposition = Objects.requireNonNull(disposition, "A disposição de staging é obrigatória.");
        if (sourceKey != null && sourceKey.length() > MAXIMUM_SOURCE_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "A chave de origem excede o limite do contrato de staging.");
        }
        sourceKey = normalizeOptional(sourceKey);
        quarantineReasonCode = normalizeReasonCode(quarantineReasonCode);

        if (disposition == StagingDisposition.VALID) {
            if (sourceKey == null || rowFingerprint == null || presenceFingerprint == null) {
                throw new IllegalArgumentException(
                        "Registro válido exige chave, hash de linha e fingerprint de presença.");
            }
            if (quarantineReasonCode != null) {
                throw new IllegalArgumentException(
                        "Registro válido não pode ter motivo de quarantine.");
            }
        } else if (quarantineReasonCode == null) {
            throw new IllegalArgumentException(
                    "Registro em quarantine exige um motivo sanitizado.");
        }
    }

    private static String normalizeOptional(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = SqlText.trimAsciiSpace(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeReasonCode(final String value) {
        if (value != null && value.length() > 64) {
            throw new IllegalArgumentException("O motivo de quarantine é inválido.");
        }
        final String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        if (!REASON_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("O motivo de quarantine é inválido.");
        }
        return normalized;
    }
}
