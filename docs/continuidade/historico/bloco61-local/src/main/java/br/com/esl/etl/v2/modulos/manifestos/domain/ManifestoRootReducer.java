package br.com.esl.etl.v2.modulos.manifestos.domain;

import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Reducers puros de Manifestos: coorte máxima, sem arrival/order/hash como desempate. */
public final class ManifestoRootReducer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> STATUS_PRECEDENCE =
            List.of("closed", "in_transit", "pending");

    public ManifestoReductionResult reduce(final Iterable<ManifestoStageRecord> observations) {
        Objects.requireNonNull(observations, "As observações são obrigatórias.");
        final List<ManifestoStageRecord> records = new ArrayList<>();
        observations.forEach(
                record -> {
                    final ManifestoStageRecord value =
                            Objects.requireNonNull(record, "A coorte não aceita observação nula.");
                    if (value.disposition() != ManifestoStageDisposition.VALID) {
                        throw new IllegalArgumentException(
                                "A redução não aceita observação já quarentenada.");
                    }
                    records.add(value);
                });
        if (records.isEmpty()) {
            throw new IllegalArgumentException("A redução exige ao menos uma observação válida.");
        }
        final ScopedSourceIdentity.SourceKey rootKey = records.get(0).sourceKey();
        if (records.stream().anyMatch(record -> !rootKey.equals(record.sourceKey()))) {
            throw new IllegalArgumentException(
                    "Um reducer trata somente uma raiz escopada por vez.");
        }
        final Instant maximumFreshness =
                records.stream()
                        .map(ManifestoStageRecord::freshnessAtUtc)
                        .max(Comparator.naturalOrder())
                        .orElseThrow();
        final List<ManifestoStageRecord> cohort =
                records.stream()
                        .filter(record -> record.freshnessAtUtc().equals(maximumFreshness))
                        .toList();
        final ManifestoFreshnessOrigin origin =
                cohort.stream()
                        .map(ManifestoStageRecord::freshnessOrigin)
                        .min(Comparator.naturalOrder())
                        .orElseThrow();
        final Map<String, ManifestoFieldValue> rootFields = new TreeMap<>();
        final Set<String> names = new HashSet<>();
        cohort.forEach(record -> names.addAll(record.rootFields().keySet()));
        for (final String name : names) {
            rootFields.put(
                    name,
                    name.equals("status")
                            ? reduceStatus(valuesFor(name, cohort))
                            : reduceDefault(valuesFor(name, cohort)));
        }
        final Map<ManifestoMetric, ManifestoMetricValue> metrics =
                new EnumMap<>(ManifestoMetric.class);
        for (final ManifestoMetric metric : ManifestoMetric.values()) {
            metrics.put(metric, reduceMetric(metric, cohort));
        }
        final ManifestoFieldValue competence =
                reduceDefault(cohort.stream().map(ManifestoStageRecord::competence).toList());
        final List<String> rootConflicts = new ArrayList<>();
        rootFields.forEach(
                (name, value) -> {
                    if (value == null) {
                        rootConflicts.add(name);
                    }
                });
        metrics.forEach(
                (metric, value) -> {
                    if (value == null) {
                        rootConflicts.add(metric.sourceField());
                    }
                });
        if (competence == null) {
            rootConflicts.add("competence");
        }
        final Children children = reduceChildren(cohort);
        return new ManifestoReductionResult(
                rootKey,
                maximumFreshness,
                origin,
                withoutConflicts(rootFields),
                withoutMetricConflicts(metrics),
                competence == null ? ManifestoFieldValue.absent() : competence,
                children.picks(),
                children.mdfes(),
                children.quarantines(),
                rootConflicts.isEmpty() ? null : "EQUAL_FRESHNESS_CONFLICT");
    }

    private static List<ManifestoFieldValue> valuesFor(
            final String name, final List<ManifestoStageRecord> cohort) {
        return cohort.stream()
                .map(record -> record.rootFields().getOrDefault(name, ManifestoFieldValue.absent()))
                .toList();
    }

    /** MAN-04: ausência não preenche valor antigo; null e value no mesmo empate conflitam. */
    private static ManifestoFieldValue reduceDefault(final List<ManifestoFieldValue> values) {
        final Set<String> canonicalValues = new HashSet<>();
        boolean explicitNull = false;
        for (final ManifestoFieldValue value : values) {
            if (value.presence() == ManifestoAttributePresence.NULL) {
                explicitNull = true;
            } else if (value.presence() == ManifestoAttributePresence.VALUE) {
                canonicalValues.add(value.canonicalJson());
            }
        }
        if (canonicalValues.size() > 1 || (explicitNull && !canonicalValues.isEmpty())) {
            return null;
        }
        if (canonicalValues.size() == 1) {
            return ManifestoFieldValue.value(canonicalValues.iterator().next());
        }
        return explicitNull ? ManifestoFieldValue.nullValue() : ManifestoFieldValue.absent();
    }

    /** Status conhecido usa apenas a precedência aprovada; desconhecido nunca recebe lifecycle. */
    private static ManifestoFieldValue reduceStatus(final List<ManifestoFieldValue> values) {
        final Set<String> statusValues = new HashSet<>();
        boolean explicitNull = false;
        for (final ManifestoFieldValue value : values) {
            if (value.presence() == ManifestoAttributePresence.NULL) {
                explicitNull = true;
            } else if (value.presence() == ManifestoAttributePresence.VALUE) {
                statusValues.add(textValue(value.canonicalJson()));
            }
        }
        if (explicitNull && !statusValues.isEmpty()) {
            return null;
        }
        if (statusValues.isEmpty()) {
            return explicitNull ? ManifestoFieldValue.nullValue() : ManifestoFieldValue.absent();
        }
        final Set<String> unknown = new HashSet<>(statusValues);
        unknown.removeAll(STATUS_PRECEDENCE);
        if (!unknown.isEmpty()) {
            return unknown.size() == 1 && statusValues.size() == 1
                    ? ManifestoFieldValue.value(jsonText(unknown.iterator().next()))
                    : null;
        }
        final String winner =
                STATUS_PRECEDENCE.stream().filter(statusValues::contains).findFirst().orElseThrow();
        return ManifestoFieldValue.value(jsonText(winner));
    }

    /** MAN-07: null complementa um único valor; zero é valor; nunca há SUM/MAX. */
    private static ManifestoMetricValue reduceMetric(
            final ManifestoMetric metric, final List<ManifestoStageRecord> cohort) {
        final List<ManifestoMetricValue> values =
                cohort.stream()
                        .map(record -> record.metrics().get(metric))
                        .filter(Objects::nonNull)
                        .toList();
        final Set<BigDecimal> distinct = new java.util.TreeSet<>(BigDecimal::compareTo);
        boolean explicitNull = false;
        for (final ManifestoMetricValue value : values) {
            if (value.presence() == ManifestoAttributePresence.NULL) {
                explicitNull = true;
            } else if (value.presence() == ManifestoAttributePresence.VALUE) {
                distinct.add(value.value());
            }
        }
        if (distinct.size() > 1) {
            return null;
        }
        if (distinct.size() == 1) {
            return ManifestoMetricValue.value(distinct.iterator().next());
        }
        return explicitNull ? ManifestoMetricValue.nullValue() : ManifestoMetricValue.absent();
    }

    private static Children reduceChildren(final List<ManifestoStageRecord> cohort) {
        final Map<String, ScopedSourceIdentity.SourceKey> picks = new TreeMap<>();
        final Map<String, ManifestoMdfeObservation> mdfes = new TreeMap<>();
        final Set<String> conflictingMdfeKeys = new HashSet<>();
        final List<String> quarantines = new ArrayList<>();
        for (final ManifestoStageRecord record : cohort) {
            record.pickSourceKey().ifPresent(key -> picks.putIfAbsent(key.storageValue(), key));
            record.mdfe()
                    .ifPresent(
                            mdfe -> {
                                if (conflictingMdfeKeys.contains(mdfe.key())) {
                                    return;
                                }
                                final ManifestoMdfeObservation previous =
                                        mdfes.putIfAbsent(mdfe.key(), mdfe);
                                if (previous != null && !previous.number().equals(mdfe.number())) {
                                    mdfes.remove(mdfe.key());
                                    conflictingMdfeKeys.add(mdfe.key());
                                    quarantines.add("MDFE_ATTRIBUTE_CONFLICT");
                                }
                            });
        }
        return new Children(List.copyOf(picks.values()), List.copyOf(mdfes.values()), quarantines);
    }

    private static Map<String, ManifestoFieldValue> withoutConflicts(
            final Map<String, ManifestoFieldValue> fields) {
        final Map<String, ManifestoFieldValue> result = new HashMap<>();
        fields.forEach(
                (name, value) ->
                        result.put(name, value == null ? ManifestoFieldValue.absent() : value));
        return result;
    }

    private static Map<ManifestoMetric, ManifestoMetricValue> withoutMetricConflicts(
            final Map<ManifestoMetric, ManifestoMetricValue> metrics) {
        final Map<ManifestoMetric, ManifestoMetricValue> result =
                new EnumMap<>(ManifestoMetric.class);
        metrics.forEach(
                (metric, value) ->
                        result.put(metric, value == null ? ManifestoMetricValue.absent() : value));
        return result;
    }

    private static String textValue(final String canonicalJson) {
        try {
            final JsonNode node = JSON.readTree(canonicalJson);
            if (node == null || !node.isTextual()) {
                throw new IllegalArgumentException("Status canônico não é texto.");
            }
            return node.textValue();
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException("Status canônico não pode ser lido.", exception);
        }
    }

    private static String jsonText(final String value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível canonicalizar status.", exception);
        }
    }

    private record Children(
            List<ScopedSourceIdentity.SourceKey> picks,
            List<ManifestoMdfeObservation> mdfes,
            List<String> quarantines) {}
}
