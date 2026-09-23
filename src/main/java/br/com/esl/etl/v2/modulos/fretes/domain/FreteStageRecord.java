package br.com.esl.etl.v2.modulos.fretes.domain;

import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Observação 6389 válida ou quarentena sanitizada; nunca imprime payload. */
public record FreteStageRecord(
        int inputOrdinal,
        ScopedSourceIdentity.SourceKey sourceKey,
        String payloadJson,
        String fieldPresenceJson,
        String businessAliasJson,
        String statusRaw,
        String statusCode,
        String statusLabel,
        boolean terminal,
        String freshnessEvidenceJson,
        Instant freshnessAtUtc,
        FreteFreshnessOrigin freshnessOrigin,
        Instant serviceAtUtc,
        String performanceEvidenceJson,
        Instant performanceAtUtc,
        String performanceOrigin,
        String cteFinalizationsJson,
        String financialJson,
        String relationCandidatesJson,
        String sidecarJson,
        String quarantineReasonCode) {

    public static final int MAXIMUM_PAGE_SIZE = 100;
    public static final int MAXIMUM_SIDECAR_EDGES = 100;
    private static final Pattern REASON = Pattern.compile("[A-Z][A-Z0-9_]{2,127}");
    private static final BigInteger MAXIMUM_ID = BigInteger.valueOf(Long.MAX_VALUE);

    public FreteStageRecord {
        if (inputOrdinal < 1 || inputOrdinal > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("O ordinal 6389 está fora da página contratada.");
        }
        if (quarantineReasonCode == null) {
            if (!isPositiveCanonicalId(sourceKey)) {
                throw new IllegalArgumentException("Fretes exige /id INTEGER positivo até BIGINT.");
            }
            payloadJson = required(payloadJson, "O payload canônico é obrigatório.");
            fieldPresenceJson = required(fieldPresenceJson, "A presença é obrigatória.");
            businessAliasJson = required(businessAliasJson, "O alias versionado é obrigatório.");
            freshnessEvidenceJson =
                    required(freshnessEvidenceJson, "A evidência de frescor é obrigatória.");
            freshnessAtUtc = Objects.requireNonNull(freshnessAtUtc, "O frescor é obrigatório.");
            freshnessOrigin =
                    Objects.requireNonNull(freshnessOrigin, "A origem de frescor é obrigatória.");
            performanceEvidenceJson =
                    required(performanceEvidenceJson, "A performance é obrigatória.");
            if ((performanceAtUtc == null) != (performanceOrigin == null)) {
                throw new IllegalArgumentException("Performance tipada e origem devem coexistir.");
            }
            if (performanceOrigin != null
                    && !performanceOrigin.equals("OFFICIAL_6389")
                    && !performanceOrigin.equals("FINISHED_AT_FALLBACK")) {
                throw new IllegalArgumentException("A origem de performance não é governada.");
            }
            cteFinalizationsJson =
                    required(cteFinalizationsJson, "CT-e/finalizações são obrigatórios.");
            financialJson = required(financialJson, "A evidência financeira é obrigatória.");
            relationCandidatesJson =
                    required(relationCandidatesJson, "Os candidatos relacionais são obrigatórios.");
            sidecarJson = required(sidecarJson, "O envelope sidecar é obrigatório.");
            validateStatus(statusRaw, statusCode, statusLabel, terminal);
        } else {
            if (!REASON.matcher(quarantineReasonCode).matches()) {
                throw new IllegalArgumentException("A quarentena exige motivo estável.");
            }
            if (sourceKey != null
                    || payloadJson != null
                    || fieldPresenceJson != null
                    || businessAliasJson != null
                    || statusRaw != null
                    || statusCode != null
                    || statusLabel != null
                    || terminal
                    || freshnessEvidenceJson != null
                    || freshnessAtUtc != null
                    || freshnessOrigin != null
                    || serviceAtUtc != null
                    || performanceEvidenceJson != null
                    || performanceAtUtc != null
                    || performanceOrigin != null
                    || cteFinalizationsJson != null
                    || financialJson != null
                    || relationCandidatesJson != null
                    || sidecarJson != null) {
                throw new IllegalArgumentException("A quarentena não aceita tipados ambíguos.");
            }
        }
    }

    public static FreteStageRecord quarantine(final int ordinal, final String reason) {
        return new FreteStageRecord(
                ordinal, null, null, null, null, null, null, null, false, null, null, null, null,
                null, null, null, null, null, null, null, reason);
    }

    public boolean quarantined() {
        return quarantineReasonCode != null;
    }

    public static boolean isPositiveCanonicalId(final ScopedSourceIdentity.SourceKey key) {
        if (key == null || key.wireType() != ScopedSourceIdentity.WireType.INTEGER) {
            return false;
        }
        final String storage = key.storageValue();
        if (!storage.matches("INTEGER:[1-9][0-9]*")) {
            return false;
        }
        final BigInteger value = new BigInteger(storage.substring("INTEGER:".length()));
        return value.compareTo(MAXIMUM_ID) <= 0;
    }

    private static void validateStatus(
            final String raw, final String code, final String label, final boolean terminal) {
        final FreteStatusDecision expected = FreteStatusDecision.fromRaw(raw);
        if (!Objects.equals(expected.code(), code)
                || !Objects.equals(expected.label(), label)
                || expected.terminal() != terminal) {
            throw new IllegalArgumentException("O status não segue o catálogo fretes-status-v1.");
        }
    }

    private static String required(final String value, final String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    @Override
    public String toString() {
        return "FreteStageRecord[inputOrdinal="
                + inputOrdinal
                + ", quarantined="
                + quarantined()
                + ", freshnessOrigin="
                + freshnessOrigin
                + ", quarantineReasonCode="
                + quarantineReasonCode
                + ", sensitive=<redacted>]";
    }
}
