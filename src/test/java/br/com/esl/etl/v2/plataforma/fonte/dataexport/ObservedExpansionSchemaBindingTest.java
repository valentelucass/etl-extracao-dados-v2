package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.bound;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.plan;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.raw;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.row;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ObservedExpansionSchemaBindingTest {
    @Test
    void fourPinnedResourcesClose150UndeclaredFieldsAnd40QualifiedFilters() {
        int fields = 0;
        int filters = 0;
        for (final var template : ObservedExpansionCaptureTemplate.values()) {
            final var schema = schema(template);
            fields += schema.fields().size();
            filters +=
                    (int)
                            schema.metadata().elements().stream()
                                    .filter(
                                            e ->
                                                    e.kind()
                                                            == ContractMetadata.ElementKind
                                                                    .DATA_FILTER)
                                    .count();
            assertTrue(
                    schema.metadata().elements().stream()
                            .filter(e -> e.kind() == ContractMetadata.ElementKind.DATA_FIELD)
                            .allMatch(
                                    e ->
                                            e.declaredType()
                                                            == ContractMetadata.DeclaredType
                                                                    .UNDECLARED
                                                    && e.declaredTypeFingerprint().isEmpty()));
        }
        assertEquals(150, fields);
        assertEquals(40, filters);
    }

    @Test
    void fatHas18QualifiedFiltersAnd17LeafNamesWithoutProviderDuplicate() {
        final var metadata = schema(ObservedExpansionCaptureTemplate.FAT).metadata();
        final var paths =
                metadata.elements().stream()
                        .filter(e -> e.kind() == ContractMetadata.ElementKind.DATA_FILTER)
                        .map(ContractMetadata.Element::path)
                        .toList();
        assertEquals(18, paths.size());
        assertEquals(18L, paths.stream().distinct().count());
        assertEquals(
                17L,
                paths.stream().map(p -> p.substring(p.lastIndexOf('.') + 1)).distinct().count());
        assertTrue(paths.contains("fit_freight_cte.corporation_sequence_number"));
        assertTrue(paths.contains("freights.corporation_sequence_number"));
    }

    @Test
    void metadataReorderIsSemanticEqualityButFieldRemovalAdditionOrDeclarationIsDrift() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var selection = ObservedExpansionSchemaCatalog.select(plan.template());
        final var original = schema(plan.template()).metadata();
        final var reversed = new ArrayList<>(original.elements());
        java.util.Collections.reverse(reversed);
        ObservedExpansionSchemaGuard.bind(
                plan, selection, 8636, new ContractMetadata(reversed, Optional.empty()));
        final var missing = new ArrayList<>(original.elements());
        missing.remove(0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ObservedExpansionSchemaGuard.bind(
                                plan,
                                selection,
                                8636,
                                new ContractMetadata(missing, Optional.empty())));
        final var added = new ArrayList<>(original.elements());
        added.add(
                new ContractMetadata.Element(
                        ContractMetadata.ElementKind.DATA_FIELD,
                        "new_info_field",
                        ContractMetadata.DeclaredType.UNDECLARED));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ObservedExpansionSchemaGuard.bind(
                                plan,
                                selection,
                                8636,
                                new ContractMetadata(added, Optional.empty())));
        final var declared = new ArrayList<>(original.elements());
        declared.replaceAll(
                e ->
                        e.path().equals("document")
                                ? new ContractMetadata.Element(
                                        e.kind(), e.path(), ContractMetadata.DeclaredType.STRING)
                                : e);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ObservedExpansionSchemaGuard.bind(
                                plan,
                                selection,
                                8636,
                                new ContractMetadata(declared, Optional.empty())));
    }

    @Test
    void filterTableOrDeclaredTypeFingerprintCannotBeCollapsedByLeafName() {
        final var plan = plan(ObservedExpansionCaptureTemplate.FAT);
        final var selection = ObservedExpansionSchemaCatalog.select(plan.template());
        final var changed = new ArrayList<>(schema(plan.template()).metadata().elements());
        changed.replaceAll(
                e ->
                        e.path().equals("fit_freight_cte.corporation_sequence_number")
                                ? new ContractMetadata.Element(
                                        e.kind(),
                                        "other_table.corporation_sequence_number",
                                        e.declaredType(),
                                        e.declaredTypeFingerprint())
                                : e);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ObservedExpansionSchemaGuard.bind(
                                plan,
                                selection,
                                4924,
                                new ContractMetadata(changed, Optional.empty())));
        final var changedType = new ArrayList<>(schema(plan.template()).metadata().elements());
        changedType.replaceAll(
                e ->
                        e.path().equals("freights.type")
                                ? ContractMetadata.Element.fromDeclaredType(
                                        e.kind(), e.path(), Optional.of("new-provider-type"))
                                : e);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ObservedExpansionSchemaGuard.bind(
                                plan,
                                selection,
                                4924,
                                new ContractMetadata(changedType, Optional.empty())));
    }

    @Test
    void selectedTemplateAndObservedInfoRequestTemplateMustMatchPlan() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ObservedExpansionSchemaGuard.bind(
                                plan,
                                ObservedExpansionSchemaCatalog.select(
                                        ObservedExpansionCaptureTemplate.FAT),
                                8636,
                                schema(plan.template()).metadata()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ObservedExpansionSchemaGuard.bind(
                                plan,
                                ObservedExpansionSchemaCatalog.select(plan.template()),
                                4924,
                                schema(plan.template()).metadata()));
    }

    @Test
    void sameUuidWithChangedSourceTenantWindowOrderPerOrLimitsIsStillADifferentPlan() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var alternatives =
                List.of(
                        copy(
                                plan,
                                "OTHER_SOURCE",
                                plan.tenantScope(),
                                plan.businessWindow(),
                                plan.orderBy(),
                                plan.per(),
                                plan.limits()),
                        copy(
                                plan,
                                plan.sourceInstance(),
                                "OTHER_TENANT",
                                plan.businessWindow(),
                                plan.orderBy(),
                                plan.per(),
                                plan.limits()),
                        copy(
                                plan,
                                plan.sourceInstance(),
                                plan.tenantScope(),
                                new BusinessDateRange(
                                        LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 18)),
                                plan.orderBy(),
                                plan.per(),
                                plan.limits()),
                        copy(
                                plan,
                                plan.sourceInstance(),
                                plan.tenantScope(),
                                plan.businessWindow(),
                                ObservedExpansionCapturePlan.OrderBy.explicit("issue_date desc"),
                                plan.per(),
                                plan.limits()),
                        copy(
                                plan,
                                plan.sourceInstance(),
                                plan.tenantScope(),
                                plan.businessWindow(),
                                plan.orderBy(),
                                1,
                                plan.limits()),
                        copy(
                                plan,
                                plan.sourceInstance(),
                                plan.tenantScope(),
                                plan.businessWindow(),
                                plan.orderBy(),
                                plan.per(),
                                new ObservedExpansionCaptureLimits(
                                        4096, 65536, 16384, 262144, 100, 200, 4, 10000)));
        for (final var changed : alternatives) {
            assertEquals(plan.captureExecutionId(), changed.captureExecutionId());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> guard.inspect(raw(changed, row(changed.template()))));
        }
        final var otherId = plan(plan.template());
        assertThrows(
                IllegalArgumentException.class,
                () -> guard.inspect(raw(otherId, row(otherId.template()))));
    }

    @Test
    void alternateDataEnvelopeCannotAcquireAnObservedRootArrayBinding() {
        final var original = plan(ObservedExpansionCaptureTemplate.CAP);
        final var changed =
                new ObservedExpansionCapturePlan(
                        UUID.randomUUID(),
                        original.template(),
                        original.sourceInstance(),
                        original.tenantScope(),
                        original.businessWindow(),
                        original.sourceZone(),
                        original.transport(),
                        original.orderBy(),
                        ObservedExpansionCapturePlan.Envelope.DATA_OBJECT,
                        original.per(),
                        original.limits());
        assertThrows(IllegalArgumentException.class, () -> bound(changed));
    }

    @Test
    void tamperedReleaseOrResourcePinIsDeniedBeforeSchemaConstruction() throws Exception {
        final var selection =
                ObservedExpansionSchemaCatalog.select(ObservedExpansionCaptureTemplate.CAP);
        final byte[] original;
        try (var input = getClass().getResourceAsStream(selection.resource())) {
            original = input.readAllBytes();
        }
        ObservedExpansionSchemaCatalog.read(selection, new ByteArrayInputStream(original));
        final var modified = original.clone();
        modified[modified.length / 2] ^= 1;
        assertThrows(
                IllegalStateException.class,
                () ->
                        ObservedExpansionSchemaCatalog.read(
                                selection, new ByteArrayInputStream(modified)));
        final byte[] changedRelease =
                new String(original, java.nio.charset.StandardCharsets.UTF_8)
                        .replace(selection.schemaRelease(), "forged-release")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(
                IllegalStateException.class,
                () ->
                        ObservedExpansionSchemaCatalog.read(
                                selection, new ByteArrayInputStream(changedRelease)));
        assertThrows(
                IllegalStateException.class,
                () ->
                        ObservedExpansionSchemaCatalog.read(
                                selection, new ByteArrayInputStream(new byte[65537])));
        assertThrows(
                IllegalStateException.class,
                () -> ObservedExpansionSchemaCatalog.read(selection, null));
    }

    @Test
    void anotherTemplatesRealResourceCannotSubstituteForThePinnedSelection() throws Exception {
        final var cap = ObservedExpansionSchemaCatalog.select(ObservedExpansionCaptureTemplate.CAP);
        final var fat = ObservedExpansionSchemaCatalog.select(ObservedExpansionCaptureTemplate.FAT);
        final byte[] bytes;
        try (var input = getClass().getResourceAsStream(fat.resource())) {
            bytes = input.readAllBytes();
        }
        assertThrows(
                IllegalStateException.class,
                () -> ObservedExpansionSchemaCatalog.read(cap, new ByteArrayInputStream(bytes)));
    }

    private static ObservedExpansionCapturePlan copy(
            final ObservedExpansionCapturePlan original,
            final String source,
            final String tenant,
            final BusinessDateRange window,
            final ObservedExpansionCapturePlan.OrderBy order,
            final int per,
            final ObservedExpansionCaptureLimits limits) {
        return new ObservedExpansionCapturePlan(
                original.captureExecutionId(),
                original.template(),
                source,
                tenant,
                window,
                original.sourceZone(),
                original.transport(),
                order,
                original.envelope(),
                per,
                limits);
    }
}
