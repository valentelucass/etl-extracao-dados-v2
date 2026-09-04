package br.com.esl.etl.v2.plataforma.contrato;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ContractResponseProfiler PROFILER =
            ContractResponseProfiler.forSyntheticFixtures(
                    ContractObservationLimits.runtimeDefaults());

    @Test
    void acceptsIdenticalEvidenceAndExpectedAbsenceOfAnOptionalField() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractValidator validator = validator(release, List.of());
        final ContractResponse withoutOptional =
                response(
                        release,
                        release.response().recordRoot(),
                        release.response().rootCardinality(),
                        release.response().keyPath(),
                        List.of(field(release, "/id"), field(release, "/status")));

        assertEquals(
                ContractValidationResult.Status.ACCEPTED,
                validator.validateMetadata(release.metadata()).status());
        assertEquals(
                ContractValidationResult.Status.ACCEPTED,
                validator.validateResponse(release.response()).status());
        assertEquals(
                ContractValidationResult.Status.ACCEPTED,
                validator.validateResponse(withoutOptional).status());
    }

    @Test
    void acceptsPageSubsetsOfAClosedKeyTypeUnionAndRejectsTypesOutsideIt() throws Exception {
        final SourceContractRelease release =
                release(profile("[{@id@:1},{@id@:@2@}]".replace('@', '"')));
        final ContractValidator validator = validator(release, List.of());
        final ContractResponse integerPage = profile("[{@id@:1}]".replace('@', '"'));
        final ContractResponse stringPage = profile("[{@id@:@2@}]".replace('@', '"'));
        final ContractResponse decimalPage = profile("[{@id@:1.5}]".replace('@', '"'));

        assertEquals(
                ContractValidationResult.Status.ACCEPTED,
                validator.validateResponse(integerPage).status());
        assertEquals(
                ContractValidationResult.Status.ACCEPTED,
                validator.validateResponse(stringPage).status());

        final ContractDiff incompatible = validator.classifyResponse(decimalPage);
        assertTrue(incompatible.breakingCount() > 0);
        assertEquals(
                ContractDriftException.Reason.BREAKING_CHANGE,
                assertThrows(
                                ContractDriftException.class,
                                () -> validator.validateResponse(decimalPage))
                        .reason());
    }

    @Test
    void classifiesMissingChangedAndAddedFiltersAsBreaking() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractValidator validator = validator(release, List.of());
        final ContractMetadata missingField =
                metadataWithout(
                        release.metadata(), ContractMetadata.ElementKind.DATA_FIELD, "status");
        final ContractMetadata missingFilter =
                metadataWithout(
                        release.metadata(),
                        ContractMetadata.ElementKind.DATA_FILTER,
                        "request_date");
        final ContractMetadata changedType =
                replaceMetadata(
                        release.metadata(),
                        new ContractMetadata.Element(
                                ContractMetadata.ElementKind.DATA_FIELD,
                                "status",
                                ContractMetadata.DeclaredType.INTEGER));
        final ContractMetadata addedFilter =
                appendMetadata(
                        release.metadata(),
                        new ContractMetadata.Element(
                                ContractMetadata.ElementKind.DATA_FILTER,
                                "updated_at",
                                ContractMetadata.DeclaredType.DATE_TIME));

        assertEquals(
                ContractChange.Kind.FIELD_REMOVED,
                validator.classifyMetadata(missingField).changes().get(0).kind());
        assertEquals(
                ContractChange.Kind.FILTER_CHANGED,
                validator.classifyMetadata(missingFilter).changes().get(0).kind());
        assertEquals(
                ContractChange.Kind.DECLARED_TYPE_CHANGED,
                validator.classifyMetadata(changedType).changes().get(0).kind());
        assertEquals(
                ContractChange.Kind.FILTER_CHANGED,
                validator.classifyMetadata(addedFilter).changes().get(0).kind());
        for (final ContractMetadata observed :
                List.of(missingField, missingFilter, changedType, addedFilter)) {
            final ContractDriftException exception =
                    assertThrows(
                            ContractDriftException.class,
                            () -> validator.validateMetadata(observed));
            assertEquals(ContractDriftException.Reason.BREAKING_CHANGE, exception.reason());
            assertTrue(exception.breakingChanges() > 0);
        }
    }

    @Test
    void blocksRootKeyTypeCardinalityRequiredPresenceAndRequiredAdditionChanges() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractValidator validator = validator(release, List.of());
        final List<ContractResponse> incompatible =
                List.of(
                        response(
                                release,
                                "$",
                                ContractResponse.Cardinality.ARRAY,
                                "/id",
                                release.response().fields()),
                        response(
                                release,
                                release.response().recordRoot(),
                                release.response().rootCardinality(),
                                "/status",
                                release.response().fields()),
                        replaceField(
                                release,
                                new ContractResponse.Field(
                                        "/status",
                                        ContractResponse.Cardinality.SCALAR,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        List.of(ContractResponse.JsonType.INTEGER))),
                        replaceField(
                                release,
                                new ContractResponse.Field(
                                        "/status",
                                        ContractResponse.Cardinality.ARRAY,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        List.of(ContractResponse.JsonType.ARRAY))),
                        replaceField(
                                release,
                                ContractTestSupport.field(
                                        "/status",
                                        ContractResponse.Presence.OPTIONAL,
                                        false,
                                        ContractResponse.JsonType.STRING)),
                        response(
                                release,
                                release.response().recordRoot(),
                                release.response().rootCardinality(),
                                release.response().keyPath(),
                                List.of(field(release, "/id"), field(release, "/note"))));

        for (final ContractResponse observed : incompatible) {
            final ContractDiff diff = validator.classifyResponse(observed);
            assertTrue(diff.breakingCount() > 0, diff.toString());
            final ContractDriftException exception =
                    assertThrows(
                            ContractDriftException.class,
                            () -> validator.validateResponse(observed));
            assertEquals(ContractDriftException.Reason.BREAKING_CHANGE, exception.reason());
        }
    }

    @Test
    void acceptsOnlyExactAllowlistedOptionalAdditionsWithAlert() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractValidator classifier = validator(release, List.of());
        final ContractMetadata metadataWithExtra =
                appendMetadata(
                        release.metadata(),
                        new ContractMetadata.Element(
                                ContractMetadata.ElementKind.DATA_FIELD,
                                "extra",
                                ContractMetadata.DeclaredType.STRING));
        final ContractResponse responseWithExtra =
                appendField(
                        release,
                        ContractTestSupport.field(
                                "/extra",
                                ContractResponse.Presence.OPTIONAL,
                                false,
                                ContractResponse.JsonType.STRING));
        final ContractChange metadataChange =
                classifier.classifyMetadata(metadataWithExtra).changes().get(0);
        final ContractChange responseChange =
                classifier.classifyResponse(responseWithExtra).changes().get(0);
        final List<ContractAllowance> allowances =
                List.of(
                        ContractAllowance.forChange(responseChange),
                        ContractAllowance.forChange(metadataChange));
        final ContractCompatibilityPolicy policy = policy(release, allowances);
        final ContractValidator validator = new ContractValidator(release, policy);

        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                validator.validateMetadata(metadataWithExtra).status());
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                validator.validateResponse(responseWithExtra).status());
        assertEquals(2, policy.allowances().size());
        assertFalse(metadataChange.toString().contains("extra"));
        assertFalse(responseChange.toString().contains("extra"));

        final List<ContractAllowance> reversed = new ArrayList<>(allowances);
        Collections.reverse(reversed);
        assertEquals(policy.fingerprint(), policy(release, reversed).fingerprint());
    }

    @Test
    void acceptsExpectedNullOnlyThroughAnExactVersionedRule() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractResponse nullableStatus =
                replaceField(
                        release,
                        ContractTestSupport.field(
                                "/status",
                                ContractResponse.Presence.REQUIRED,
                                true,
                                ContractResponse.JsonType.STRING));
        final ContractValidator classifier = validator(release, List.of());
        final ContractChange change = classifier.classifyResponse(nullableStatus).changes().get(0);

        final ContractDriftException unapproved =
                assertThrows(
                        ContractDriftException.class,
                        () -> classifier.validateResponse(nullableStatus));
        assertEquals(
                ContractDriftException.Reason.UNAPPROVED_COMPATIBLE_CHANGE, unapproved.reason());
        assertEquals(1, unapproved.compatibleChanges());

        final ContractValidator allowed =
                validator(release, List.of(ContractAllowance.forChange(change)));
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                allowed.validateResponse(nullableStatus).status());

        final ContractResponse allNullStatus =
                replaceField(
                        release,
                        new ContractResponse.Field(
                                ContractResponse.FieldScope.RECORD,
                                "/status",
                                ContractResponse.Cardinality.UNOBSERVED,
                                ContractResponse.Presence.REQUIRED,
                                true,
                                List.of()));
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                allowed.validateResponse(allNullStatus).status());
    }

    @Test
    void staleOrForgedRulesDoNotMatchAndCriticalChangesCannotEnterTheAllowlist() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractValidator classifier = validator(release, List.of());
        final ContractResponse optionalExtra =
                appendField(
                        release,
                        ContractTestSupport.field(
                                "/extra",
                                ContractResponse.Presence.OPTIONAL,
                                false,
                                ContractResponse.JsonType.STRING));
        final ContractChange compatible =
                classifier.classifyResponse(optionalExtra).changes().get(0);
        final ContractAllowance stale =
                new ContractAllowance(
                        compatible.component(),
                        compatible.kind(),
                        compatible.responseFieldScope(),
                        compatible.approvedResponseField(),
                        compatible.path(),
                        new ImmutableFingerprint("contract-change-v2", "0".repeat(64)));
        final ContractResponse missingRequired =
                response(
                        release,
                        release.response().recordRoot(),
                        release.response().rootCardinality(),
                        release.response().keyPath(),
                        List.of(field(release, "/id"), field(release, "/note")));
        final ContractChange breaking =
                classifier.classifyResponse(missingRequired).changes().get(0);

        final ContractDriftException exception =
                assertThrows(
                        ContractDriftException.class,
                        () -> validator(release, List.of(stale)).validateResponse(optionalExtra));
        assertEquals(
                ContractDriftException.Reason.UNAPPROVED_COMPATIBLE_CHANGE, exception.reason());
        assertFalse(stale.toString().contains(stale.path()));
        assertFalse(stale.toString().contains(stale.changeSignature().sha256()));
        assertThrows(IllegalArgumentException.class, () -> ContractAllowance.forChange(breaking));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ContractAllowance(
                                breaking.component(),
                                breaking.kind(),
                                breaking.responseFieldScope(),
                                breaking.approvedResponseField(),
                                breaking.path(),
                                breaking.signature()));
    }

    @Test
    void responseAllowanceRequiresAndBindsTheExactApprovedFieldDescriptor() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractChange change =
                validator(release, List.of())
                        .classifyResponse(
                                appendField(
                                        release,
                                        ContractTestSupport.field(
                                                "/extra",
                                                ContractResponse.Presence.OPTIONAL,
                                                false,
                                                ContractResponse.JsonType.STRING)))
                        .changes()
                        .get(0);
        final ContractAllowance exact = ContractAllowance.forChange(change);
        assertEquals(change.approvedResponseField(), exact.approvedResponseField());
        assertTrue(exact.matches(change));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ContractAllowance(
                                change.component(),
                                change.kind(),
                                change.responseFieldScope(),
                                java.util.Optional.empty(),
                                change.path(),
                                change.signature()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ContractAllowance(
                                change.component(),
                                change.kind(),
                                change.responseFieldScope(),
                                java.util.Optional.of(
                                        new ContractResponse.Field(
                                                ContractResponse.FieldScope.ENVELOPE,
                                                "/extra",
                                                ContractResponse.Cardinality.SCALAR,
                                                ContractResponse.Presence.OPTIONAL,
                                                false,
                                                List.of(ContractResponse.JsonType.STRING))),
                                change.path(),
                                change.signature()));

        final ContractAllowance differentShape =
                new ContractAllowance(
                        change.component(),
                        change.kind(),
                        change.responseFieldScope(),
                        java.util.Optional.of(
                                ContractTestSupport.field(
                                        "/extra",
                                        ContractResponse.Presence.OPTIONAL,
                                        true,
                                        ContractResponse.JsonType.STRING)),
                        change.path(),
                        change.signature());
        assertFalse(differentShape.matches(change));
        assertNotEquals(
                policy(release, List.of(exact)).fingerprint(),
                policy(release, List.of(differentShape)).fingerprint());
    }

    @Test
    void policyIsBoundedUniqueTamperEvidentAndBoundToItsBaseline() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractValidator classifier = validator(release, List.of());
        final ContractChange change =
                classifier
                        .classifyResponse(
                                appendField(
                                        release,
                                        ContractTestSupport.field(
                                                "/extra",
                                                ContractResponse.Presence.OPTIONAL,
                                                false,
                                                ContractResponse.JsonType.STRING)))
                        .changes()
                        .get(0);
        final ContractAllowance allowance = ContractAllowance.forChange(change);
        final ContractCompatibilityPolicy valid = policy(release, List.of(allowance));

        assertThrows(
                IllegalArgumentException.class,
                () -> policy(release, List.of(allowance, allowance)));
        final List<ContractAllowance> excessive = new ArrayList<>();
        for (int index = 0; index <= ContractCompatibilityPolicy.MAXIMUM_ALLOWANCES; index++) {
            excessive.add(
                    new ContractAllowance(
                            ContractChange.Component.RESPONSE,
                            ContractChange.Kind.OPTIONAL_FIELD_ADDED,
                            java.util.Optional.of(ContractResponse.FieldScope.RECORD),
                            java.util.Optional.of(
                                    ContractTestSupport.field(
                                            "/extra" + index,
                                            ContractResponse.Presence.OPTIONAL,
                                            false,
                                            ContractResponse.JsonType.STRING)),
                            "/extra" + index,
                            new ImmutableFingerprint(
                                    "contract-change-v2", String.format("%064x", index))));
        }
        assertThrows(IllegalArgumentException.class, () -> policy(release, excessive));
        final List<ContractAllowance> nonIterableOversizedAllowances =
                new AbstractList<>() {
                    @Override
                    public ContractAllowance get(final int index) {
                        throw new AssertionError("A lista acima do teto não deve ser iterada.");
                    }

                    @Override
                    public int size() {
                        return ContractCompatibilityPolicy.MAXIMUM_ALLOWANCES + 1;
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () -> policy(release, nonIterableOversizedAllowances));
        final List<ContractOpaquePath> nonIterableOversizedOpaquePaths =
                new AbstractList<>() {
                    @Override
                    public ContractOpaquePath get(final int index) {
                        throw new AssertionError("A lista acima do teto não deve ser iterada.");
                    }

                    @Override
                    public int size() {
                        return ContractCompatibilityPolicy.MAXIMUM_OPAQUE_PATHS + 1;
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContractCompatibilityPolicy.create(
                                "policy-v1",
                                release.contractFingerprint(),
                                List.of(),
                                nonIterableOversizedOpaquePaths));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ContractCompatibilityPolicy(
                                valid.policyVersion(),
                                valid.baselineContract(),
                                valid.allowances(),
                                valid.opaquePaths(),
                                new ImmutableFingerprint("forged", "f".repeat(64))));
        final SourceContractRelease otherRelease =
                SourceContractRelease.create(
                        release.sourceKind(),
                        release.documentReference(),
                        "other-version",
                        release.metadata(),
                        release.response());
        assertThrows(
                ContractDriftException.class, () -> new ContractValidator(otherRelease, valid));
    }

    @Test
    void inheritsOptionalityForANewConditionalSubtreeButBlocksAnUnprovenAddition()
            throws Exception {
        final ContractResponse baselineResponse =
                profile("[{@id@:@a@},{@id@:@b@}]".replace('@', '"'));
        final SourceContractRelease release = release(baselineResponse);
        final ContractResponse conditional =
                profile("[{@id@:@a@,@extra@:{@code@:@x@}},{@id@:@b@}]".replace('@', '"'));
        final ContractValidator classifier = validator(release, List.of());
        final ContractDiff conditionalDiff = classifier.classifyResponse(conditional);

        assertEquals(2, conditionalDiff.compatibleCount());
        assertEquals(0, conditionalDiff.breakingCount());
        assertTrue(
                conditionalDiff.changes().stream()
                        .allMatch(
                                change ->
                                        change.kind() == ContractChange.Kind.OPTIONAL_FIELD_ADDED));
        assertEquals(
                ContractDriftException.Reason.UNAPPROVED_COMPATIBLE_CHANGE,
                assertThrows(
                                ContractDriftException.class,
                                () -> classifier.validateResponse(conditional))
                        .reason());
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                validator(
                                release,
                                conditionalDiff.changes().stream()
                                        .map(ContractAllowance::forChange)
                                        .toList())
                        .validateResponse(conditional)
                        .status());

        final ContractResponse unproven =
                profile(
                        "[{@id@:@a@,@extra@:{@code@:@x@}},{@id@:@b@,@extra@:{@code@:@y@}}]"
                                .replace('@', '"'));
        final ContractDiff breaking = classifier.classifyResponse(unproven);
        assertTrue(breaking.breakingCount() > 0);
        assertTrue(
                breaking.changes().stream()
                        .anyMatch(
                                change ->
                                        change.kind()
                                                == ContractChange.Kind
                                                        .REQUIRED_OR_UNPROVEN_FIELD_ADDED));
    }

    @Test
    void treatsTheFirstObservedArrayElementShapeAsAnExactCompatibleChange() throws Exception {
        final ContractResponse baselineResponse =
                profile("[{@id@:@a@,@tags@:[]},{@id@:@b@,@tags@:[]}]".replace('@', '"'));
        final SourceContractRelease release = release(baselineResponse);
        final ContractResponse observed =
                profile(
                        "[{@id@:@a@,@tags@:[{@code@:@x@}]},{@id@:@b@,@tags@:[{@code@:@y@}]}]"
                                .replace('@', '"'));
        final ContractValidator classifier = validator(release, List.of());
        final ContractDiff diff = classifier.classifyResponse(observed);

        assertFalse(baselineResponse.find("/tags/*").isPresent());
        assertEquals(2, diff.compatibleCount());
        assertEquals(0, diff.breakingCount());
        assertTrue(
                diff.changes().stream()
                        .allMatch(
                                change ->
                                        change.kind() == ContractChange.Kind.OPTIONAL_FIELD_ADDED));
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                validator(
                                release,
                                diff.changes().stream().map(ContractAllowance::forChange).toList())
                        .validateResponse(observed)
                        .status());

        final ContractResponse differentElementType =
                profile("[{@id@:@a@,@tags@:[1]},{@id@:@b@,@tags@:[2]}]".replace('@', '"'));
        assertEquals(
                ContractDriftException.Reason.UNAPPROVED_COMPATIBLE_CHANGE,
                assertThrows(
                                ContractDriftException.class,
                                () ->
                                        validator(
                                                        release,
                                                        diff.changes().stream()
                                                                .map(ContractAllowance::forChange)
                                                                .toList())
                                                .validateResponse(differentElementType))
                        .reason());
    }

    @Test
    void treatsTheFirstArrayElementInsideAMixedFieldAsExactCompatibleEvidence() throws Exception {
        final ContractResponse baselineResponse =
                profile("[{@id@:@a@,@tags@:[]},{@id@:@b@,@tags@:@legacy@}]".replace('@', '"'));
        final SourceContractRelease release = release(baselineResponse);
        final ContractResponse observed =
                profile("[{@id@:@a@,@tags@:[@x@]},{@id@:@b@,@tags@:@legacy@}]".replace('@', '"'));
        final ContractValidator classifier = validator(release, List.of());
        final ContractDiff diff = classifier.classifyResponse(observed);

        assertEquals(
                ContractResponse.Cardinality.MIXED,
                baselineResponse.find("/tags").orElseThrow().cardinality());
        assertEquals(1, diff.compatibleCount());
        assertEquals(0, diff.breakingCount());
        assertEquals(ContractChange.Kind.OPTIONAL_FIELD_ADDED, diff.changes().get(0).kind());
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                validator(release, List.of(ContractAllowance.forChange(diff.changes().get(0))))
                        .validateResponse(observed)
                        .status());

        final ContractResponse populatedBaseline =
                profile("[{@id@:@a@,@tags@:[@x@]},{@id@:@b@,@tags@:@legacy@}]".replace('@', '"'));
        final SourceContractRelease populatedRelease = release(populatedBaseline);
        final ContractResponse emptyArrayObserved =
                profile("[{@id@:@a@,@tags@:[]},{@id@:@b@,@tags@:@legacy@}]".replace('@', '"'));
        assertEquals(
                0,
                validator(populatedRelease, List.of())
                        .classifyResponse(emptyArrayObserved)
                        .breakingCount());
    }

    @Test
    void requiresAnExactRuleForTheFirstShapeAfterANullOnlyBaseline() throws Exception {
        final ContractResponse baselineResponse =
                profile("[{@id@:@a@,@note@:null},{@id@:@b@,@note@:null}]".replace('@', '"'));
        final SourceContractRelease release = release(baselineResponse);
        final ContractResponse strings =
                profile("[{@id@:@a@,@note@:@x@},{@id@:@b@,@note@:@y@}]".replace('@', '"'));
        final ContractValidator classifier = validator(release, List.of());
        final ContractDiff stringDiff = classifier.classifyResponse(strings);

        assertEquals(1, stringDiff.compatibleCount());
        assertEquals(
                ContractChange.Kind.PREVIOUSLY_UNOBSERVED_SHAPE_OBSERVED,
                stringDiff.changes().get(0).kind());
        final ContractAllowance stringAllowance =
                ContractAllowance.forChange(stringDiff.changes().get(0));
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                validator(release, List.of(stringAllowance)).validateResponse(strings).status());

        final ContractResponse numbers =
                profile("[{@id@:@a@,@note@:1},{@id@:@b@,@note@:2}]".replace('@', '"'));
        assertEquals(
                ContractDriftException.Reason.UNAPPROVED_COMPATIBLE_CHANGE,
                assertThrows(
                                ContractDriftException.class,
                                () ->
                                        validator(release, List.of(stringAllowance))
                                                .validateResponse(numbers))
                        .reason());

        final ContractResponse partlyMissing =
                profile("[{@id@:@a@,@note@:@x@},{@id@:@b@}]".replace('@', '"'));
        assertTrue(classifier.classifyResponse(partlyMissing).breakingCount() > 0);
    }

    @Test
    void rejectsObservedMetadataFromTheWrongSourceDomain() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractMetadata wrongDomain =
                new ContractMetadata(
                        List.of(
                                new ContractMetadata.Element(
                                        ContractMetadata.ElementKind.GRAPHQL_SELECTION,
                                        "/id",
                                        ContractMetadata.DeclaredType.STRING)),
                        java.util.Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> validator(release, List.of()).classifyMetadata(wrongDomain));
    }

    private static ContractValidator validator(
            final SourceContractRelease release, final List<ContractAllowance> allowances) {
        return new ContractValidator(release, policy(release, allowances));
    }

    private static SourceContractRelease release(final ContractResponse response) {
        return SourceContractRelease.create(
                ContractSourceKind.DATA_EXPORT,
                "dataexport-synthetic-profiler",
                "synthetic-v1",
                ContractTestSupport.metadata(),
                response);
    }

    private static ContractResponse profile(final String json) throws Exception {
        return PROFILER.profile(
                OBJECT_MAPPER.readTree(json), "$", ContractResponse.Cardinality.ARRAY, "/id");
    }

    private static ContractCompatibilityPolicy policy(
            final SourceContractRelease release, final List<ContractAllowance> allowances) {
        return ContractCompatibilityPolicy.create(
                "policy-v1", release.contractFingerprint(), allowances);
    }

    private static ContractMetadata metadataWithout(
            final ContractMetadata metadata,
            final ContractMetadata.ElementKind kind,
            final String path) {
        return new ContractMetadata(
                metadata.elements().stream()
                        .filter(element -> element.kind() != kind || !element.path().equals(path))
                        .toList(),
                metadata.approvedDocument());
    }

    private static ContractMetadata replaceMetadata(
            final ContractMetadata metadata, final ContractMetadata.Element replacement) {
        final List<ContractMetadata.Element> elements =
                metadata.elements().stream()
                        .filter(
                                element ->
                                        element.kind() != replacement.kind()
                                                || !element.path().equals(replacement.path()))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        elements.add(replacement);
        return new ContractMetadata(elements, metadata.approvedDocument());
    }

    private static ContractMetadata appendMetadata(
            final ContractMetadata metadata, final ContractMetadata.Element addition) {
        final List<ContractMetadata.Element> elements = new ArrayList<>(metadata.elements());
        elements.add(addition);
        return new ContractMetadata(elements, metadata.approvedDocument());
    }

    private static ContractResponse replaceField(
            final SourceContractRelease release, final ContractResponse.Field replacement) {
        final List<ContractResponse.Field> fields =
                release.response().fields().stream()
                        .filter(
                                field ->
                                        field.scope() != replacement.scope()
                                                || !field.path().equals(replacement.path()))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        fields.add(replacement);
        return response(
                release,
                release.response().recordRoot(),
                release.response().rootCardinality(),
                release.response().keyPath(),
                fields);
    }

    private static ContractResponse appendField(
            final SourceContractRelease release, final ContractResponse.Field addition) {
        final List<ContractResponse.Field> fields = new ArrayList<>(release.response().fields());
        fields.add(addition);
        return response(
                release,
                release.response().recordRoot(),
                release.response().rootCardinality(),
                release.response().keyPath(),
                fields);
    }

    private static ContractResponse response(
            final SourceContractRelease release,
            final String root,
            final ContractResponse.Cardinality cardinality,
            final String keyPath,
            final List<ContractResponse.Field> fields) {
        final List<ContractResponse.Field> normalized = new ArrayList<>();
        for (final ContractResponse.Field field : fields) {
            if (field.scope() != ContractResponse.FieldScope.ENVELOPE
                    || !field.path().equals(release.response().recordRoot())) {
                normalized.add(field);
            }
        }
        if (!"$".equals(root)) {
            normalized.add(
                    new ContractResponse.Field(
                            ContractResponse.FieldScope.ENVELOPE,
                            root,
                            cardinality,
                            ContractResponse.Presence.REQUIRED,
                            false,
                            List.of(
                                    cardinality == ContractResponse.Cardinality.ARRAY
                                            ? ContractResponse.JsonType.ARRAY
                                            : ContractResponse.JsonType.OBJECT)));
        } else {
            normalized.removeIf(field -> field.scope() == ContractResponse.FieldScope.ENVELOPE);
        }
        return new ContractResponse(
                root,
                cardinality,
                ContractResponse.ObservationState.POPULATED,
                keyPath,
                normalized);
    }

    private static ContractResponse.Field field(
            final SourceContractRelease release, final String path) {
        return release.response().find(path).orElseThrow();
    }
}
