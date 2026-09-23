package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Observação 8656 tipada ou quarentena sanitizada; payload nunca aparece no toString. */
public record LocalizacaoCargaStageRecord(
        int inputOrdinal,
        ScopedSourceIdentity.SourceKey sourceKey,
        String payloadJson,
        String fieldPresenceJson,
        String serviceAtRaw,
        Instant serviceAtUtc,
        LocalizacaoCargaAttributePresence serviceAtPresence,
        LocalizacaoCargaParseState serviceAtParseState,
        String invoicesVolumesRaw,
        Integer invoicesVolumes,
        LocalizacaoCargaAttributePresence invoicesVolumesPresence,
        LocalizacaoCargaParseState invoicesVolumesParseState,
        String taxedWeightRaw,
        BigDecimal taxedWeight,
        String invoicesValueRaw,
        BigDecimal invoicesValue,
        String totalRaw,
        BigDecimal total,
        String statusRaw,
        String statusNormalized,
        boolean statusTerminal,
        String statusBranchNicknameProvenance,
        LocalizacaoCargaStageDisposition disposition,
        String quarantineReasonCode) {

    public static final int MAXIMUM_PAGE_SIZE = 100;
    private static final Pattern REASON = Pattern.compile("[A-Z][A-Z0-9_]{2,127}");

    public LocalizacaoCargaStageRecord {
        if (inputOrdinal < 1 || inputOrdinal > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("O ordinal 8656 está fora do microbatch.");
        }
        disposition = Objects.requireNonNull(disposition, "A disposição é obrigatória.");
        if (disposition == LocalizacaoCargaStageDisposition.VALID) {
            if (Objects.requireNonNull(sourceKey, "A chave 8656 é obrigatória.").wireType()
                    != ScopedSourceIdentity.WireType.INTEGER) {
                throw new IllegalArgumentException("A chave 8656 deve manter wire type INTEGER.");
            }
            required(payloadJson, "O payload canônico é obrigatório.");
            required(fieldPresenceJson, "A matriz de presença é obrigatória.");
            serviceAtPresence = Objects.requireNonNull(serviceAtPresence);
            serviceAtParseState = Objects.requireNonNull(serviceAtParseState);
            invoicesVolumesPresence = Objects.requireNonNull(invoicesVolumesPresence);
            invoicesVolumesParseState = Objects.requireNonNull(invoicesVolumesParseState);
            required(statusNormalized, "O status normalizado é obrigatório.");
            if (serviceAtPresence != LocalizacaoCargaAttributePresence.VALUE
                    || serviceAtParseState != LocalizacaoCargaParseState.VALID
                    || serviceAtRaw == null
                    || serviceAtUtc == null) {
                throw new IllegalArgumentException(
                        "Um registro promovível exige service_at válido.");
            }
            validateIntegerEnvelope(
                    invoicesVolumesRaw,
                    invoicesVolumes,
                    invoicesVolumesPresence,
                    invoicesVolumesParseState);
            final LocalizacaoCargaStatusDecision expected =
                    LocalizacaoCargaStatusDecision.fromRaw(statusRaw);
            if (!expected.normalized().equals(statusNormalized)
                    || expected.terminal() != statusTerminal) {
                throw new IllegalArgumentException("O status não segue o catálogo local.");
            }
            if (!"UNSOURCED_LEGACY".equals(statusBranchNicknameProvenance)
                    || quarantineReasonCode != null) {
                throw new IllegalArgumentException("O registro 8656 válido viola LOC-07.");
            }
        } else if (quarantineReasonCode == null
                || !REASON.matcher(quarantineReasonCode).matches()) {
            throw new IllegalArgumentException("A quarentena exige motivo estável.");
        }
    }

    public static LocalizacaoCargaStageRecord quarantine(final int ordinal, final String reason) {
        return quarantine(ordinal, reason, null, null);
    }

    public static LocalizacaoCargaStageRecord quarantine(
            final int ordinal,
            final String reason,
            final String payloadJson,
            final String fieldPresenceJson) {
        return new LocalizacaoCargaStageRecord(
                ordinal,
                null,
                payloadJson,
                fieldPresenceJson,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                LocalizacaoCargaStageDisposition.QUARANTINE,
                reason);
    }

    public boolean quarantined() {
        return disposition == LocalizacaoCargaStageDisposition.QUARANTINE;
    }

    private static void required(final String value, final String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validateIntegerEnvelope(
            final String raw,
            final Integer typed,
            final LocalizacaoCargaAttributePresence presence,
            final LocalizacaoCargaParseState parseState) {
        if (presence == LocalizacaoCargaAttributePresence.VALUE) {
            if (raw == null || typed == null || parseState != LocalizacaoCargaParseState.VALID) {
                throw new IllegalArgumentException("O volume local VALUE deve ser válido.");
            }
            return;
        }
        final LocalizacaoCargaParseState expected =
                presence == LocalizacaoCargaAttributePresence.ABSENT
                        ? LocalizacaoCargaParseState.NOT_PRESENT
                        : LocalizacaoCargaParseState.EXPLICIT_NULL;
        if (raw != null || typed != null || parseState != expected) {
            throw new IllegalArgumentException("O volume local sem valor é incoerente.");
        }
    }

    @Override
    public String toString() {
        return "LocalizacaoCargaStageRecord[inputOrdinal="
                + inputOrdinal
                + ", disposition="
                + disposition
                + ", quarantineReasonCode="
                + quarantineReasonCode
                + ", sensitive=<redacted>]";
    }
}
