package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Comparadores em memória para janelas fechadas; nunca persistem ou exibem chaves de negócio. */
public final class ContractParityComparator {

    private ContractParityComparator() {}

    public static ContractIdentityParityResult compareColetas(
            final List<JsonNode> dataExportRows,
            final List<ContractGraphQlColetaIdentity> graphQlRows) {
        // O gate V2-004d une sequence_code (6908) a sequenceCode (GraphQL) antes de comparar IDs.
        return compareIdentity(
                dataExportRows,
                graphQlRows,
                DataExportTemplate.COLETAS,
                "sequence_code",
                ContractGraphQlColetaIdentity::sequenceCode,
                ContractGraphQlColetaIdentity::sourceId);
    }

    public static ContractIdentityParityResult compareFretes(
            final List<JsonNode> dataExportRows,
            final List<ContractGraphQlFreteIdentity> graphQlRows) {
        return compareIdentity(
                dataExportRows,
                graphQlRows,
                DataExportTemplate.FRETES,
                "corporation_sequence_number",
                ContractGraphQlFreteIdentity::corporationSequenceNumber,
                ContractGraphQlFreteIdentity::sourceId);
    }

    /** Compatibilidade para testes sintéticos que usam o campo de entidade {@code id}. */
    public static ContractPaginationResult compareRepeatedPagination(
            final List<ContractObservedPage> firstRunPages,
            final List<ContractObservedPage> secondRunPages,
            final String naturalKeyField) {
        return compareRepeatedPagination(firstRunPages, secondRunPages, "id", naturalKeyField);
    }

    public static ContractPaginationResult compareRepeatedPagination(
            final List<ContractObservedPage> firstRunPages,
            final List<ContractObservedPage> secondRunPages,
            final DataExportTemplate template,
            final String naturalKeyField) {
        Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        return compareRepeatedPagination(
                firstRunPages, secondRunPages, template.paginationEntityField(), naturalKeyField);
    }

    private static ContractPaginationResult compareRepeatedPagination(
            final List<ContractObservedPage> firstRunPages,
            final List<ContractObservedPage> secondRunPages,
            final String entityIdField,
            final String naturalKeyField) {
        final PaginationRun firstRun =
                analyzePagination(firstRunPages, entityIdField, naturalKeyField);
        final PaginationRun secondRun =
                analyzePagination(secondRunPages, entityIdField, naturalKeyField);
        return new ContractPaginationResult(
                firstRun.physicalRecordCount(),
                secondRun.physicalRecordCount(),
                firstRun.entityCount(),
                secondRun.entityCount(),
                firstRun.pageCount(),
                secondRun.pageCount(),
                firstRun.nonEmptyPageCount(),
                secondRun.nonEmptyPageCount(),
                firstRun.hasTerminalEmptyPage(),
                secondRun.hasTerminalEmptyPage(),
                firstRun.hasNoIntermediateEmptyPage(),
                secondRun.hasNoIntermediateEmptyPage(),
                firstRun.pageNumbersConsecutive(),
                secondRun.pageNumbersConsecutive(),
                firstRun.entityIdsVerifiable(),
                secondRun.entityIdsVerifiable(),
                firstRun.entityCountsWithinPageSize(),
                secondRun.entityCountsWithinPageSize(),
                firstRun.entityIdsUniqueAcrossPages(),
                secondRun.entityIdsUniqueAcrossPages(),
                firstRun.keysComplete(),
                secondRun.keysComplete(),
                firstRun.keysUnique(),
                secondRun.keysUnique(),
                firstRun.entityCount() == secondRun.entityCount(),
                firstRun.orderedKeys().equals(secondRun.orderedKeys()),
                firstRun.keySet().equals(secondRun.keySet()));
    }

    private static <T> ContractIdentityParityResult compareIdentity(
            final List<JsonNode> dataExportRows,
            final List<T> graphQlRows,
            final DataExportTemplate template,
            final String dataExportNaturalKeyField,
            final Function<T, Optional<String>> graphQlKey,
            final Function<T, Optional<String>> graphQlId) {
        Objects.requireNonNull(dataExportRows, "As linhas Data Export são obrigatórias.");
        Objects.requireNonNull(graphQlRows, "As linhas GraphQL são obrigatórias.");
        final IdentityIndex dataExport =
                indexDataExport(dataExportRows, template, dataExportNaturalKeyField);
        final IdentityIndex graphQl = indexGraphQl(graphQlRows, graphQlKey, graphQlId);
        final boolean naturalKeySetsEqual =
                dataExport.idsByKey().keySet().equals(graphQl.idsByKey().keySet());
        final boolean naturalKeyToGraphQlSourceIdOneToOne =
                dataExport.recordCount() > 0
                        && graphQl.recordCount() > 0
                        && dataExport.keysComplete()
                        && graphQl.keysComplete()
                        && dataExport.keysUnique()
                        && graphQl.keysUnique()
                        && naturalKeySetsEqual
                        && graphQl.idsComplete()
                        && graphQl.idsUnique();
        final boolean canCompareIds =
                naturalKeyToGraphQlSourceIdOneToOne
                        && dataExport.idsComplete()
                        && dataExport.idsUnique();
        final boolean sourceIdsEqual =
                canCompareIds && dataExport.idsByKey().equals(graphQl.idsByKey());
        return new ContractIdentityParityResult(
                dataExport.recordCount(),
                graphQl.recordCount(),
                dataExport.recordCount() == graphQl.recordCount(),
                dataExport.keysComplete(),
                graphQl.keysComplete(),
                dataExport.keysUnique(),
                graphQl.keysUnique(),
                dataExport.idsComplete(),
                graphQl.idsComplete(),
                dataExport.idsUnique(),
                graphQl.idsUnique(),
                naturalKeySetsEqual,
                naturalKeyToGraphQlSourceIdOneToOne,
                canCompareIds,
                sourceIdsEqual);
    }

    private static IdentityIndex indexDataExport(
            final List<JsonNode> rows,
            final DataExportTemplate template,
            final String naturalKeyField) {
        final ContractDataExportEntityIndex entities =
                ContractDataExportEntityIndex.forTemplate(template, rows);
        final Map<String, Optional<String>> idsByKey = new HashMap<>();
        final Set<String> observedCanonicalIds = new HashSet<>();
        boolean keysComplete = entities.entityIdsVerifiable();
        boolean keysUnique = entities.entityIdsVerifiable();
        boolean idsComplete = entities.entityIdsVerifiable();
        boolean idsUnique = entities.entityIdsVerifiable();
        for (final ContractDataExportEntityIndex.Entity entity : entities.entities()) {
            final Optional<String> key = entity.consistentScalar(naturalKeyField);
            final Optional<String> id = normalize(entity.id().asText());
            if (key.isEmpty()) {
                keysComplete = false;
                continue;
            }
            if (id.isEmpty()) {
                idsComplete = false;
            } else if (!observedCanonicalIds.add(id.orElseThrow())) {
                idsUnique = false;
            }
            if (idsByKey.putIfAbsent(key.orElseThrow(), id) != null) {
                keysUnique = false;
            }
        }
        return new IdentityIndex(
                entities.entityCount(),
                keysComplete,
                keysUnique,
                idsComplete,
                idsUnique,
                Map.copyOf(idsByKey));
    }

    private static <T> IdentityIndex indexGraphQl(
            final List<T> rows,
            final Function<T, Optional<String>> keyReader,
            final Function<T, Optional<String>> idReader) {
        final Map<String, Optional<String>> idsByKey = new HashMap<>();
        final Set<String> observedIds = new HashSet<>();
        boolean keysComplete = true;
        boolean keysUnique = true;
        boolean idsComplete = true;
        boolean idsUnique = true;
        for (final T row : rows) {
            if (row == null) {
                keysComplete = false;
                idsComplete = false;
                continue;
            }
            final Optional<String> key = normalize(keyReader.apply(row));
            final Optional<String> id = normalize(idReader.apply(row));
            if (key.isEmpty()) {
                keysComplete = false;
                continue;
            }
            if (id.isEmpty()) {
                idsComplete = false;
            } else if (!observedIds.add(id.orElseThrow())) {
                idsUnique = false;
            }
            if (idsByKey.putIfAbsent(key.orElseThrow(), id) != null) {
                keysUnique = false;
            }
        }
        return new IdentityIndex(
                rows.size(),
                keysComplete,
                keysUnique,
                idsComplete,
                idsUnique,
                Map.copyOf(idsByKey));
    }

    private static PaginationRun analyzePagination(
            final List<ContractObservedPage> pages,
            final String entityIdField,
            final String naturalKeyField) {
        Objects.requireNonNull(pages, "As páginas são obrigatórias.");
        if (entityIdField == null || entityIdField.isBlank()) {
            throw new IllegalArgumentException("O campo de entidade é obrigatório.");
        }
        if (naturalKeyField == null || naturalKeyField.isBlank()) {
            throw new IllegalArgumentException("O campo de ordenação é obrigatório.");
        }
        final List<String> orderedKeys = new ArrayList<>();
        final Set<String> uniqueKeys = new HashSet<>();
        final Map<JsonNode, Optional<String>> keysByEntityId = new LinkedHashMap<>();
        boolean entityIdsVerifiable = true;
        boolean entityCountsWithinPageSize = true;
        boolean entityIdsUniqueAcrossPages = true;
        boolean keysComplete = true;
        boolean keysUnique = true;
        boolean pageNumbersConsecutive = true;
        boolean hasIntermediateEmptyPage = false;
        int physicalRecordCount = 0;
        int nonEmptyPageCount = 0;
        for (int index = 0; index < pages.size(); index++) {
            final ContractObservedPage page = pages.get(index);
            Objects.requireNonNull(page, "Uma página de contrato é obrigatória.");
            if (page.pageNumber() != index + 1) {
                pageNumbersConsecutive = false;
            }
            if (page.records().isEmpty() && index < pages.size() - 1) {
                hasIntermediateEmptyPage = true;
            }
            if (!page.records().isEmpty()) {
                nonEmptyPageCount++;
            }
            physicalRecordCount += page.records().size();
            final ContractDataExportEntityIndex pageEntities =
                    ContractDataExportEntityIndex.from(page.records(), entityIdField);
            entityIdsVerifiable &= pageEntities.entityIdsVerifiable();
            entityCountsWithinPageSize &= pageEntities.entityLimitWithin(page.requestedPageSize());
            for (final ContractDataExportEntityIndex.Entity entity : pageEntities.entities()) {
                final Optional<String> key = entity.consistentScalar(naturalKeyField);
                if (keysByEntityId.containsKey(entity.id())) {
                    entityIdsUniqueAcrossPages = false;
                    if (!Objects.equals(keysByEntityId.get(entity.id()), key)) {
                        keysComplete = false;
                    }
                    continue;
                }
                keysByEntityId.put(entity.id(), key);
                if (key.isEmpty()) {
                    keysComplete = false;
                    continue;
                }
                final String value = key.orElseThrow();
                orderedKeys.add(value);
                if (!uniqueKeys.add(value)) {
                    keysUnique = false;
                }
            }
        }
        final boolean hasTerminalEmptyPage =
                !pages.isEmpty() && pages.get(pages.size() - 1).records().isEmpty();
        return new PaginationRun(
                physicalRecordCount,
                keysByEntityId.size(),
                pages.size(),
                nonEmptyPageCount,
                hasTerminalEmptyPage,
                !hasIntermediateEmptyPage,
                pageNumbersConsecutive,
                entityIdsVerifiable,
                entityCountsWithinPageSize,
                entityIdsUniqueAcrossPages,
                keysComplete,
                keysUnique,
                List.copyOf(orderedKeys),
                Set.copyOf(uniqueKeys));
    }

    private static Optional<String> normalize(final Optional<String> value) {
        Objects.requireNonNull(value, "O valor de identidade é obrigatório.");
        return value.filter(text -> !text.isBlank()).map(String::trim);
    }

    private static Optional<String> normalize(final String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private record IdentityIndex(
            int recordCount,
            boolean keysComplete,
            boolean keysUnique,
            boolean idsComplete,
            boolean idsUnique,
            Map<String, Optional<String>> idsByKey) {}

    private record PaginationRun(
            int physicalRecordCount,
            int entityCount,
            int pageCount,
            int nonEmptyPageCount,
            boolean hasTerminalEmptyPage,
            boolean hasNoIntermediateEmptyPage,
            boolean pageNumbersConsecutive,
            boolean entityIdsVerifiable,
            boolean entityCountsWithinPageSize,
            boolean entityIdsUniqueAcrossPages,
            boolean keysComplete,
            boolean keysUnique,
            List<String> orderedKeys,
            Set<String> keySet) {}
}
