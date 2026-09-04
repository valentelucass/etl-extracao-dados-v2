package br.com.esl.etl.v2.plataforma.contrato;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Classifica o diff e aplica a allowlist exata de mudanças compatíveis. */
public final class ContractValidator {

    private final SourceContractRelease baseline;
    private final ContractCompatibilityPolicy policy;

    public ContractValidator(
            final SourceContractRelease baseline, final ContractCompatibilityPolicy policy) {
        this.baseline = Objects.requireNonNull(baseline, "O release baseline é obrigatório.");
        this.policy = Objects.requireNonNull(policy, "A política de contrato é obrigatória.");
        if (!baseline.contractFingerprint().equals(policy.baselineContract())) {
            throw new ContractDriftException(ContractDriftException.Reason.BASELINE_MISMATCH, 0, 0);
        }
    }

    public ContractDiff classifyMetadata(final ContractMetadata observed) {
        Objects.requireNonNull(observed, "A metadata observada é obrigatória.");
        SourceContractRelease.validateDomain(baseline.sourceKind(), observed);
        final List<ContractChange> changes = new ArrayList<>();
        final Map<String, ContractMetadata.Element> expected =
                metadataByIdentity(baseline.metadata());
        final Map<String, ContractMetadata.Element> actual = metadataByIdentity(observed);

        if (!baseline.metadata().approvedDocument().equals(observed.approvedDocument())) {
            add(
                    changes,
                    metadataChange(
                            ContractChange.Kind.APPROVED_DOCUMENT_CHANGED,
                            ContractChange.Severity.BREAKING,
                            "approved-document",
                            observed.approvedDocument()
                                    .map(
                                            document ->
                                                    document.fingerprint().version()
                                                            + ':'
                                                            + document.fingerprint().sha256())
                                    .orElse("absent")));
        }

        for (final Map.Entry<String, ContractMetadata.Element> entry : expected.entrySet()) {
            final ContractMetadata.Element actualElement = actual.get(entry.getKey());
            if (actualElement == null) {
                final ContractMetadata.Element expectedElement = entry.getValue();
                add(
                        changes,
                        metadataChange(
                                isFilter(expectedElement)
                                        ? ContractChange.Kind.FILTER_CHANGED
                                        : ContractChange.Kind.FIELD_REMOVED,
                                ContractChange.Severity.BREAKING,
                                expectedElement.path(),
                                "absent:"
                                        + ContractCanonicalizer.metadataSignature(
                                                expectedElement)));
            } else if (actualElement.declaredType() != entry.getValue().declaredType()
                    || !actualElement
                            .declaredTypeFingerprint()
                            .equals(entry.getValue().declaredTypeFingerprint())) {
                add(
                        changes,
                        metadataChange(
                                isFilter(actualElement)
                                        ? ContractChange.Kind.FILTER_CHANGED
                                        : ContractChange.Kind.DECLARED_TYPE_CHANGED,
                                ContractChange.Severity.BREAKING,
                                actualElement.path(),
                                ContractCanonicalizer.metadataSignature(actualElement)));
            }
        }

        for (final Map.Entry<String, ContractMetadata.Element> entry : actual.entrySet()) {
            if (!expected.containsKey(entry.getKey())) {
                final ContractMetadata.Element element = entry.getValue();
                final boolean filter = isFilter(element);
                add(
                        changes,
                        metadataChange(
                                filter
                                        ? ContractChange.Kind.FILTER_CHANGED
                                        : ContractChange.Kind.OPTIONAL_FIELD_ADDED,
                                filter
                                        ? ContractChange.Severity.BREAKING
                                        : ContractChange.Severity.COMPATIBLE,
                                element.path(),
                                ContractCanonicalizer.metadataSignature(element)));
            }
        }
        return new ContractDiff(changes);
    }

    public ContractDiff classifyResponse(final ContractResponse observed) {
        Objects.requireNonNull(observed, "A resposta observada é obrigatória.");
        final List<ContractChange> changes = new ArrayList<>();
        classifyRootAndKey(observed, changes);

        final Map<String, ContractResponse.Field> expected =
                responseByIdentity(baseline.response());
        final Map<String, ContractResponse.Field> actual =
                normalizePageLocalSampling(expected, responseByIdentity(observed));
        for (final Map.Entry<String, ContractResponse.Field> entry : expected.entrySet()) {
            final ContractResponse.Field expectedField = entry.getValue();
            if (observed.observationState() == ContractResponse.ObservationState.EMPTY
                    && expectedField.scope() == ContractResponse.FieldScope.RECORD) {
                continue;
            }
            final String path = reportedPath(expectedField);
            final ContractResponse.Field actualField = actual.get(entry.getKey());
            if (actualField == null) {
                if (expectedField.presence() == ContractResponse.Presence.REQUIRED
                        && missingFieldIsConclusive(expectedField, expected, actual)) {
                    add(
                            changes,
                            responseFieldChange(
                                    expectedField.scope(),
                                    ContractChange.Kind.REQUIRED_FIELD_MISSING,
                                    ContractChange.Severity.BREAKING,
                                    path,
                                    "absent:"
                                            + ContractCanonicalizer.fieldSignature(expectedField)));
                }
                continue;
            }
            classifyKnownField(path, expectedField, actualField, changes);
        }

        for (final Map.Entry<String, ContractResponse.Field> entry : actual.entrySet()) {
            if (!expected.containsKey(entry.getKey())) {
                final ContractResponse.Field field = entry.getValue();
                final boolean compatible = compatibleAddition(field, expected, actual);
                if (compatible) {
                    add(
                            changes,
                            responseFieldChange(
                                    ContractChange.Kind.OPTIONAL_FIELD_ADDED,
                                    ContractChange.Severity.COMPATIBLE,
                                    field));
                } else {
                    add(
                            changes,
                            responseFieldChange(
                                    field.scope(),
                                    ContractChange.Kind.REQUIRED_OR_UNPROVEN_FIELD_ADDED,
                                    ContractChange.Severity.BREAKING,
                                    reportedPath(field),
                                    ContractCanonicalizer.fieldSignature(field)));
                }
            }
        }
        return new ContractDiff(changes);
    }

    private Map<String, ContractResponse.Field> normalizePageLocalSampling(
            final Map<String, ContractResponse.Field> expected,
            final Map<String, ContractResponse.Field> observed) {
        final Map<String, ContractResponse.Field> normalized = new HashMap<>(observed);
        for (final Map.Entry<String, ContractResponse.Field> entry : observed.entrySet()) {
            final ContractResponse.Field field = entry.getValue();
            if (!expected.containsKey(entry.getKey())) {
                policy.approvedFieldForPageObservation(
                                ContractChange.Kind.OPTIONAL_FIELD_ADDED, field)
                        .ifPresent(approved -> normalized.put(entry.getKey(), approved));
            }
        }
        for (final Map.Entry<String, ContractResponse.Field> entry : expected.entrySet()) {
            final ContractResponse.Field baselineField = entry.getValue();
            final ContractResponse.Field observedField = normalized.get(entry.getKey());
            if (observedField == null) {
                continue;
            }
            if (baselineField.cardinality() == ContractResponse.Cardinality.UNOBSERVED) {
                policy.approvedFieldForPageObservation(
                                ContractChange.Kind.PREVIOUSLY_UNOBSERVED_SHAPE_OBSERVED,
                                observedField)
                        .ifPresent(approved -> normalized.put(entry.getKey(), approved));
            } else if (!baselineField.nullable() && observedField.nullable()) {
                policy.approvedFieldForPageObservation(
                                ContractChange.Kind.NULLABILITY_WIDENED, observedField)
                        .ifPresent(approved -> normalized.put(entry.getKey(), approved));
            }
        }
        return Map.copyOf(normalized);
    }

    public ContractValidationResult validateMetadata(final ContractMetadata observed) {
        return enforce(classifyMetadata(observed));
    }

    public ContractValidationResult validateResponse(final ContractResponse observed) {
        return enforce(classifyResponse(observed));
    }

    private ContractValidationResult enforce(final ContractDiff diff) {
        if (diff.breakingCount() != 0) {
            throw new ContractDriftException(
                    ContractDriftException.Reason.BREAKING_CHANGE,
                    diff.breakingCount(),
                    diff.compatibleCount());
        }
        final long unapproved =
                diff.changes().stream().filter(change -> !policy.allows(change)).count();
        if (unapproved != 0) {
            throw new ContractDriftException(
                    ContractDriftException.Reason.UNAPPROVED_COMPATIBLE_CHANGE, 0, unapproved);
        }
        return new ContractValidationResult(
                diff.changes().isEmpty()
                        ? ContractValidationResult.Status.ACCEPTED
                        : ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                diff);
    }

    private void classifyRootAndKey(
            final ContractResponse observed, final List<ContractChange> changes) {
        if (!baseline.response().recordRoot().equals(observed.recordRoot())
                || baseline.response().rootCardinality() != observed.rootCardinality()) {
            add(
                    changes,
                    responseChange(
                            ContractChange.Kind.ROOT_CHANGED,
                            ContractChange.Severity.BREAKING,
                            "/root",
                            observed.recordRoot() + ':' + observed.rootCardinality().name()));
        }
        if (!baseline.response().keyPath().equals(observed.keyPath())) {
            add(
                    changes,
                    responseChange(
                            ContractChange.Kind.KEY_CHANGED,
                            ContractChange.Severity.BREAKING,
                            "/key",
                            observed.keyPath()));
            return;
        }
        if (observed.observationState() == ContractResponse.ObservationState.POPULATED) {
            final ContractResponse.Field expectedKey =
                    baseline.response().find(baseline.response().keyPath()).orElseThrow();
            final ContractResponse.Field key = observed.find(observed.keyPath()).orElse(null);
            if (key == null
                    || key.presence() != ContractResponse.Presence.REQUIRED
                    || key.nullable()
                    || key.cardinality() != ContractResponse.Cardinality.SCALAR
                    || key.jsonTypes().isEmpty()
                    || !expectedKey.jsonTypes().containsAll(key.jsonTypes())) {
                add(
                        changes,
                        responseChange(
                                ContractChange.Kind.KEY_CHANGED,
                                ContractChange.Severity.BREAKING,
                                "/key",
                                key == null
                                        ? "absent"
                                        : ContractCanonicalizer.fieldSignature(key)));
            }
        }
    }

    private void classifyKnownField(
            final String path,
            final ContractResponse.Field expected,
            final ContractResponse.Field actual,
            final List<ContractChange> changes) {
        if (actual.cardinality() == ContractResponse.Cardinality.UNOBSERVED) {
            if (expected.presence() == ContractResponse.Presence.REQUIRED
                    && actual.presence() == ContractResponse.Presence.OPTIONAL) {
                add(
                        changes,
                        responseFieldChange(
                                expected.scope(),
                                ContractChange.Kind.REQUIRED_FIELD_MISSING,
                                ContractChange.Severity.BREAKING,
                                path,
                                ContractCanonicalizer.fieldSignature(actual)));
            }
            if (!expected.nullable()) {
                add(
                        changes,
                        responseFieldChange(
                                ContractChange.Kind.NULLABILITY_WIDENED,
                                ContractChange.Severity.COMPATIBLE,
                                actual));
            }
            return;
        }
        if (expected.cardinality() == ContractResponse.Cardinality.UNOBSERVED) {
            if (expected.presence() == ContractResponse.Presence.REQUIRED
                    && actual.presence() == ContractResponse.Presence.OPTIONAL) {
                add(
                        changes,
                        responseFieldChange(
                                expected.scope(),
                                ContractChange.Kind.REQUIRED_FIELD_MISSING,
                                ContractChange.Severity.BREAKING,
                                path,
                                ContractCanonicalizer.fieldSignature(actual)));
            }
            add(
                    changes,
                    responseFieldChange(
                            ContractChange.Kind.PREVIOUSLY_UNOBSERVED_SHAPE_OBSERVED,
                            ContractChange.Severity.COMPATIBLE,
                            actual));
            return;
        }
        if (!cardinalityCompatible(expected.cardinality(), actual.cardinality())) {
            add(
                    changes,
                    responseFieldChange(
                            expected.scope(),
                            ContractChange.Kind.CARDINALITY_CHANGED,
                            ContractChange.Severity.BREAKING,
                            path,
                            ContractCanonicalizer.fieldSignature(actual)));
        }
        if (!expected.jsonTypes().containsAll(actual.jsonTypes())) {
            add(
                    changes,
                    responseFieldChange(
                            expected.scope(),
                            ContractChange.Kind.TYPE_CHANGED,
                            ContractChange.Severity.BREAKING,
                            path,
                            ContractCanonicalizer.fieldSignature(actual)));
        }
        if (expected.presence() == ContractResponse.Presence.REQUIRED
                && actual.presence() == ContractResponse.Presence.OPTIONAL
                && !path.endsWith("/*")) {
            add(
                    changes,
                    responseFieldChange(
                            expected.scope(),
                            ContractChange.Kind.REQUIRED_FIELD_MISSING,
                            ContractChange.Severity.BREAKING,
                            path,
                            ContractCanonicalizer.fieldSignature(actual)));
        }
        if (!expected.nullable() && actual.nullable()) {
            add(
                    changes,
                    responseFieldChange(
                            ContractChange.Kind.NULLABILITY_WIDENED,
                            ContractChange.Severity.COMPATIBLE,
                            actual));
        }
    }

    private ContractChange metadataChange(
            final ContractChange.Kind kind,
            final ContractChange.Severity severity,
            final String path,
            final String structuralSignature) {
        return change(
                ContractChange.Component.METADATA,
                kind,
                severity,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                path,
                structuralSignature);
    }

    private ContractChange responseChange(
            final ContractChange.Kind kind,
            final ContractChange.Severity severity,
            final String path,
            final String structuralSignature) {
        return change(
                ContractChange.Component.RESPONSE,
                kind,
                severity,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                path,
                structuralSignature);
    }

    private ContractChange responseFieldChange(
            final ContractResponse.FieldScope scope,
            final ContractChange.Kind kind,
            final ContractChange.Severity severity,
            final String path,
            final String structuralSignature) {
        return change(
                ContractChange.Component.RESPONSE,
                kind,
                severity,
                java.util.Optional.of(scope),
                java.util.Optional.empty(),
                path,
                structuralSignature);
    }

    private ContractChange responseFieldChange(
            final ContractChange.Kind kind,
            final ContractChange.Severity severity,
            final ContractResponse.Field approvedField) {
        return change(
                ContractChange.Component.RESPONSE,
                kind,
                severity,
                java.util.Optional.of(approvedField.scope()),
                java.util.Optional.of(approvedField),
                reportedPath(approvedField),
                ContractCanonicalizer.fieldSignature(approvedField));
    }

    private ContractChange change(
            final ContractChange.Component component,
            final ContractChange.Kind kind,
            final ContractChange.Severity severity,
            final java.util.Optional<ContractResponse.FieldScope> responseFieldScope,
            final java.util.Optional<ContractResponse.Field> approvedResponseField,
            final String path,
            final String structuralSignature) {
        return new ContractChange(
                component,
                kind,
                severity,
                responseFieldScope,
                approvedResponseField,
                path,
                ContractCanonicalizer.change(
                        baseline, component, kind, responseFieldScope, path, structuralSignature));
    }

    private static Map<String, ContractMetadata.Element> metadataByIdentity(
            final ContractMetadata metadata) {
        final Map<String, ContractMetadata.Element> indexed = new HashMap<>();
        for (final ContractMetadata.Element element : metadata.elements()) {
            indexed.put(element.kind().name() + '\u0000' + element.path(), element);
        }
        return indexed;
    }

    private static Map<String, ContractResponse.Field> responseByIdentity(
            final ContractResponse response) {
        final Map<String, ContractResponse.Field> indexed = new HashMap<>();
        for (final ContractResponse.Field field : response.fields()) {
            indexed.put(responseIdentity(field.scope(), field.path()), field);
        }
        return indexed;
    }

    private static String responseIdentity(
            final ContractResponse.FieldScope scope, final String path) {
        return scope.name() + '\u0000' + path;
    }

    private static String reportedPath(final ContractResponse.Field field) {
        return field.scope() == ContractResponse.FieldScope.ENVELOPE
                ? "/envelope" + field.path()
                : field.path();
    }

    private static boolean isFilter(final ContractMetadata.Element element) {
        return element.kind() == ContractMetadata.ElementKind.DATA_FILTER
                || element.kind() == ContractMetadata.ElementKind.GRAPHQL_ARGUMENT;
    }

    private static boolean missingFieldIsConclusive(
            final ContractResponse.Field field,
            final Map<String, ContractResponse.Field> baseline,
            final Map<String, ContractResponse.Field> observed) {
        final String path = field.path();
        final ContractResponse.FieldScope scope = field.scope();
        if (path.endsWith("/*")) {
            final ContractResponse.Field array =
                    observed.get(responseIdentity(scope, ContractText.parentPointer(path)));
            if (array != null && array.jsonTypes().contains(ContractResponse.JsonType.ARRAY)) {
                return false;
            }
        }
        String parent = ContractText.parentPointer(path);
        while (!parent.isEmpty()) {
            final ContractResponse.Field actualParent =
                    observed.get(responseIdentity(scope, parent));
            final ContractResponse.Field expectedParent =
                    baseline.get(responseIdentity(scope, parent));
            if (actualParent == null) {
                if (expectedParent == null
                        || expectedParent.presence() == ContractResponse.Presence.OPTIONAL
                        || parent.endsWith("/*")) {
                    return false;
                }
            } else if (actualParent.cardinality() == ContractResponse.Cardinality.UNOBSERVED
                    && actualParent.nullable()) {
                return false;
            }
            parent = ContractText.parentPointer(parent);
        }
        return true;
    }

    private static boolean compatibleAddition(
            final ContractResponse.Field field,
            final Map<String, ContractResponse.Field> baseline,
            final Map<String, ContractResponse.Field> observed) {
        if (field.presence() == ContractResponse.Presence.OPTIONAL) {
            return true;
        }
        final ContractResponse.FieldScope scope = field.scope();
        String child = field.path();
        String parent = ContractText.parentPointer(child);
        while (!parent.isEmpty()) {
            final ContractResponse.Field expectedParent =
                    baseline.get(responseIdentity(scope, parent));
            final ContractResponse.Field actualParent =
                    observed.get(responseIdentity(scope, parent));
            if (isConditionalParent(expectedParent) || isConditionalParent(actualParent)) {
                return true;
            }
            if (child.endsWith("/*")
                    && expectedParent != null
                    && expectedParent.jsonTypes().contains(ContractResponse.JsonType.ARRAY)
                    && !baseline.containsKey(responseIdentity(scope, child))) {
                return true;
            }
            child = parent;
            parent = ContractText.parentPointer(parent);
        }
        return false;
    }

    private static boolean isConditionalParent(final ContractResponse.Field field) {
        return field != null
                && (field.presence() == ContractResponse.Presence.OPTIONAL
                        || field.nullable()
                        || field.cardinality() == ContractResponse.Cardinality.MIXED);
    }

    private static boolean cardinalityCompatible(
            final ContractResponse.Cardinality expected,
            final ContractResponse.Cardinality actual) {
        return expected == actual
                || expected == ContractResponse.Cardinality.MIXED
                        && actual != ContractResponse.Cardinality.UNOBSERVED;
    }

    private static void add(final List<ContractChange> changes, final ContractChange change) {
        if (changes.size() >= ContractDiff.MAXIMUM_CHANGES) {
            throw new ContractDriftException(
                    ContractDriftException.Reason.DIFF_LIMIT_EXCEEDED, 0, 0);
        }
        changes.add(change);
    }
}
