package br.com.esl.etl.v2.modulos.coletas.domain;

import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * COL-OBS-01: compara somente raízes 6908 observadas na mesma janela declarada pelo chamador. Um
 * expected sem oráculo de multiplicidade ou presença não inventa essas expectativas. Nem a
 * igualdade aqui, nem linhas repetidas por raiz provam snapshot, filhos ou completude da fonte.
 */
public final class ColetaObservedRootComparator {
    private static final int MAXIMUM_SOURCE_PAGES = 4;
    private static final Set<String> PRESENCE_FIELDS =
            Set.of(
                    "id",
                    "sequence_code",
                    "status",
                    "status_updated_at",
                    "finish_date",
                    "service_date",
                    "request_date",
                    "cancellation_reason",
                    "manifesto",
                    "pick_item_id",
                    "fit_p_m_pck_sequence_code",
                    "frete",
                    "pck_mik_mft_sequence_code");

    private ColetaObservedRootComparator() {}

    public enum ExpectedProvenance {
        CAPTURED_6908_PAGE_BEFORE_MAPPER,
        CAPTURED_6908_BOUNDED_TRAVERSAL,
        INDEPENDENT_WINDOW_ORACLE,
        SYNTHETIC_FIXTURE
    }

    /**
     * O caller atribui o mesmo cohort somente depois de conferir corte e proveniência em ambas as
     * entradas. Igualdade deste binding é necessária, mas não comprova o snapshot da fonte.
     * sourcePage é positiva para captura de uma página; zero identifica o agregado de uma travessia
     * limitada. Nenhum dos dois valores confirma a terminalidade ou a estabilidade do snapshot.
     */
    public record Binding(
            String sourceInstance,
            String tenantScope,
            LocalDate requestDate,
            String contractFingerprint,
            UUID comparisonCohort,
            int sourcePage) {
        public Binding {
            new ScopedSourceIdentity(
                    sourceInstance,
                    tenantScope,
                    FirstWaveIdentityContract.Entity.COLETAS,
                    new ScopedSourceIdentity.SourceKey(
                            ScopedSourceIdentity.WireType.INTEGER, "INTEGER:0"));
            Objects.requireNonNull(requestDate, "A data civil é obrigatória.");
            if (contractFingerprint == null || !contractFingerprint.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("O fingerprint do contrato é obrigatório.");
            }
            Objects.requireNonNull(comparisonCohort, "O cohort comparado é obrigatório.");
            if (sourcePage < 0 || sourcePage > MAXIMUM_SOURCE_PAGES) {
                throw new IllegalArgumentException("A página excede o piloto limitado.");
            }
        }

        @Override
        public String toString() {
            return "Binding[scope=<redacted>, contract=<redacted>, cohort=<redacted>, sourcePage="
                    + sourcePage
                    + "]";
        }
    }

    public record ExpectedRoot(
            ScopedSourceIdentity identity,
            OptionalInt physicalRows,
            Map<String, ColetaAttributePresence> fieldPresence,
            Set<Integer> sourcePages) {
        public ExpectedRoot(
                final ScopedSourceIdentity identity,
                final OptionalInt physicalRows,
                final Map<String, ColetaAttributePresence> fieldPresence) {
            this(identity, physicalRows, fieldPresence, Set.of());
        }

        public ExpectedRoot {
            requireColeta(identity);
            physicalRows =
                    Objects.requireNonNull(physicalRows, "O oráculo de linhas é obrigatório.");
            if (physicalRows.isPresent() && physicalRows.getAsInt() < 1) {
                throw new IllegalArgumentException(
                        "O número esperado de linhas deve ser positivo.");
            }
            fieldPresence = copyPresence(fieldPresence);
            final Set<Integer> declaredPages =
                    Objects.requireNonNull(sourcePages, "As páginas são obrigatórias.");
            if (declaredPages.size() > MAXIMUM_SOURCE_PAGES
                    || declaredPages.stream()
                            .anyMatch(
                                    page -> page == null || page < 1 || page > MAXIMUM_SOURCE_PAGES)
                    || (physicalRows.isPresent()
                            && declaredPages.size() > physicalRows.getAsInt())) {
                throw new IllegalArgumentException("As páginas da raiz são inválidas.");
            }
            sourcePages = Set.copyOf(declaredPages);
        }

        @Override
        public String toString() {
            return "ExpectedRoot[identity=<redacted>, physicalRowsDeclared="
                    + physicalRows.isPresent()
                    + ", presenceFields="
                    + fieldPresence.size()
                    + ", sourcePageCount="
                    + sourcePages.size()
                    + "]";
        }
    }

    public record ObservedRow(
            ScopedSourceIdentity identity,
            int page,
            Map<String, ColetaAttributePresence> fieldPresence) {
        public ObservedRow {
            requireColeta(identity);
            if (page < 0 || page > MAXIMUM_SOURCE_PAGES) {
                throw new IllegalArgumentException("A página observada excede o piloto limitado.");
            }
            fieldPresence = copyPresence(fieldPresence);
        }

        @Override
        public String toString() {
            return "ObservedRow[identity=<redacted>, page=" + page + "]";
        }
    }

    public record Result(
            ExpectedProvenance expectedProvenance,
            int expectedRoots,
            int observedRoots,
            int expectedOnlyRoots,
            int observedOnlyRoots,
            int duplicateExpectedRoots,
            int observedPhysicalRows,
            int repeatedRootRows,
            int rootsAcrossPages,
            int pageSetMismatches,
            int physicalOracleRoots,
            int physicalRowCountMismatches,
            int presenceComparedCells,
            int presenceMismatches,
            int presenceMissingCells,
            int presenceConflictingCells) {
        public Result {
            expectedProvenance =
                    Objects.requireNonNull(expectedProvenance, "A proveniência é obrigatória.");
        }

        public String evidenceLabel() {
            return switch (expectedProvenance) {
                case CAPTURED_6908_PAGE_BEFORE_MAPPER -> "PARIDADE_DA_PAGINA_OBSERVADA";
                case CAPTURED_6908_BOUNDED_TRAVERSAL -> "PARIDADE_DA_TRAVESSIA_LIMITADA_OBSERVADA";
                case INDEPENDENT_WINDOW_ORACLE, SYNTHETIC_FIXTURE ->
                        "COMPARACAO_DE_RAIZES_OBSERVADAS";
            };
        }

        public boolean observedRootSetsEqual() {
            return duplicateExpectedRoots == 0 && expectedOnlyRoots == 0 && observedOnlyRoots == 0;
        }

        /**
         * Verifica apenas oráculos declarados; zero células de presença não prova paridade de
         * campos.
         */
        public boolean declaredObservationsMatch() {
            return observedRootSetsEqual()
                    && rootsAcrossPages == 0
                    && pageSetMismatches == 0
                    && physicalRowCountMismatches == 0
                    && presenceMismatches == 0
                    && presenceMissingCells == 0
                    && presenceConflictingCells == 0;
        }

        public boolean sourceCompletenessProven() {
            return false;
        }

        public boolean childCompletenessProven() {
            return false;
        }

        public boolean terminalityProven() {
            return false;
        }

        @Override
        public String toString() {
            return "Result[provenance="
                    + expectedProvenance
                    + ", expectedRoots="
                    + expectedRoots
                    + ", observedRoots="
                    + observedRoots
                    + ", expectedOnlyRoots="
                    + expectedOnlyRoots
                    + ", observedOnlyRoots="
                    + observedOnlyRoots
                    + ", identities=<redacted>]";
        }
    }

    public static Result compare(
            final ExpectedProvenance provenance,
            final Binding expectedBinding,
            final Binding observedBinding,
            final List<ExpectedRoot> expected,
            final List<ObservedRow> observed) {
        Objects.requireNonNull(provenance, "A proveniência do expected é obrigatória.");
        final Binding binding =
                Objects.requireNonNull(expectedBinding, "O binding expected é obrigatório.");
        if (!binding.equals(
                Objects.requireNonNull(observedBinding, "O binding observado é obrigatório."))) {
            throw new IllegalArgumentException("Janela, escopo, corte ou contrato divergente.");
        }
        if (provenance == ExpectedProvenance.CAPTURED_6908_PAGE_BEFORE_MAPPER
                && binding.sourcePage() == 0) {
            throw new IllegalArgumentException("A captura pré-mapper exige uma página declarada.");
        }
        if (provenance == ExpectedProvenance.CAPTURED_6908_BOUNDED_TRAVERSAL
                && binding.sourcePage() != 0) {
            throw new IllegalArgumentException(
                    "A travessia exige binding agregado sem página singular.");
        }
        Objects.requireNonNull(expected, "As raízes esperadas são obrigatórias.");
        Objects.requireNonNull(observed, "As linhas observadas são obrigatórias.");
        final Map<ScopedSourceIdentity, ExpectedRoot> expectedByRoot = new LinkedHashMap<>();
        final Set<ScopedSourceIdentity> rootsAcrossPages = new HashSet<>();
        int duplicateExpectedRoots = 0;
        for (final ExpectedRoot root : expected) {
            final ExpectedRoot required = Objects.requireNonNull(root, "Raiz esperada nula.");
            requireScope(binding, required.identity());
            if (provenance == ExpectedProvenance.CAPTURED_6908_BOUNDED_TRAVERSAL
                    && required.sourcePages().isEmpty()) {
                throw new IllegalArgumentException("A travessia exige páginas reais da origem.");
            }
            if (provenance == ExpectedProvenance.CAPTURED_6908_PAGE_BEFORE_MAPPER
                    && !required.sourcePages().isEmpty()
                    && !required.sourcePages().equals(Set.of(binding.sourcePage()))) {
                throw new IllegalArgumentException(
                        "A raiz esperada não pertence à página declarada.");
            }
            if (required.sourcePages().size() > 1) {
                rootsAcrossPages.add(required.identity());
            }
            if (expectedByRoot.putIfAbsent(required.identity(), required) != null) {
                duplicateExpectedRoots++;
            }
        }

        final Map<ScopedSourceIdentity, RootRows> observedByRoot = new LinkedHashMap<>();
        for (final ObservedRow row : observed) {
            final ObservedRow required = Objects.requireNonNull(row, "Linha observada nula.");
            requireScope(binding, required.identity());
            if (provenance == ExpectedProvenance.CAPTURED_6908_PAGE_BEFORE_MAPPER
                    && required.page() != 0
                    && required.page() != binding.sourcePage()) {
                throw new IllegalArgumentException(
                        "A página observada não pertence à captura declarada.");
            }
            observedByRoot
                    .computeIfAbsent(required.identity(), ignored -> new RootRows())
                    .add(required);
        }

        int expectedOnly = 0;
        int physicalOracleRoots = 0;
        int physicalMismatches = 0;
        int pageSetMismatches = 0;
        int comparedPresence = 0;
        int mismatchedPresence = 0;
        int missingPresence = 0;
        int conflictingPresence = 0;
        for (final ExpectedRoot root : expectedByRoot.values()) {
            final RootRows rows = observedByRoot.get(root.identity());
            if (rows == null) {
                expectedOnly++;
                continue;
            }
            if (root.physicalRows().isPresent()) {
                physicalOracleRoots++;
                if (root.physicalRows().getAsInt() != rows.count()) {
                    physicalMismatches++;
                }
            }
            if (!root.sourcePages().isEmpty()
                    && !rows.pages().isEmpty()
                    && !root.sourcePages().equals(rows.pages())) {
                pageSetMismatches++;
            }
            for (final var field : root.fieldPresence().entrySet()) {
                final PresenceObservation actual = rows.presenceOf(field.getKey());
                switch (actual.state()) {
                    case MISSING -> missingPresence++;
                    case CONFLICT -> conflictingPresence++;
                    case CONSISTENT -> {
                        comparedPresence++;
                        if (actual.value() != field.getValue()) {
                            mismatchedPresence++;
                        }
                    }
                }
            }
        }

        int observedOnly = 0;
        int repeatedRows = 0;
        boolean sawKnownPage = false;
        boolean sawUnknownPage = false;
        for (final var entry : observedByRoot.entrySet()) {
            if (!expectedByRoot.containsKey(entry.getKey())) {
                observedOnly++;
            }
            final RootRows rows = entry.getValue();
            repeatedRows += rows.count() - 1;
            sawKnownPage |= !rows.pages().isEmpty();
            sawUnknownPage |= rows.hasUnknownPage();
            if (rows.pages().size() > 1) {
                rootsAcrossPages.add(entry.getKey());
            }
        }
        if (provenance == ExpectedProvenance.CAPTURED_6908_BOUNDED_TRAVERSAL
                && sawKnownPage
                && sawUnknownPage) {
            throw new IllegalArgumentException("A atribuição de páginas observadas é parcial.");
        }
        return new Result(
                provenance,
                expectedByRoot.size(),
                observedByRoot.size(),
                expectedOnly,
                observedOnly,
                duplicateExpectedRoots,
                observed.size(),
                repeatedRows,
                rootsAcrossPages.size(),
                pageSetMismatches,
                physicalOracleRoots,
                physicalMismatches,
                comparedPresence,
                mismatchedPresence,
                missingPresence,
                conflictingPresence);
    }

    private static void requireScope(final Binding binding, final ScopedSourceIdentity identity) {
        if (!binding.sourceInstance().equals(identity.sourceInstance())
                || !binding.tenantScope().equals(identity.tenantScope())) {
            throw new IllegalArgumentException("A identidade não pertence ao escopo vinculado.");
        }
    }

    private static void requireColeta(final ScopedSourceIdentity identity) {
        final ScopedSourceIdentity required =
                Objects.requireNonNull(identity, "A identidade é obrigatória.");
        if (required.entity() != FirstWaveIdentityContract.Entity.COLETAS
                || required.sourceKey().wireType() != ScopedSourceIdentity.WireType.INTEGER) {
            throw new IllegalArgumentException("O piloto exige ID integral de Coletas 6908.");
        }
    }

    private static Map<String, ColetaAttributePresence> copyPresence(
            final Map<String, ColetaAttributePresence> fields) {
        final Map<String, ColetaAttributePresence> required =
                Objects.requireNonNull(fields, "As presenças são obrigatórias.");
        if (required.size() > PRESENCE_FIELDS.size()) {
            throw new IllegalArgumentException("O número de presenças excede o contrato 6908.");
        }
        for (final var entry : required.entrySet()) {
            if (entry.getKey() == null
                    || !PRESENCE_FIELDS.contains(entry.getKey())
                    || entry.getValue() == null) {
                throw new IllegalArgumentException("Uma presença declarada é inválida.");
            }
        }
        return Map.copyOf(required);
    }

    private enum PresenceState {
        CONSISTENT,
        MISSING,
        CONFLICT
    }

    private record PresenceObservation(PresenceState state, ColetaAttributePresence value) {}

    private static final class RootRows {
        private final List<ObservedRow> rows = new ArrayList<>();
        private final Set<Integer> pages = new HashSet<>();
        private boolean hasUnknownPage;

        private void add(final ObservedRow row) {
            rows.add(row);
            if (row.page() > 0) {
                pages.add(row.page());
            } else {
                hasUnknownPage = true;
            }
        }

        private int count() {
            return rows.size();
        }

        private Set<Integer> pages() {
            return pages;
        }

        private boolean hasUnknownPage() {
            return hasUnknownPage;
        }

        private PresenceObservation presenceOf(final String field) {
            ColetaAttributePresence first = null;
            for (final ObservedRow row : rows) {
                if (!row.fieldPresence().containsKey(field)) {
                    return new PresenceObservation(PresenceState.MISSING, null);
                }
                final ColetaAttributePresence value = row.fieldPresence().get(field);
                if (first == null) {
                    first = value;
                } else if (first != value) {
                    return new PresenceObservation(PresenceState.CONFLICT, null);
                }
            }
            return new PresenceObservation(PresenceState.CONSISTENT, first);
        }
    }
}
