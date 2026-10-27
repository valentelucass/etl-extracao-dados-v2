package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Descriptive offline-core plan. No constructor, JSON or result here grants runtime authority. */
public record ObservedExpansionCapturePlan(
        UUID captureExecutionId,
        ObservedExpansionCaptureTemplate template,
        String sourceInstance,
        String tenantScope,
        BusinessDateRange businessWindow,
        ZoneId sourceZone,
        DataExportTransport transport,
        OrderBy orderBy,
        Envelope envelope,
        int per,
        ObservedExpansionCaptureLimits limits) {
    public static final String BASELINE_SHA256 =
            "219D408FE35E4A95A36F6FA7B740000A8EDE640676AA47C3111EC34FC86E4AAB";

    public enum Envelope {
        ROOT_ARRAY,
        DATA_OBJECT
    }

    public record OrderBy(List<String> expressions) {
        public OrderBy {
            Objects.requireNonNull(expressions);
            final int count = expressions.size();
            if (count < 0 || count > 1) {
                throw new IllegalArgumentException("OBS_CAPTURE_ORDER");
            }
            final var boundedCopy = new ArrayList<String>(count);
            for (int index = 0; index < count; index++) {
                final String expression = Objects.requireNonNull(expressions.get(index));
                if (expression.isBlank() || expression.length() > 80) {
                    throw new IllegalArgumentException("OBS_CAPTURE_ORDER");
                }
                boundedCopy.add(expression);
            }
            expressions = List.copyOf(boundedCopy);
        }

        public static OrderBy absent() {
            return new OrderBy(List.of());
        }

        public static OrderBy explicit(final String expression) {
            return new OrderBy(List.of(Objects.requireNonNull(expression)));
        }

        public boolean present() {
            return !expressions.isEmpty();
        }
    }

    public ObservedExpansionCapturePlan {
        Objects.requireNonNull(captureExecutionId);
        Objects.requireNonNull(template);
        sourceInstance = exactScope(sourceInstance);
        tenantScope = exactScope(tenantScope);
        Objects.requireNonNull(businessWindow);
        Objects.requireNonNull(sourceZone);
        Objects.requireNonNull(transport);
        Objects.requireNonNull(orderBy);
        Objects.requireNonNull(envelope);
        Objects.requireNonNull(limits);
        if (transport != DataExportTransport.GET_WITH_BODY
                || per < 1
                || per > 100
                || (orderBy.present()
                        && !orderBy.expressions()
                                .equals(List.of(template.knownOrderExpression())))) {
            throw new IllegalArgumentException("OBS_CAPTURE_REQUEST_POLICY");
        }
    }

    private static String exactScope(final String value) {
        if (value == null
                || value.isBlank()
                || value.length() > 128
                || !value.equals(value.strip())
                || value.startsWith("SYNTHETIC_")
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("OBS_CAPTURE_SCOPE");
        }
        return value;
    }

    public String sourceKind() {
        return "DATA_EXPORT";
    }

    /** Explicitly a missing integration gate, never an admission result. */
    public String sourceSchemaValidation() {
        return "NOT_PERFORMED_BY_CORE";
    }

    public ObservedExpansionCaptureRequest request(final int page) {
        return new ObservedExpansionCaptureRequest(this, page);
    }

    @Override
    public String toString() {
        return "ObservedExpansionCapturePlan[id="
                + captureExecutionId
                + ",template="
                + template.templateId()
                + ",schema=NOT_PERFORMED_BY_CORE]";
    }
}
