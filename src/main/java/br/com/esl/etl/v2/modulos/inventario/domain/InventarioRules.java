package br.com.esl.etl.v2.modulos.inventario.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreshness;
import java.text.Normalizer;
import java.util.Locale;

/** INV-02 priority is shared by dedupe and application; INV-03 combines proof by SQL OR. */
public final class InventarioRules {
    private InventarioRules() {}

    public static ExpansionFreshness freshness(final InventarioObservation row) {
        final var performance = row.cnrCSFitDpnPerformanceFinishedAt().value();
        final var finished = row.finishedAt().value();
        return ExpansionFreshness.instant(
                performance != null
                        ? performance
                        : finished != null ? finished : row.startedAt().value());
    }

    public static boolean proofAttached(final String raw) {
        if (raw == null) {
            return false;
        }
        final String text =
                Normalizer.normalize(raw, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT);
        final int proof = text.indexOf("comprovante");
        if (proof < 0) {
            return false;
        }
        final int delivery = text.indexOf("entrega", proof + 11);
        return delivery >= 0 && text.indexOf("anexado", delivery + 7) >= 0;
    }
}
