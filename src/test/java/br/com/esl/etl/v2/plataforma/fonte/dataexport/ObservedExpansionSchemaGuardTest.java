package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.bound;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.plan;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.raw;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.row;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.schema;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ObservedExpansionSchemaGuardTest {
    @Test
    void allFourPositiveFixturesBindAndKeepOriginalRawWithoutGrantingCoreValidation() {
        for (final var template : ObservedExpansionCaptureTemplate.values()) {
            final var plan = plan(template);
            final var raw = raw(plan, row(template));
            final byte[] original = raw.body();
            final var result = bound(plan).inspect(raw);
            assertTrue(result.schemaMatched());
            assertEquals(1, result.physicalRows().orElseThrow());
            assertSame(raw, result.rawPage());
            assertSame(raw, result.checkedPage().orElseThrow().rawPage());
            assertArrayEquals(original, result.rawPage().body());
            assertEquals("OBSERVED_WIRE_MATCH_ONLY", result.validationScope());
            assertEquals("NOT_PERFORMED_BY_CORE", plan.sourceSchemaValidation());
        }
    }

    @Test
    void removingEachOfThe150ObservedPresentFieldsIsCausallyRejected() {
        int mutations = 0;
        for (final var template : ObservedExpansionCaptureTemplate.values()) {
            final var plan = plan(template);
            final var guard = bound(plan);
            final var baseline = row(template);
            assertTrue(guard.inspect(raw(plan, baseline)).schemaMatched());
            for (final var field : schema(template).fields().values()) {
                assertTrue(field.required());
                final var changed = baseline.deepCopy();
                changed.remove(field.path());
                assertRejected(
                        guard.inspect(raw(plan, changed)),
                        ObservedExpansionSchemaGuard.Issue.MISSING_FIELD);
                mutations++;
            }
        }
        assertEquals(150, mutations);
    }

    @Test
    void undeclaredFieldIsRejectedWhileUnqualifiedCoreAloneWouldMapIt() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var baseline = row(plan.template());
        final var guard = bound(plan);
        assertTrue(guard.inspect(raw(plan, baseline)).schemaMatched());
        baseline.put("new_provider_field", "offline");
        final var changed = raw(plan, baseline);
        assertRejected(guard.inspect(changed), ObservedExpansionSchemaGuard.Issue.UNDECLARED_FIELD);
        final var coreOnly = new ObservedExpansionPageProjector(plan).projectNext(changed);
        assertEquals(ObservedExpansionPageProjection.Status.MAPPED, coreOnly.status());
        assertEquals("NOT_PERFORMED_BY_CORE", coreOnly.sourceSchemaValidation());
    }

    @Test
    void sixNullOnlyFieldsNeverAcquireAnInventedNonNullType() {
        int mutations = 0;
        for (final var template : ObservedExpansionCaptureTemplate.values()) {
            final var plan = plan(template);
            final var guard = bound(plan);
            final var baseline = row(template);
            assertTrue(guard.inspect(raw(plan, baseline)).schemaMatched());
            for (final var field : schema(template).fields().values()) {
                if (field.unknownNonNull()) {
                    assertEquals(
                            Set.of(ObservedExpansionSchemaCatalog.Wire.NULL), field.wireTypes());
                    final var changed = baseline.deepCopy();
                    changed.put(field.path(), "not-yet-observed");
                    assertRejected(
                            guard.inspect(raw(plan, changed)),
                            ObservedExpansionSchemaGuard.Issue.NON_NULL_TYPE_UNOBSERVED);
                    mutations++;
                }
            }
        }
        assertEquals(6, mutations);
    }

    @Test
    void
            historicalGenericNumberRetainsIntegerAndFractionalFamilyWithoutClaimingFractionObservation() {
        int families = 0;
        for (final var template : ObservedExpansionCaptureTemplate.values()) {
            final var plan = plan(template);
            final var guard = bound(plan);
            for (final var field : schema(template).fields().values()) {
                if (field.historicalGenericNumber()) {
                    assertTrue(
                            field.wireTypes()
                                    .contains(ObservedExpansionSchemaCatalog.Wire.INTEGER));
                    assertTrue(
                            field.wireTypes().contains(ObservedExpansionSchemaCatalog.Wire.NUMBER));
                    final var integer = row(template);
                    integer.put(field.path(), 1);
                    assertTrue(guard.inspect(raw(plan, integer)).schemaMatched());
                    final var number = row(template);
                    number.put(field.path(), new BigDecimal("1.2500"));
                    assertTrue(guard.inspect(raw(plan, number)).schemaMatched());
                    final var wrong = row(template);
                    wrong.put(field.path(), "1.2500");
                    assertRejected(
                            guard.inspect(raw(plan, wrong)),
                            ObservedExpansionSchemaGuard.Issue.WIRE_TYPE);
                    families++;
                }
            }
        }
        assertEquals(16, families);
    }

    @Test
    void eachOfSixStringArraysRejectsObjectNestedArrayNumberBooleanAndNullItems() {
        int arrays = 0;
        for (final var template : ObservedExpansionCaptureTemplate.values()) {
            final var plan = plan(template);
            final var guard = bound(plan);
            for (final var field : schema(template).fields().values()) {
                if (!field.arrayItemTypes().isEmpty()) {
                    final var good = row(template);
                    assertTrue(guard.inspect(raw(plan, good)).schemaMatched());
                    final var object = good.deepCopy();
                    object.putArray(field.path()).addObject().put("new", "offline");
                    final var nested = good.deepCopy();
                    nested.putArray(field.path()).addArray().add("offline");
                    final var number = good.deepCopy();
                    number.putArray(field.path()).add(1);
                    final var bool = good.deepCopy();
                    bool.putArray(field.path()).add(true);
                    final var nil = good.deepCopy();
                    nil.putArray(field.path()).addNull();
                    for (final var changed : java.util.List.of(object, nested, number, bool, nil)) {
                        assertRejected(
                                guard.inspect(raw(plan, changed)),
                                ObservedExpansionSchemaGuard.Issue.ARRAY_ITEM_TYPE);
                    }
                    arrays++;
                }
            }
        }
        assertEquals(6, arrays);
    }

    @Test
    void sampleMaximumIsNotInventedAsArrayContractAndDuplicatesAreRetained() {
        final var plan = plan(ObservedExpansionCaptureTemplate.INV);
        final var row = row(plan.template());
        final var items = row.putArray("cnr_c_s_fit_invoices_mapping");
        for (int i = 0; i < 11; i++) {
            items.add("same-offline-item");
        }
        final var raw = raw(plan, row);
        final var result = bound(plan).inspect(raw);
        assertTrue(result.schemaMatched());
        assertArrayEquals(raw.body(), result.checkedPage().orElseThrow().rawPage().body());
    }

    @Test
    void rootObjectNewNestingOrWrongScalarTypeIsRejected() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var guard = bound(plan);
        final var good = row(plan.template());
        assertTrue(guard.inspect(raw(plan, good)).schemaMatched());
        final var nested = good.deepCopy();
        nested.putObject("document").put("child", "offline");
        assertRejected(
                guard.inspect(raw(plan, nested)), ObservedExpansionSchemaGuard.Issue.WIRE_TYPE);
        final var wrong = good.deepCopy();
        wrong.put("document", true);
        assertRejected(
                guard.inspect(raw(plan, wrong)), ObservedExpansionSchemaGuard.Issue.WIRE_TYPE);
        assertRejected(
                guard.inspect(raw(plan, "{\"data\":[" + good + "]}")),
                ObservedExpansionSchemaGuard.Issue.ROOT_ARRAY_REQUIRED);
    }

    @Test
    void newlyNullNonNullableFieldIsDriftRatherThanImplicitWidening() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var good = row(plan.template());
        final var guard = bound(plan);
        assertTrue(guard.inspect(raw(plan, good)).schemaMatched());
        good.putNull("document");
        assertRejected(
                guard.inspect(raw(plan, good)),
                ObservedExpansionSchemaGuard.Issue.NULL_NOT_OBSERVED);
    }

    @Test
    void sevenLongSyntheticCommentsPassWireAndKeepMapperIssuesInUnchangedCore() {
        final var plan = plan(ObservedExpansionCaptureTemplate.SIN);
        final var row = row(plan.template());
        row.put("insurance_claim_commentary", "x".repeat(2241));
        final String source =
                "[" + String.join(",", java.util.Collections.nCopies(7, row.toString())) + "]";
        final var raw = raw(plan, source);
        final var checked = bound(plan).inspect(raw).checkedPage().orElseThrow();
        final var projected =
                new ObservedExpansionPageProjector(plan).projectNext(checked.rawPage());
        assertEquals(7, projected.physicalRows().orElseThrow());
        assertEquals(7, projected.fieldIssueRows());
        for (final var record : projected.records()) {
            final var commentary =
                    record.fields().stream()
                            .filter(f -> f.path().equals("insurance_claim_commentary"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(2241, commentary.value().raw().length());
            assertEquals("INVALID_INSURANCE_CLAIM_COMMENTARY", commentary.value().issue());
        }
        assertArrayEquals(raw.body(), projected.rawPage().body());
        assertEquals("NOT_PERFORMED_BY_CORE", projected.sourceSchemaValidation());
    }

    @Test
    void malformedJsonDuplicateKeysAndTrailingDocumentsRemainRawWithUnknownCount() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        for (final String body :
                java.util.List.of("[", "[{\"document\":1,\"document\":2}]", "[] []")) {
            final var raw = raw(plan, body);
            final var result = bound(plan).inspect(raw);
            assertRejected(result, ObservedExpansionSchemaGuard.Issue.INVALID_JSON_OR_NODE_BOUND);
            assertSame(raw, result.rawPage());
            assertArrayEquals(body.getBytes(StandardCharsets.UTF_8), result.rawPage().body());
        }
    }

    @Test
    void invalidUtf8DoesNotProduceReplacementCharactersOrAMappedCount() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final byte[] body = {(byte) 0xc3, 0x28};
        final var raw =
                new ObservedExpansionRawPage(plan.request(1), java.time.Instant.EPOCH, body);
        final var result = bound(plan).inspect(raw);
        assertRejected(result, ObservedExpansionSchemaGuard.Issue.INVALID_UTF8);
        assertArrayEquals(body, result.rawPage().body());
    }

    @Test
    void equalLogicalStringsDoNotCauseRawDecodeReserialize() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final String fixture =
                "[ "
                        + row(plan.template())
                                .toString()
                                .replace(
                                        "\"comments\":\"offline\"",
                                        "\"comments\":\"\\u006fffline\"")
                        + " ]";
        final var raw = raw(plan, fixture);
        final var result = bound(plan).inspect(raw);
        assertTrue(result.schemaMatched());
        assertSame(raw, result.checkedPage().orElseThrow().rawPage());
        assertArrayEquals(fixture.getBytes(StandardCharsets.UTF_8), result.rawPage().body());
    }

    @Test
    void emptyRootArrayIsOnlyAValidWireShapeAndNeverASnapshotClaim() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var result = bound(plan).inspect(raw(plan, "[]"));
        assertTrue(result.schemaMatched());
        assertEquals(0, result.physicalRows().orElseThrow());
        assertEquals("OBSERVED_WIRE_MATCH_ONLY", result.validationScope());
    }

    private static void assertRejected(
            final ObservedExpansionSchemaGuard.Inspection result,
            final ObservedExpansionSchemaGuard.Issue issue) {
        assertFalse(result.schemaMatched());
        assertEquals(issue, result.issue());
        assertTrue(result.physicalRows().isEmpty());
        assertTrue(result.checkedPage().isEmpty());
    }
}
