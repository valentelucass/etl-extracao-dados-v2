package br.com.esl.etl.v2.plataforma.identidade;

import br.com.esl.etl.v2.plataforma.SqlText;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Tuple escopada de origem e codec unitário de source key, sem coerção entre tipos JSON. */
public record ScopedSourceIdentity(
        String sourceInstance,
        String tenantScope,
        FirstWaveIdentityContract.Entity entity,
        SourceKey sourceKey) {

    private static final Pattern SCOPE_IDENTIFIER =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern INTEGER_STORAGE = Pattern.compile("INTEGER:-?(?:0|[1-9][0-9]*)");

    public ScopedSourceIdentity {
        sourceInstance = scope(sourceInstance, false);
        tenantScope = scope(tenantScope, true);
        entity = Objects.requireNonNull(entity, "A entidade é obrigatória.");
        sourceKey = Objects.requireNonNull(sourceKey, "A source key é obrigatória.");
    }

    public static ScopedSourceIdentity fromJson(
            final String sourceInstance,
            final String tenantScope,
            final FirstWaveIdentityContract contract,
            final JsonNode rawSourceKey) {
        final FirstWaveIdentityContract required =
                Objects.requireNonNull(contract, "O contrato de identidade é obrigatório.");
        return new ScopedSourceIdentity(
                sourceInstance,
                tenantScope,
                required.entity(),
                SourceKey.fromJson(required.sourceKey().wireTypes(), rawSourceKey));
    }

    @Override
    public String toString() {
        return "ScopedSourceIdentity[sourceInstance=<redacted>, tenantScope=<redacted>, entity="
                + entity
                + ", sourceKey=<redacted>]";
    }

    private static String scope(final String value, final boolean rejectGlobalSentinel) {
        if (value == null || !SCOPE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("O namespace de identidade é inválido.");
        }
        final String normalized = SqlText.trimAsciiSpace(value);
        if (!normalized.equals(value)) {
            throw new IllegalArgumentException("O namespace de identidade não pode ser ajustado.");
        }
        if (rejectGlobalSentinel) {
            final String upper = normalized.toUpperCase(Locale.ROOT);
            if (upper.equals("GLOBAL") || upper.equals("SINGLETON") || upper.equals("DEFAULT")) {
                throw new IllegalArgumentException(
                        "O tenant exige escopo explícito; sentinel global não é permitido.");
            }
        }
        return normalized;
    }

    /** Valor persistível com tag de tipo; o accessor é para staging, nunca para logging. */
    public record SourceKey(WireType wireType, String storageValue) {

        public static final int MAXIMUM_STORAGE_CHARACTERS = 256;

        public SourceKey {
            wireType = Objects.requireNonNull(wireType, "O tipo da source key é obrigatório.");
            storageValue =
                    Objects.requireNonNull(storageValue, "O valor persistível é obrigatório.");
            validateStorage(wireType, storageValue);
        }

        public static SourceKey fromJson(
                final FirstWaveIdentityContract.WireTypePolicy policy,
                final JsonNode rawSourceKey) {
            final FirstWaveIdentityContract.WireTypePolicy requiredPolicy =
                    Objects.requireNonNull(policy, "A política de wire type é obrigatória.");
            if (rawSourceKey == null || rawSourceKey.isMissingNode() || rawSourceKey.isNull()) {
                throw new IdentityQuarantineException(
                        IdentityQuarantineException.Reason.MISSING_SOURCE_KEY);
            }
            if (rawSourceKey.isIntegralNumber() && requiredPolicy.permitsInteger()) {
                final BigInteger value = rawSourceKey.bigIntegerValue();
                return checked(WireType.INTEGER, "INTEGER:" + value);
            }
            if (rawSourceKey.isTextual() && requiredPolicy.permitsString()) {
                return checked(WireType.STRING, "STRING:" + rawSourceKey.textValue());
            }
            throw new IdentityQuarantineException(
                    IdentityQuarantineException.Reason.INVALID_SOURCE_KEY_TYPE);
        }

        @Override
        public String toString() {
            return "SourceKey[wireType=" + wireType + ", storageValue=<redacted>]";
        }

        private static SourceKey checked(final WireType type, final String storageValue) {
            if (storageValue.length() > MAXIMUM_STORAGE_CHARACTERS) {
                throw new IdentityQuarantineException(
                        IdentityQuarantineException.Reason.SOURCE_KEY_TOO_LONG);
            }
            try {
                return new SourceKey(type, storageValue);
            } catch (final IllegalArgumentException exception) {
                throw new IdentityQuarantineException(
                        IdentityQuarantineException.Reason.INVALID_SOURCE_KEY_VALUE);
            }
        }

        private static void validateStorage(final WireType type, final String storageValue) {
            if (storageValue.length() > MAXIMUM_STORAGE_CHARACTERS) {
                throw new IllegalArgumentException("A source key excede o limite de staging.");
            }
            if (type == WireType.INTEGER) {
                if (!INTEGER_STORAGE.matcher(storageValue).matches()) {
                    throw new IllegalArgumentException("A source key integral não é canônica.");
                }
                return;
            }
            if (!storageValue.startsWith("STRING:")) {
                throw new IllegalArgumentException("A source key textual não contém tag de tipo.");
            }
            final String value = storageValue.substring("STRING:".length());
            if (value.isBlank()
                    || !SqlText.trimAsciiSpace(value).equals(value)
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                    || value.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("A source key textual não é persistível.");
            }
        }
    }

    public enum WireType {
        INTEGER,
        STRING
    }
}
