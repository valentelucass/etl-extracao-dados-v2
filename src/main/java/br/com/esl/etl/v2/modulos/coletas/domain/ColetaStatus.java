package br.com.esl.etl.v2.modulos.coletas.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Catálogo fechado {@code coletas-status-v1}, separado do valor bruto observado. */
public final class ColetaStatus {

    public static final String CATALOG_VERSION = "coletas-status-v1";

    private static final Map<String, Definition> DEFINITIONS =
            Map.of(
                    "pending", new Definition("Pendente", false),
                    "treatment", new Definition("Em tratativa", false),
                    "manifested", new Definition("Manifestada", false),
                    "in_transit", new Definition("Em trânsito", false),
                    "draft", new Definition("Rascunho", false),
                    "finished", new Definition("Finalizada", true),
                    "done", new Definition("Coletada", true),
                    "canceled", new Definition("Cancelada", true),
                    "cancelled", new Definition("Cancelada", true));

    private ColetaStatus() {}

    public static Resolved resolve(final String rawStatus, final String cancellationReason) {
        final String normalized = normalize(rawStatus);
        final Definition definition = normalized == null ? null : DEFINITIONS.get(normalized);
        if (definition == null) {
            return new Resolved(rawStatus, null, null, false, action(null, cancellationReason), 0);
        }
        return new Resolved(
                rawStatus,
                normalized,
                definition.label(),
                definition.terminal(),
                action(normalized, cancellationReason),
                definition.terminal() ? 1 : 0);
    }

    public static Optional<String> normalizeKnown(final String rawStatus) {
        final String normalized = normalize(rawStatus);
        return normalized != null && DEFINITIONS.containsKey(normalized)
                ? Optional.of(normalized)
                : Optional.empty();
    }

    private static String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized =
                value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return normalized.isEmpty() ? null : normalized;
    }

    private static String action(final String knownCode, final String cancellationReason) {
        if ("finished".equals(knownCode) || "done".equals(knownCode)) {
            return "Coleta Realizada";
        }
        if (cancellationReason != null && !cancellationReason.trim().isEmpty()) {
            return cancellationReason;
        }
        if ("canceled".equals(knownCode) || "cancelled".equals(knownCode)) {
            return "Coleta cancelada";
        }
        return "Pendente";
    }

    /** Resultado de catálogo: código desconhecido não recebe equivalência inventada. */
    public record Resolved(
            String raw,
            String code,
            String label,
            boolean terminal,
            String occurrenceAction,
            int attempts) {

        @Override
        public String toString() {
            return "Resolved[raw=<redacted>, code="
                    + code
                    + ", label="
                    + label
                    + ", terminal="
                    + terminal
                    + ", occurrenceAction=<redacted>, attempts="
                    + attempts
                    + "]";
        }
    }

    private record Definition(String label, boolean terminal) {}
}
