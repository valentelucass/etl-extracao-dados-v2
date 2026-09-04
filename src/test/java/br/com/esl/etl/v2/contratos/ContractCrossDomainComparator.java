package br.com.esl.etl.v2.contratos;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Comparações transitórias em memória entre as fontes de contrato.
 *
 * <p>Os resultados guardam exclusivamente contagens e flags. Relação de negócio, receita e
 * equivalência financeira permanecem fechadas até que o dono aprove campos e a execução remota
 * prove a janela autorizada.
 */
public final class ContractCrossDomainComparator {

    private static final String FRETE_COLETA_CANDIDATE_FIELD = "fit_p_m_pck_sequence_code";
    private static final String COLETA_SEQUENCE_FIELD = "sequence_code";

    private ContractCrossDomainComparator() {}

    public static ContractFreteColetaRelationResult compareFreteColeta(
            final List<JsonNode> dataExportFretes,
            final List<JsonNode> dataExportColetas,
            final List<ContractGraphQlFreteIdentity> graphQlFretes,
            final List<ContractGraphQlColetaIdentity> graphQlColetas) {
        Objects.requireNonNull(dataExportFretes, "Os Fretes Data Export são obrigatórios.");
        Objects.requireNonNull(dataExportColetas, "As Coletas Data Export são obrigatórias.");
        Objects.requireNonNull(graphQlFretes, "Os Fretes GraphQL são obrigatórios.");
        Objects.requireNonNull(graphQlColetas, "As Coletas GraphQL são obrigatórias.");

        final ScalarSet candidateKeys = scalarSet(dataExportFretes, FRETE_COLETA_CANDIDATE_FIELD);
        final ScalarSet coletaSequenceCodes = scalarSet(dataExportColetas, COLETA_SEQUENCE_FIELD);
        final boolean candidateKeysResolveToColetas =
                candidateKeys.complete()
                        && !candidateKeys.values().isEmpty()
                        && coletaSequenceCodes.complete()
                        && coletaSequenceCodes.values().containsAll(candidateKeys.values());

        final ScalarSet graphQlFretePickItems = graphQlFretePickItems(graphQlFretes);
        final ScalarSet graphQlColetaPickItems = graphQlColetaPickItems(graphQlColetas);
        final boolean graphQlPickItemBaselineResolves =
                graphQlFretePickItems.complete()
                        && !graphQlFretePickItems.values().isEmpty()
                        && graphQlColetaPickItems.complete()
                        && graphQlColetaPickItems
                                .values()
                                .containsAll(graphQlFretePickItems.values());

        // A cardinalidade e a receita exigem mapeamento de campos e janela fechada aprovados.
        return new ContractFreteColetaRelationResult(
                dataExportFretes.size(),
                dataExportColetas.size(),
                candidateKeys.values().size(),
                candidateKeys.complete(),
                candidateKeysResolveToColetas,
                graphQlFretePickItems.complete() && graphQlColetaPickItems.complete(),
                graphQlPickItemBaselineResolves,
                false,
                false,
                false,
                false);
    }

    public static ContractCteInvoiceRelationResult compareCteInvoice(
            final List<JsonNode> dataExportFretes,
            final String dataExportFreteCteField,
            final List<JsonNode> invoiceRows,
            final String invoiceCteField,
            final List<ContractGraphQlFreteIdentity> graphQlFretes) {
        Objects.requireNonNull(dataExportFretes, "Os Fretes Data Export são obrigatórios.");
        Objects.requireNonNull(invoiceRows, "As Faturas Data Export são obrigatórias.");
        Objects.requireNonNull(graphQlFretes, "Os Fretes GraphQL são obrigatórios.");
        final String freteField =
                ContractEvidenceSanitizer.fieldName(
                        dataExportFreteCteField, "O campo CT-e de Frete é obrigatório.");
        final String invoiceField =
                ContractEvidenceSanitizer.fieldName(
                        invoiceCteField, "O campo CT-e de Fatura é obrigatório.");
        final ScalarSet dataExportCtes = normalizedCteSet(dataExportFretes, freteField);
        final ScalarSet invoiceCtes = normalizedCteSet(invoiceRows, invoiceField);
        final ScalarSet graphQlCtes = graphQlCteSet(graphQlFretes);
        final boolean dataExportFreteCtesMatchGraphQl =
                dataExportCtes.complete()
                        && !dataExportCtes.values().isEmpty()
                        && graphQlCtes.complete()
                        && dataExportCtes.values().equals(graphQlCtes.values());
        final boolean invoiceCtesCoverDataExportFretes =
                invoiceCtes.complete()
                        && !invoiceCtes.values().isEmpty()
                        && dataExportCtes.complete()
                        && invoiceCtes.values().containsAll(dataExportCtes.values());
        return new ContractCteInvoiceRelationResult(
                dataExportFretes.size(),
                invoiceRows.size(),
                graphQlFretes.size(),
                dataExportCtes.complete(),
                invoiceCtes.complete(),
                graphQlCtes.complete(),
                dataExportFreteCtesMatchGraphQl,
                invoiceCtesCoverDataExportFretes,
                dataExportFreteCtesMatchGraphQl && invoiceCtesCoverDataExportFretes);
    }

    private static ScalarSet scalarSet(final List<JsonNode> rows, final String fieldName) {
        final Set<String> values = new HashSet<>();
        boolean complete = !rows.isEmpty();
        for (final JsonNode row : rows) {
            final Optional<String> value = scalar(row, fieldName);
            if (value.isEmpty()) {
                complete = false;
            } else {
                values.add(value.orElseThrow());
            }
        }
        return new ScalarSet(complete, Set.copyOf(values));
    }

    private static ScalarSet normalizedCteSet(final List<JsonNode> rows, final String fieldName) {
        final Set<String> values = new HashSet<>();
        boolean complete = !rows.isEmpty();
        for (final JsonNode row : rows) {
            final Optional<String> value =
                    scalar(row, fieldName).flatMap(ContractCrossDomainComparator::normalizeCte);
            if (value.isEmpty()) {
                complete = false;
            } else {
                values.add(value.orElseThrow());
            }
        }
        return new ScalarSet(complete, Set.copyOf(values));
    }

    private static ScalarSet graphQlFretePickItems(
            final List<ContractGraphQlFreteIdentity> graphQlFretes) {
        final Set<String> values = new HashSet<>();
        boolean complete = !graphQlFretes.isEmpty();
        for (final ContractGraphQlFreteIdentity frete : graphQlFretes) {
            if (frete == null || frete.pickItemId().isEmpty()) {
                complete = false;
            } else {
                values.add(frete.pickItemId().orElseThrow().trim());
            }
        }
        return new ScalarSet(complete, Set.copyOf(values));
    }

    private static ScalarSet graphQlColetaPickItems(
            final List<ContractGraphQlColetaIdentity> graphQlColetas) {
        final Set<String> values = new HashSet<>();
        boolean complete = !graphQlColetas.isEmpty();
        for (final ContractGraphQlColetaIdentity coleta : graphQlColetas) {
            if (coleta == null || coleta.pickItemIds().isEmpty()) {
                complete = false;
                continue;
            }
            for (final Optional<String> pickItemId : coleta.pickItemIds()) {
                if (pickItemId == null
                        || pickItemId.isEmpty()
                        || pickItemId.orElseThrow().isBlank()) {
                    complete = false;
                } else {
                    values.add(pickItemId.orElseThrow().trim());
                }
            }
        }
        return new ScalarSet(complete, Set.copyOf(values));
    }

    private static ScalarSet graphQlCteSet(final List<ContractGraphQlFreteIdentity> graphQlFretes) {
        final Set<String> values = new HashSet<>();
        boolean complete = !graphQlFretes.isEmpty();
        for (final ContractGraphQlFreteIdentity frete : graphQlFretes) {
            final Optional<String> cte =
                    frete == null
                            ? Optional.empty()
                            : frete.cteKey().flatMap(ContractCrossDomainComparator::normalizeCte);
            if (cte.isEmpty()) {
                complete = false;
            } else {
                values.add(cte.orElseThrow());
            }
        }
        return new ScalarSet(complete, Set.copyOf(values));
    }

    private static Optional<String> scalar(final JsonNode row, final String fieldName) {
        if (row == null || !row.isObject()) {
            return Optional.empty();
        }
        final JsonNode value = row.get(fieldName);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return Optional.empty();
        }
        final String text = value.asText();
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.trim());
    }

    private static Optional<String> normalizeCte(final String value) {
        final String normalized = value.replaceAll("[^0-9]", "");
        return normalized.matches("[0-9]{44}") ? Optional.of(normalized) : Optional.empty();
    }

    private record ScalarSet(boolean complete, Set<String> values) {}
}
