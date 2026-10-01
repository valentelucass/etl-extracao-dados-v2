package br.com.esl.etl.v2.plataforma.analitico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticBindingContractsTest {
    private static final UUID EXECUTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate START = LocalDate.of(2036, 4, 1);
    private static final LocalDate END = START.plusDays(1);

    @Test
    void dimensionBindingAcceptsScopedKeysAndHalfOpenValidity() {
        final var binding =
                dimension("INTEGER:1", "synthetic-branch", 1, START, END, null, "synthetic-proof");
        assertEquals(AnalyticDimensionBinding.Entity.FRETE, binding.entity());
        assertEquals(AnalyticDimensionBinding.Role.BRANCH, binding.role());
        assertEquals("FILIAL", binding.role().dimension());
        assertEquals(END, binding.toExclusive());
        assertEquals(
                "synthetic-prior",
                dimension(
                                "INTEGER:1",
                                "synthetic-branch",
                                2,
                                START,
                                END,
                                "synthetic-prior",
                                "synthetic-proof")
                        .previousEntityKey());
    }

    @Test
    void dimensionBindingRejectsUnscopedOrAmbiguousAssignments() {
        for (final String key : List.of("", "  ", " INTEGER:1", "INTEGER:1 ", "x".repeat(257))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            dimension(
                                    key,
                                    "synthetic-branch",
                                    1,
                                    START,
                                    END,
                                    null,
                                    "synthetic-proof"));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> dimension(null, "synthetic-branch", 1, START, END, null, "synthetic-proof"));
        for (final String entityKey :
                List.of("branch", "synthetic-", "synthetic-" + "x".repeat(55))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            dimension(
                                    "INTEGER:1",
                                    entityKey,
                                    1,
                                    START,
                                    END,
                                    null,
                                    "synthetic-proof"));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> dimension("INTEGER:1", null, 1, START, END, null, "synthetic-proof"));
        for (final int revision : List.of(0, 100001)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            dimension(
                                    "INTEGER:1",
                                    "synthetic-branch",
                                    revision,
                                    START,
                                    END,
                                    null,
                                    "synthetic-proof"));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        dimension(
                                "INTEGER:1",
                                "synthetic-branch",
                                1,
                                START,
                                START,
                                null,
                                "synthetic-proof"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        dimension(
                                "INTEGER:1",
                                "synthetic-branch",
                                1,
                                START,
                                END,
                                "other",
                                "synthetic-proof"));
        assertThrows(
                IllegalArgumentException.class,
                () -> dimension("INTEGER:1", "synthetic-branch", 1, START, END, null, "proof"));
        assertThrows(
                NullPointerException.class,
                () ->
                        new AnalyticDimensionBinding(
                                null,
                                "INTEGER:1",
                                EXECUTION,
                                AnalyticDimensionBinding.Role.BRANCH,
                                "synthetic-branch",
                                1,
                                START,
                                END,
                                true,
                                null,
                                "synthetic-proof"));
    }

    @Test
    void freightRelationSeparatesCrosswalkHistoryFromDirectBinding() {
        assertEquals(
                "INTEGER:2",
                relation(
                                AnalyticFreightRelationBinding.Kind.CROSSWALK,
                                "INTEGER:1",
                                "INTEGER:3",
                                2,
                                "INTEGER:2")
                        .previousFreightKey());
        assertEquals(
                null,
                relation(
                                AnalyticFreightRelationBinding.Kind.DIRECT,
                                "INTEGER:1",
                                "INTEGER:3",
                                1,
                                null)
                        .previousFreightKey());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        relation(
                                AnalyticFreightRelationBinding.Kind.DIRECT,
                                "INTEGER:1",
                                "INTEGER:3",
                                2,
                                "INTEGER:2"));
    }

    @Test
    void freightRelationRejectsInvalidAndOutOfRangeKeys() {
        for (final String key :
                List.of(
                        "INTEGER:0",
                        "INTEGER:01",
                        "INTEGER:-1",
                        "1",
                        "INTEGER:9223372036854775808")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            relation(
                                    AnalyticFreightRelationBinding.Kind.CROSSWALK,
                                    key,
                                    "INTEGER:3",
                                    1,
                                    null));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            relation(
                                    AnalyticFreightRelationBinding.Kind.CROSSWALK,
                                    "INTEGER:1",
                                    key,
                                    1,
                                    null));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        relation(
                                AnalyticFreightRelationBinding.Kind.CROSSWALK,
                                null,
                                "INTEGER:3",
                                1,
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        relation(
                                AnalyticFreightRelationBinding.Kind.CROSSWALK,
                                "INTEGER:1",
                                "INTEGER:3",
                                1,
                                "old"));
        for (final int revision : List.of(0, 100001)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            relation(
                                    AnalyticFreightRelationBinding.Kind.CROSSWALK,
                                    "INTEGER:1",
                                    "INTEGER:3",
                                    revision,
                                    null));
        }
        assertThrows(
                NullPointerException.class,
                () ->
                        new AnalyticFreightRelationBinding(
                                null,
                                "INTEGER:1",
                                EXECUTION,
                                "INTEGER:3",
                                EXECUTION,
                                1,
                                true,
                                null));
    }

    @Test
    void manifestStateAllowsExplicitReactivationButRejectsInvalidLifecycle() {
        assertEquals(
                true,
                new AnalyticManifestState("INTEGER:1", EXECUTION, 2, true, true).reactivate());
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticManifestState("INTEGER:1", EXECUTION, 2, false, true));
        for (final String key : List.of("INTEGER:0", "INTEGER:01", "1")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnalyticManifestState(key, EXECUTION, 1, true, false));
        }
        assertEquals(
                "ANA_MANIFEST_STATE_KEY_RANGE",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new AnalyticManifestState(
                                                "INTEGER:9223372036854775808",
                                                EXECUTION,
                                                1,
                                                true,
                                                false))
                        .getMessage());
        for (final int revision : List.of(0, 100001)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnalyticManifestState("INTEGER:1", EXECUTION, revision, true, false));
        }
    }

    @Test
    void fiscalAttributeKeepsAbsentSeriesDistinctFromInvalidSeries() {
        assertEquals(null, new AnalyticFiscalAttribute(1, EXECUTION, 1, null).nfseSeries());
        assertEquals("S", new AnalyticFiscalAttribute(1, EXECUTION, 1, "S").nfseSeries());
        for (final String series : List.of("", "  ", "x".repeat(51))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnalyticFiscalAttribute(1, EXECUTION, 1, series));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalyticFiscalAttribute(0, EXECUTION, 1, "S"));
        for (final int revision : List.of(0, 100001)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new AnalyticFiscalAttribute(1, EXECUTION, revision, "S"));
        }
        assertThrows(
                NullPointerException.class, () -> new AnalyticFiscalAttribute(1, null, 1, "S"));
    }

    private static AnalyticDimensionBinding dimension(
            final String sourceKey,
            final String entityKey,
            final int revision,
            final LocalDate from,
            final LocalDate toExclusive,
            final String previousEntityKey,
            final String evidence) {
        return new AnalyticDimensionBinding(
                AnalyticDimensionBinding.Entity.FRETE,
                sourceKey,
                EXECUTION,
                AnalyticDimensionBinding.Role.BRANCH,
                entityKey,
                revision,
                from,
                toExclusive,
                true,
                previousEntityKey,
                evidence);
    }

    private static AnalyticFreightRelationBinding relation(
            final AnalyticFreightRelationBinding.Kind kind,
            final String originKey,
            final String freightKey,
            final int revision,
            final String previousFreightKey) {
        return new AnalyticFreightRelationBinding(
                kind,
                originKey,
                EXECUTION,
                freightKey,
                EXECUTION,
                revision,
                true,
                previousFreightKey);
    }
}
