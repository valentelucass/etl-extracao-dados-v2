package br.com.esl.etl.v2.modulos.cotacoes.domain;

import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.regex.Pattern;

/** Uma observação 6906 válida ou uma quarentena sanitizada, nunca o payload em log. */
public record CotacaoStageRecord(
        int inputOrdinal,
        ScopedSourceIdentity.SourceKey sourceKey,
        String payloadJson,
        String fieldPresenceJson,
        String userNameNormalized,
        Instant nfseIssuedAtUtc,
        Instant cteIssuedAtUtc,
        Instant requestedAtUtc,
        Instant freshnessAtUtc,
        CotacaoFreshnessOrigin freshnessOrigin,
        LocalDate freshnessBusinessDate,
        BigDecimal totalAmount,
        String currencyCode,
        String originUf,
        String destinationUf,
        String quarantineReasonCode) {

    public static final int MAXIMUM_PAGE_SIZE = 1_000;
    private static final String INTEGER_PREFIX = "INTEGER:";
    private static final BigInteger MAXIMUM_SEQUENCE_CODE = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int DECIMAL_INTEGER_DIGITS = 15;
    private static final int DECIMAL_SCALE = 4;
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Pattern REASON = Pattern.compile("[A-Z][A-Z0-9_]{2,127}");

    public CotacaoStageRecord {
        if (inputOrdinal < 1 || inputOrdinal > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("O ordinal de Cotações está fora da página.");
        }
        if (quarantineReasonCode == null) {
            sourceKey = Objects.requireNonNull(sourceKey, "A source key é obrigatória.");
            if (!isPositiveSequenceCode(sourceKey)) {
                throw new IllegalArgumentException(
                        "Cotações aceita sequence_code entre 1 e 9223372036854775807.");
            }
            payloadJson = required(payloadJson, "O payload canônico é obrigatório.");
            fieldPresenceJson = required(fieldPresenceJson, "A presença canônica é obrigatória.");
            userNameNormalized = nullableNormalizedText(userNameNormalized);
            freshnessAtUtc = Objects.requireNonNull(freshnessAtUtc, "O frescor é obrigatório.");
            freshnessOrigin =
                    Objects.requireNonNull(freshnessOrigin, "A origem de frescor é obrigatória.");
            freshnessBusinessDate =
                    Objects.requireNonNull(
                            freshnessBusinessDate, "A data de negócio de frescor é obrigatória.");
            requireConsistentFreshness(
                    nfseIssuedAtUtc,
                    cteIssuedAtUtc,
                    requestedAtUtc,
                    freshnessAtUtc,
                    freshnessOrigin,
                    freshnessBusinessDate);
            if (!isTotalAmountRepresentable(totalAmount)) {
                throw new IllegalArgumentException("O valor de Cotações excede DECIMAL(19,4).");
            }
            if (currencyCode != null) {
                throw new IllegalArgumentException(
                        "A moeda de Cotações pertence somente à referência tarifária.");
            }
            originUf = nullableUf(originUf);
            destinationUf = nullableUf(destinationUf);
        } else {
            if (!REASON.matcher(quarantineReasonCode).matches()) {
                throw new IllegalArgumentException("A quarentena exige motivo estável sanitizado.");
            }
            if (sourceKey != null
                    || payloadJson != null
                    || fieldPresenceJson != null
                    || userNameNormalized != null
                    || nfseIssuedAtUtc != null
                    || cteIssuedAtUtc != null
                    || requestedAtUtc != null
                    || freshnessAtUtc != null
                    || freshnessOrigin != null
                    || freshnessBusinessDate != null
                    || totalAmount != null
                    || currencyCode != null
                    || originUf != null
                    || destinationUf != null) {
                throw new IllegalArgumentException(
                        "A quarentena de Cotações não aceita campos tipados ambíguos.");
            }
        }
    }

    public static boolean isPositiveSequenceCode(final ScopedSourceIdentity.SourceKey sourceKey) {
        if (sourceKey == null || sourceKey.wireType() != ScopedSourceIdentity.WireType.INTEGER) {
            return false;
        }
        final BigInteger sequenceCode =
                new BigInteger(sourceKey.storageValue().substring(INTEGER_PREFIX.length()));
        return sequenceCode.signum() > 0 && sequenceCode.compareTo(MAXIMUM_SEQUENCE_CODE) <= 0;
    }

    public static boolean isTotalAmountRepresentable(final BigDecimal value) {
        if (value == null) {
            return true;
        }
        if (value.scale() > DECIMAL_SCALE) {
            return false;
        }
        final long integerDigits = (long) value.precision() - value.scale();
        return value.signum() == 0 || integerDigits <= DECIMAL_INTEGER_DIGITS;
    }

    public static CotacaoStageRecord quarantine(final int ordinal, final String reason) {
        return new CotacaoStageRecord(
                ordinal, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, reason);
    }

    public boolean quarantined() {
        return quarantineReasonCode != null;
    }

    private static String required(final String value, final String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String nullableUf(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        if (!trimmed.matches("[A-Za-z]{2}")) {
            throw new IllegalArgumentException("Código textual inválido para Cotações.");
        }
        return trimmed.toUpperCase(java.util.Locale.ROOT);
    }

    private static String nullableNormalizedText(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        return normalized.isEmpty() ? null : normalized;
    }

    private static void requireConsistentFreshness(
            final Instant nfseIssuedAt,
            final Instant cteIssuedAt,
            final Instant requestedAt,
            final Instant freshnessAt,
            final CotacaoFreshnessOrigin freshnessOrigin,
            final LocalDate freshnessBusinessDate) {
        final Instant expectedAt;
        final CotacaoFreshnessOrigin expectedOrigin;
        if (nfseIssuedAt != null) {
            expectedAt = nfseIssuedAt;
            expectedOrigin = CotacaoFreshnessOrigin.NFSE_ISSUED_AT;
        } else if (cteIssuedAt != null) {
            expectedAt = cteIssuedAt;
            expectedOrigin = CotacaoFreshnessOrigin.CTE_ISSUED_AT;
        } else if (requestedAt != null) {
            expectedAt = requestedAt;
            expectedOrigin = CotacaoFreshnessOrigin.REQUESTED_AT;
        } else {
            throw new IllegalArgumentException("Cotações exige um instante de frescor da origem.");
        }
        if (!expectedAt.equals(freshnessAt) || expectedOrigin != freshnessOrigin) {
            throw new IllegalArgumentException(
                    "O frescor de Cotações não segue a precedência aprovada.");
        }
        if (!expectedAt.atZone(SOURCE_ZONE).toLocalDate().equals(freshnessBusinessDate)) {
            throw new IllegalArgumentException(
                    "A data de negócio de Cotações diverge do frescor aprovado.");
        }
    }

    @Override
    public String toString() {
        return "CotacaoStageRecord[inputOrdinal="
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
