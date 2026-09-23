package br.com.esl.etl.v2.modulos.faturasporcliente.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreshness;
import java.util.Locale;

/** FAT-02/05/06/07 and the laboratory resolution of the MAT-04/PUB-04 placeholder conflict. */
public final class FaturaClienteRules {
    private FaturaClienteRules() {}

    public static ExpansionFreshness freshness(final FaturaClienteObservation row) {
        return ExpansionFreshness.maximum(
                ExpansionFreshness.civilEnd(row.fitAntIlsAtnTransactionDate().value()),
                ExpansionFreshness.civilEnd(row.fitAntIlsDueDate().value()),
                ExpansionFreshness.civilEnd(row.fitAntIssueDate().value()),
                ExpansionFreshness.civilEnd(row.fitAntIlsOriginalDueDate().value()),
                ExpansionFreshness.instant(row.fitFheCteIssuedAt().value()));
    }

    public static boolean realDocument(final String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        final String value = raw.trim().toLowerCase(Locale.ROOT);
        return !value.equals("faturado") && !value.equals("aguardando faturamento");
    }

    public static String cnpj(final String raw) {
        if (raw == null) {
            return null;
        }
        final String digits = raw.replaceAll("[^0-9]", "");
        return digits.length() == 14 ? digits : null;
    }

    public static String freightType(final String raw) {
        return raw == null ? null : raw.replace("Freight::", "").trim();
    }

    public enum FiscalPolicy {
        UNRESOLVED,
        SYNTHETIC_CTE,
        SYNTHETIC_NFSE
    }
}
