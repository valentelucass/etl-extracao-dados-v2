package br.com.esl.etl.v2.modulos.fretes.domain;

/** Catálogo exato FRE-03; valores desconhecidos são preservados sem inferência. */
public record FreteStatusDecision(
        String raw, String code, String label, boolean terminal, boolean known) {
    public static FreteStatusDecision fromRaw(final String raw) {
        if (raw == null) {
            return new FreteStatusDecision(null, null, null, false, false);
        }
        return switch (raw) {
            case "finished" -> new FreteStatusDecision(raw, raw, "finalizado", true, true);
            case "done" -> new FreteStatusDecision(raw, raw, "finalizado", true, true);
            case "canceled" -> new FreteStatusDecision(raw, raw, "cancelada", true, true);
            case "cancelled" -> new FreteStatusDecision(raw, raw, "cancelada", true, true);
            default -> new FreteStatusDecision(raw, null, null, false, false);
        };
    }
}
