package br.com.esl.etl.v2.modulos.manifestos.domain;

import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Uma observação física 6399; conteúdo nunca é exposto por {@code toString}. */
public final class ManifestoStageRecord {

    public static final int MAXIMUM_PAGE_SIZE = 100;
    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    private final int inputOrdinal;
    private final ScopedSourceIdentity.SourceKey sourceKey;
    private final Map<String, ManifestoFieldValue> rootFields;
    private final Map<ManifestoMetric, ManifestoMetricValue> metrics;
    private final String payloadJson;
    private final String fieldPresenceJson;
    private final String relationCandidatesJson;
    private final Instant freshnessAtUtc;
    private final ManifestoFreshnessOrigin freshnessOrigin;
    private final ManifestoFieldValue competence;
    private final Optional<ScopedSourceIdentity.SourceKey> pickSourceKey;
    private final Optional<ManifestoMdfeObservation> mdfe;
    private final ManifestoStageDisposition disposition;
    private final String quarantineReasonCode;

    private ManifestoStageRecord(
            final int inputOrdinal,
            final ScopedSourceIdentity.SourceKey sourceKey,
            final Map<String, ManifestoFieldValue> rootFields,
            final Map<ManifestoMetric, ManifestoMetricValue> metrics,
            final String payloadJson,
            final String fieldPresenceJson,
            final String relationCandidatesJson,
            final Instant freshnessAtUtc,
            final ManifestoFreshnessOrigin freshnessOrigin,
            final ManifestoFieldValue competence,
            final ScopedSourceIdentity.SourceKey pickSourceKey,
            final ManifestoMdfeObservation mdfe,
            final ManifestoStageDisposition disposition,
            final String quarantineReasonCode) {
        if (inputOrdinal < 1 || inputOrdinal > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("O ordinal de Manifestos está fora da página.");
        }
        this.inputOrdinal = inputOrdinal;
        this.disposition = Objects.requireNonNull(disposition, "A disposição é obrigatória.");
        if (disposition == ManifestoStageDisposition.QUARANTINE) {
            this.sourceKey = sourceKey;
            this.rootFields = Map.of();
            this.metrics = Map.of();
            this.payloadJson = null;
            this.fieldPresenceJson = null;
            this.relationCandidatesJson = null;
            this.freshnessAtUtc = null;
            this.freshnessOrigin = null;
            this.competence = null;
            this.pickSourceKey = Optional.empty();
            this.mdfe = Optional.empty();
            if (quarantineReasonCode == null
                    || !REASON_CODE.matcher(quarantineReasonCode).matches()) {
                throw new IllegalArgumentException("Quarantine exige reason code sanitizado.");
            }
            this.quarantineReasonCode = quarantineReasonCode;
            return;
        }
        this.sourceKey = requirePositiveInteger(sourceKey, "A chave raiz é obrigatória.");
        this.rootFields = requiredMap(rootFields, "Os campos da raiz são obrigatórios.");
        this.metrics = requiredMap(metrics, "As métricas são obrigatórias.");
        this.payloadJson = requiredJson(payloadJson, "O payload é obrigatório.");
        this.fieldPresenceJson = requiredJson(fieldPresenceJson, "A presença é obrigatória.");
        this.relationCandidatesJson =
                requiredJson(relationCandidatesJson, "A evidência relacional é obrigatória.");
        this.freshnessAtUtc =
                Objects.requireNonNull(freshnessAtUtc, "O frescor UTC é obrigatório.");
        this.freshnessOrigin =
                Objects.requireNonNull(freshnessOrigin, "A origem do frescor é obrigatória.");
        this.competence = Objects.requireNonNull(competence, "A competência é obrigatória.");
        this.pickSourceKey =
                Optional.ofNullable(pickSourceKey)
                        .map(key -> requirePositiveInteger(key, "A chave de pick é inválida."));
        this.mdfe = Optional.ofNullable(mdfe);
        if (quarantineReasonCode != null) {
            throw new IllegalArgumentException("Registro válido não aceita motivo de quarantine.");
        }
        this.quarantineReasonCode = null;
    }

    public static ManifestoStageRecord valid(
            final int inputOrdinal,
            final ScopedSourceIdentity.SourceKey sourceKey,
            final Map<String, ManifestoFieldValue> rootFields,
            final Map<ManifestoMetric, ManifestoMetricValue> metrics,
            final String payloadJson,
            final String fieldPresenceJson,
            final String relationCandidatesJson,
            final Instant freshnessAtUtc,
            final ManifestoFreshnessOrigin freshnessOrigin,
            final ManifestoFieldValue competence,
            final ScopedSourceIdentity.SourceKey pickSourceKey,
            final ManifestoMdfeObservation mdfe) {
        return new ManifestoStageRecord(
                inputOrdinal,
                sourceKey,
                rootFields,
                metrics,
                payloadJson,
                fieldPresenceJson,
                relationCandidatesJson,
                freshnessAtUtc,
                freshnessOrigin,
                competence,
                pickSourceKey,
                mdfe,
                ManifestoStageDisposition.VALID,
                null);
    }

    public static ManifestoStageRecord quarantine(
            final int inputOrdinal,
            final ScopedSourceIdentity.SourceKey sourceKey,
            final String quarantineReasonCode) {
        return new ManifestoStageRecord(
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
                null,
                ManifestoStageDisposition.QUARANTINE,
                quarantineReasonCode);
    }

    public int inputOrdinal() {
        return inputOrdinal;
    }

    public ScopedSourceIdentity.SourceKey sourceKey() {
        return sourceKey;
    }

    public Map<String, ManifestoFieldValue> rootFields() {
        return rootFields;
    }

    public Map<ManifestoMetric, ManifestoMetricValue> metrics() {
        return metrics;
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

    public Instant freshnessAtUtc() {
        return freshnessAtUtc;
    }

    public ManifestoFreshnessOrigin freshnessOrigin() {
        return freshnessOrigin;
    }

    public ManifestoFieldValue competence() {
        return competence;
    }

    public Optional<ScopedSourceIdentity.SourceKey> pickSourceKey() {
        return pickSourceKey;
    }

    public Optional<ManifestoMdfeObservation> mdfe() {
        return mdfe;
    }

    public ManifestoStageDisposition disposition() {
        return disposition;
    }

    public String quarantineReasonCode() {
        return quarantineReasonCode;
    }

    @Override
    public String toString() {
        return "ManifestoStageRecord[inputOrdinal="
                + inputOrdinal
                + ", disposition="
                + disposition
                + ", sourceKey=<redacted>, payload=<redacted>]";
    }

    private static ScopedSourceIdentity.SourceKey requirePositiveInteger(
            final ScopedSourceIdentity.SourceKey sourceKey, final String message) {
        final ScopedSourceIdentity.SourceKey value = Objects.requireNonNull(sourceKey, message);
        if (value.wireType() != ScopedSourceIdentity.WireType.INTEGER
                || !value.storageValue().startsWith("INTEGER:")
                || new java.math.BigInteger(value.storageValue().substring(8)).signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static <K, V> Map<K, V> requiredMap(final Map<K, V> values, final String message) {
        Objects.requireNonNull(values, message);
        final Map<K, V> copy = new LinkedHashMap<>();
        values.forEach(
                (key, value) ->
                        copy.put(
                                Objects.requireNonNull(key, message),
                                Objects.requireNonNull(value, message)));
        return Map.copyOf(copy);
    }

    private static String requiredJson(final String value, final String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
