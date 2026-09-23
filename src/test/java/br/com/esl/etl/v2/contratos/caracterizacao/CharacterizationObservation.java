package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.AuthorizationState;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ChildKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.EvidenceClassification;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OracleAvailability;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OracleKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.PaginationKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Presence;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.RootCardinality;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.StatusMode;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TemporalTranslation;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TerminalCondition;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TimestampState;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TimezoneState;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.WireType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Métricas estruturais sanitizadas de uma observação; não contém registros nem chaves brutas. */
public record CharacterizationObservation(
        Entity entity,
        SourceKind sourceKind,
        OracleKind oracleKind,
        OracleAvailability oracleAvailability,
        AuthorizationState authorizationState,
        String sourceInstance,
        String tenantScope,
        String recordRoot,
        Set<String> observedPaths,
        String observedSourceKeyPath,
        boolean observedSourceKeyTypeTagged,
        Presence sourceKeyPresence,
        Set<WireType> sourceKeyWireTypes,
        boolean sourceKeyValueValid,
        int sourceKeyCount,
        int distinctSourceKeyCount,
        boolean sourceKeyCollision,
        int crossTypeLexemePairCount,
        int crossTypeTaggedDistinctPairCount,
        int physicalRowCount,
        int logicalRootCount,
        int childCount,
        int distinctChildCount,
        boolean childConflict,
        List<ChildObservation> childObservations,
        boolean physicalExpansion,
        RootCardinality rootCardinality,
        List<FieldObservation> fieldObservations,
        Set<String> observedRecordPaths,
        Set<String> observedConnectionPaths,
        Set<String> observedRootScalarPaths,
        List<FilterObservation> filterObservations,
        Set<String> observedRelationshipCandidatePaths,
        boolean relationshipInferenceApplied,
        boolean triStateTrackingEnabled,
        boolean absenceLifecycleInferenceApplied,
        PaginationObservation pagination,
        TemporalObservation temporal,
        StatusObservation status,
        ResourceUsage usage,
        EvidenceClassification evidenceClassification) {

    public CharacterizationObservation {
        entity = Objects.requireNonNull(entity, "A entidade observada é obrigatória.");
        sourceKind = Objects.requireNonNull(sourceKind, "O source kind observado é obrigatório.");
        oracleKind = Objects.requireNonNull(oracleKind, "O oráculo observado é obrigatório.");
        oracleAvailability =
                Objects.requireNonNull(
                        oracleAvailability, "A disponibilidade do oráculo é obrigatória.");
        authorizationState =
                Objects.requireNonNull(
                        authorizationState, "O contexto de autorização é obrigatório.");
        observedPaths = sortedPaths(observedPaths);
        if (observedSourceKeyPath == null
                || observedSourceKeyPath.isBlank()
                || !observedSourceKeyPath.startsWith("/")) {
            throw new IllegalArgumentException("O path observado da source key é inválido.");
        }
        sourceKeyPresence =
                Objects.requireNonNull(sourceKeyPresence, "A presença da chave é obrigatória.");
        sourceKeyWireTypes = immutableWireTypes(sourceKeyWireTypes);
        nonNegative(sourceKeyCount, "sourceKeyCount");
        nonNegative(distinctSourceKeyCount, "distinctSourceKeyCount");
        nonNegative(crossTypeLexemePairCount, "crossTypeLexemePairCount");
        nonNegative(crossTypeTaggedDistinctPairCount, "crossTypeTaggedDistinctPairCount");
        nonNegative(physicalRowCount, "physicalRowCount");
        nonNegative(logicalRootCount, "logicalRootCount");
        nonNegative(childCount, "childCount");
        nonNegative(distinctChildCount, "distinctChildCount");
        childObservations = sortedChildObservations(childObservations);
        rootCardinality =
                Objects.requireNonNull(rootCardinality, "A cardinalidade observada é obrigatória.");
        fieldObservations = sortedFieldObservations(fieldObservations);
        observedRecordPaths = sortedPathsAllowEmpty(observedRecordPaths);
        observedConnectionPaths = sortedPathsAllowEmpty(observedConnectionPaths);
        observedRootScalarPaths = sortedPathsAllowEmpty(observedRootScalarPaths);
        if (!observedPaths.containsAll(observedRootScalarPaths)) {
            throw new IllegalArgumentException(
                    "Um escalar de raiz não pertence aos paths observados.");
        }
        filterObservations = sortedFilterObservations(filterObservations);
        observedRelationshipCandidatePaths =
                sortedPathsAllowEmpty(observedRelationshipCandidatePaths);
        pagination = Objects.requireNonNull(pagination, "A paginação observada é obrigatória.");
        temporal = Objects.requireNonNull(temporal, "A evidência temporal é obrigatória.");
        status = Objects.requireNonNull(status, "A evidência de status é obrigatória.");
        usage = Objects.requireNonNull(usage, "O consumo observado é obrigatório.");
        evidenceClassification =
                Objects.requireNonNull(
                        evidenceClassification, "A classificação da evidência é obrigatória.");
    }

    public String observationFingerprint() {
        return CharacterizationFingerprint.sha256(this);
    }

    private static Set<String> sortedPaths(final Set<String> values) {
        final TreeSet<String> sorted =
                new TreeSet<>(
                        Objects.requireNonNull(values, "Os paths observados são obrigatórios."));
        if (sorted.isEmpty()
                || sorted.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Os paths observados são inválidos.");
        }
        return Collections.unmodifiableSet(sorted);
    }

    private static Set<String> sortedPathsAllowEmpty(final Set<String> values) {
        final TreeSet<String> sorted =
                new TreeSet<>(
                        Objects.requireNonNull(values, "Os paths observados são obrigatórios."));
        if (sorted.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Os paths observados são inválidos.");
        }
        return Collections.unmodifiableSet(sorted);
    }

    private static Set<Presence> immutablePresence(final Set<Presence> values) {
        Objects.requireNonNull(values, "Os estados de presença observados são obrigatórios.");
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Os estados de presença observados são inválidos.");
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private static Set<WireType> immutableWireTypes(final Set<WireType> values) {
        Objects.requireNonNull(values, "Os wire types observados são obrigatórios.");
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Os wire types observados são inválidos.");
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private static List<ChildObservation> sortedChildObservations(
            final List<ChildObservation> values) {
        final List<ChildObservation> sorted =
                new ArrayList<>(
                        Objects.requireNonNull(
                                values, "As observações de filhos são obrigatórias."));
        sorted.sort(Comparator.comparing(ChildObservation::kind));
        if (sorted.stream().anyMatch(Objects::isNull)
                || sorted.stream().map(ChildObservation::kind).distinct().count()
                        != sorted.size()) {
            throw new IllegalArgumentException("As observações de filhos são inválidas.");
        }
        return List.copyOf(sorted);
    }

    private static List<FieldObservation> sortedFieldObservations(
            final List<FieldObservation> values) {
        final List<FieldObservation> sorted =
                new ArrayList<>(
                        Objects.requireNonNull(
                                values, "As observações de campo são obrigatórias."));
        sorted.sort(Comparator.comparing(FieldObservation::path));
        if (sorted.isEmpty()
                || sorted.stream().anyMatch(Objects::isNull)
                || sorted.stream().map(FieldObservation::path).distinct().count()
                        != sorted.size()) {
            throw new IllegalArgumentException("As observações de campo são inválidas.");
        }
        return List.copyOf(sorted);
    }

    private static List<FilterObservation> sortedFilterObservations(
            final List<FilterObservation> values) {
        final List<FilterObservation> sorted =
                new ArrayList<>(
                        Objects.requireNonNull(
                                values, "As observações de filtro são obrigatórias."));
        sorted.sort(Comparator.comparing(FilterObservation::path));
        if (sorted.isEmpty()
                || sorted.stream().anyMatch(Objects::isNull)
                || sorted.stream().map(FilterObservation::path).distinct().count()
                        != sorted.size()) {
            throw new IllegalArgumentException("As observações de filtro são inválidas.");
        }
        return List.copyOf(sorted);
    }

    private static void nonNegative(final int value, final String field) {
        if (value < 0) {
            throw new IllegalArgumentException("A métrica " + field + " não pode ser negativa.");
        }
    }

    public record PaginationObservation(
            PaginationKind kind,
            TerminalCondition terminalCondition,
            int pageSize,
            int pagesObserved,
            int dataPagesObserved,
            int terminalProbeRequests,
            int maximumDistinctSourceKeysOnPage,
            boolean shortNonTerminalPageObserved,
            int shortPageObservedRowCount,
            boolean continuedAfterShortPage,
            boolean terminalConditionMet,
            boolean hasNextPageAtLimit,
            boolean cursorRequired,
            boolean cursorPresent,
            int cursorCount,
            int distinctCursorCount,
            boolean cursorCycleDetected,
            String observedOrderingExpression,
            boolean orderingRespected,
            int exactReplayTieCount,
            int divergentTieCount) {

        public PaginationObservation {
            kind = Objects.requireNonNull(kind, "O tipo de paginação observado é obrigatório.");
            terminalCondition =
                    Objects.requireNonNull(
                            terminalCondition, "A condição terminal observada é obrigatória.");
            nonNegative(pageSize, "pageSize");
            nonNegative(pagesObserved, "pagesObserved");
            nonNegative(dataPagesObserved, "dataPagesObserved");
            nonNegative(terminalProbeRequests, "terminalProbeRequests");
            nonNegative(maximumDistinctSourceKeysOnPage, "maximumDistinctSourceKeysOnPage");
            nonNegative(shortPageObservedRowCount, "shortPageObservedRowCount");
            nonNegative(cursorCount, "cursorCount");
            nonNegative(distinctCursorCount, "distinctCursorCount");
            nonNegative(exactReplayTieCount, "exactReplayTieCount");
            nonNegative(divergentTieCount, "divergentTieCount");
            if (observedOrderingExpression != null
                    && !observedOrderingExpression
                            .toLowerCase(java.util.Locale.ROOT)
                            .matches("[a-z_]+ asc")) {
                throw new IllegalArgumentException("A ordenação observada é inválida.");
            }
            if (shortNonTerminalPageObserved
                            && (shortPageObservedRowCount == 0
                                    || shortPageObservedRowCount >= pageSize)
                    || !shortNonTerminalPageObserved && shortPageObservedRowCount != 0
                    || continuedAfterShortPage && !shortNonTerminalPageObserved) {
                throw new IllegalArgumentException("A evidência de página curta é incoerente.");
            }
        }
    }

    /** Tipos e estados vistos por path; os valores correspondentes nunca entram no harness. */
    public record FieldObservation(
            String path, Set<WireType> wireTypes, Set<Presence> presenceStates) {

        public FieldObservation {
            if (path == null || path.isBlank() || !path.startsWith("/")) {
                throw new IllegalArgumentException("O path observado do campo é inválido.");
            }
            wireTypes = immutableWireTypes(wireTypes);
            presenceStates = immutablePresence(presenceStates);
        }
    }

    public record FilterObservation(
            String path, boolean present, boolean watermark, Boolean booleanValue) {

        public FilterObservation {
            if (path == null || !path.matches("[a-z][a-z0-9_.]{1,127}")) {
                throw new IllegalArgumentException("O filtro observado é inválido.");
            }
        }
    }

    public record ChildObservation(
            ChildKind kind,
            String observedKeyPath,
            boolean observedTypeTagged,
            Set<Presence> presenceStates,
            Set<WireType> wireTypes,
            Set<String> attributePaths,
            int attributeWithoutKeyCount,
            int keyWithoutAttributeCount,
            boolean valueDomainValid,
            int count,
            int distinctCount,
            boolean collision) {

        public ChildObservation {
            kind = Objects.requireNonNull(kind, "O tipo de filho observado é obrigatório.");
            if (observedKeyPath == null
                    || observedKeyPath.isBlank()
                    || !observedKeyPath.startsWith("/")) {
                throw new IllegalArgumentException(
                        "O path observado da chave de filho é inválido.");
            }
            presenceStates = immutablePresence(presenceStates);
            wireTypes = immutableWireTypes(wireTypes);
            attributePaths = sortedPathsAllowEmpty(attributePaths);
            nonNegative(attributeWithoutKeyCount, "childObservation.attributeWithoutKeyCount");
            nonNegative(keyWithoutAttributeCount, "childObservation.keyWithoutAttributeCount");
            nonNegative(count, "childObservation.count");
            nonNegative(distinctCount, "childObservation.distinctCount");
            if (distinctCount > count) {
                throw new IllegalArgumentException(
                        "A cardinalidade observada do filho é inválida.");
            }
        }
    }

    public record TemporalObservation(
            TimestampState timestampState,
            TimezoneState timezoneState,
            TemporalTranslation observedTranslation,
            String observedTimezone,
            Set<String> observedPaths,
            Set<String> validValuePaths,
            String selectedFreshnessPath,
            boolean precedenceApplied) {

        public TemporalObservation {
            timestampState =
                    Objects.requireNonNull(timestampState, "O estado do timestamp é obrigatório.");
            timezoneState =
                    Objects.requireNonNull(timezoneState, "O estado do timezone é obrigatório.");
            observedTranslation =
                    Objects.requireNonNull(
                            observedTranslation, "A tradução temporal observada é obrigatória.");
            if (timezoneState == TimezoneState.NOT_APPLICABLE && observedTimezone != null
                    || timezoneState == TimezoneState.EXPLICIT_CONFIRMED
                            && (observedTimezone == null || observedTimezone.isBlank())) {
                throw new IllegalArgumentException("O timezone observado é incoerente.");
            }
            observedPaths = sortedPathsAllowEmpty(observedPaths);
            validValuePaths = sortedPathsAllowEmpty(validValuePaths);
            if (!observedPaths.containsAll(validValuePaths)) {
                throw new IllegalArgumentException("Os timestamps válidos não foram observados.");
            }
        }
    }

    public record StatusObservation(
            String path,
            StatusMode mode,
            String appliedPolicyFingerprint,
            int knownCount,
            int unknownCount,
            boolean referenceApplied,
            boolean conflict) {

        public StatusObservation {
            mode = Objects.requireNonNull(mode, "O modo de status observado é obrigatório.");
            nonNegative(knownCount, "status.knownCount");
            nonNegative(unknownCount, "status.unknownCount");
            if (mode == StatusMode.NOT_APPLICABLE && path != null
                    || mode != StatusMode.NOT_APPLICABLE
                            && (path == null || path.isBlank() || !path.startsWith("/"))) {
                throw new IllegalArgumentException("O path de status observado é inválido.");
            }
            if (mode == StatusMode.NOT_APPLICABLE && appliedPolicyFingerprint != null
                    || mode != StatusMode.NOT_APPLICABLE
                            && (appliedPolicyFingerprint == null
                                    || !appliedPolicyFingerprint.matches("[0-9a-f]{64}"))) {
                throw new IllegalArgumentException("A política de status observada é inválida.");
            }
        }
    }

    public record ResourceUsage(long bytes, int rows, int pages, int depth, int paths, int nodes) {

        public ResourceUsage {
            if (bytes < 0 || rows < 0 || pages < 0 || depth < 0 || paths < 0 || nodes < 0) {
                throw new IllegalArgumentException("O consumo estrutural não pode ser negativo.");
            }
        }
    }
}
