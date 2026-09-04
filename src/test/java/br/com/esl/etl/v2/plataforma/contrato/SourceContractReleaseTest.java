package br.com.esl.etl.v2.plataforma.contrato;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SourceContractReleaseTest {

    @Test
    void canonicalizationIsDeterministicAndIndependentOfCollectionOrder() {
        final SourceContractRelease release = ContractTestSupport.release();
        final List<ContractMetadata.Element> reversedMetadata =
                new ArrayList<>(release.metadata().elements());
        final List<ContractResponse.Field> reversedFields =
                new ArrayList<>(release.response().fields());
        Collections.reverse(reversedMetadata);
        Collections.reverse(reversedFields);

        final SourceContractRelease reordered =
                SourceContractRelease.create(
                        release.sourceKind(),
                        release.documentReference(),
                        release.contractVersion(),
                        new ContractMetadata(reversedMetadata, Optional.empty()),
                        new ContractResponse(
                                release.response().recordRoot(),
                                release.response().rootCardinality(),
                                release.response().observationState(),
                                release.response().keyPath(),
                                reversedFields));

        assertEquals(release.metadataFingerprint(), reordered.metadataFingerprint());
        assertEquals(release.responseFingerprint(), reordered.responseFingerprint());
        assertEquals(release.contractFingerprint(), reordered.contractFingerprint());
        assertEquals("source-metadata-v1", release.metadataFingerprint().version());
        assertEquals("source-response-v1", release.responseFingerprint().version());
        assertEquals(release.contractVersion(), release.contractFingerprint().version());
        assertEquals(64, release.contractFingerprint().sha256().length());
    }

    @Test
    void canonicalizationRejectsMalformedUtf16AndPreservesSupplementaryCodePoints() {
        for (final String malformed :
                List.of("\uD800", "\uD801", "\uDC00", "\uDC00\uD800", "\uD800\uD800")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ContractCanonicalizer.declaredTypeText(malformed));
        }

        final String validSupplementary = "synthetic-\uD83D\uDE00-type";
        assertEquals(
                ContractCanonicalizer.declaredTypeText(validSupplementary),
                ContractCanonicalizer.declaredTypeText(validSupplementary));
        assertNotEquals(
                ContractCanonicalizer.declaredTypeText(validSupplementary),
                ContractCanonicalizer.declaredTypeText("synthetic-?-type"));
    }

    @Test
    void everySemanticComponentAndContractVersionAffectTheReleaseFingerprint() {
        final SourceContractRelease baseline = ContractTestSupport.release();
        final ContractMetadata changedMetadata =
                new ContractMetadata(
                        List.of(
                                new ContractMetadata.Element(
                                        ContractMetadata.ElementKind.DATA_FIELD,
                                        "id",
                                        ContractMetadata.DeclaredType.INTEGER),
                                baseline.metadata().elements().get(1),
                                baseline.metadata().elements().get(2)),
                        Optional.empty());
        final ContractResponse changedResponse =
                new ContractResponse(
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/id",
                        baseline.response().fields().stream()
                                .filter(
                                        field ->
                                                field.scope() == ContractResponse.FieldScope.RECORD)
                                .toList());

        final SourceContractRelease metadataRelease =
                SourceContractRelease.create(
                        baseline.sourceKind(),
                        baseline.documentReference(),
                        baseline.contractVersion(),
                        changedMetadata,
                        baseline.response());
        final SourceContractRelease responseRelease =
                SourceContractRelease.create(
                        baseline.sourceKind(),
                        baseline.documentReference(),
                        baseline.contractVersion(),
                        baseline.metadata(),
                        changedResponse);
        final SourceContractRelease versionRelease =
                SourceContractRelease.create(
                        baseline.sourceKind(),
                        baseline.documentReference(),
                        "2026-08-30.2",
                        baseline.metadata(),
                        baseline.response());

        assertNotEquals(baseline.metadataFingerprint(), metadataRelease.metadataFingerprint());
        assertEquals(baseline.responseFingerprint(), metadataRelease.responseFingerprint());
        assertNotEquals(baseline.responseFingerprint(), responseRelease.responseFingerprint());
        assertNotEquals(baseline.contractFingerprint(), metadataRelease.contractFingerprint());
        assertNotEquals(baseline.contractFingerprint(), responseRelease.contractFingerprint());
        assertNotEquals(baseline.contractFingerprint(), versionRelease.contractFingerprint());
    }

    @Test
    void canonicalFingerprintsMatchTheVersionedGoldenVectors() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractCompatibilityPolicy policy = ContractTestSupport.policy(release);
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        java.util.UUID.fromString("00000000-0000-0000-0000-000000000044"),
                        release,
                        policy,
                        new ImmutableFingerprint("runtime-v1", "d".repeat(64)));
        final ApprovedGraphQlDocument document =
                ApprovedGraphQlDocument.approve(
                        "query Individual($enabled: Boolean!) { individual(enabled: $enabled) { id } }");
        final ContractResponsePathBoundary boundary =
                ContractResponsePathBoundary.forRuntime(release, policy);

        org.junit.jupiter.api.Assertions.assertAll(
                () ->
                        assertEquals(
                                "7abd78ecc44c12edf1c2828bf5677d6d1fe4df7e48462d8c4248af766c357d46",
                                release.metadataFingerprint().sha256()),
                () ->
                        assertEquals(
                                "0ba42c10f129f8dd422cce872444f94ed2e8976c5ebe2c4999ea67cecaeea311",
                                release.responseFingerprint().sha256()),
                () ->
                        assertEquals(
                                "31a8b537ba618a7106dd79bdbae4287ff7363b48baf12bdb7845e1033d6beceb",
                                release.contractFingerprint().sha256()),
                () ->
                        assertEquals(
                                "371f3246337ccc46d82e387d198493595070916ad816cee4b4e48e9ae8b2de5c",
                                policy.fingerprint().sha256()),
                () ->
                        assertEquals(
                                "3745187bb96d724aadccd667785df26afe58c4f8bd4a3a3bb01c6c66b4d76cd0",
                                document.fingerprint().sha256()),
                () ->
                        assertEquals(
                                "1af51fd55c9c67a2e188c76a0294245c276b1e91c5a998e938c9fc0c252a0d25",
                                binding.configurationFingerprint().sha256()),
                () ->
                        assertEquals(
                                "3f6772f01700acc7befa8c09e1a6ac721f5288349c2b10e658d2ea4d476596ad",
                                boundary.fingerprint().sha256()));
    }

    @Test
    void nonEmptyPolicyGoldenBindsResponseScopeAllowanceAndOpaqueObject() {
        final SourceContractRelease base = ContractTestSupport.release();
        final List<ContractResponse.Field> baselineFields =
                new ArrayList<>(base.response().fields());
        baselineFields.add(
                new ContractResponse.Field(
                        ContractResponse.FieldScope.RECORD,
                        "/attributes",
                        ContractResponse.Cardinality.OBJECT,
                        ContractResponse.Presence.REQUIRED,
                        false,
                        List.of(ContractResponse.JsonType.OBJECT)));
        final SourceContractRelease release =
                SourceContractRelease.create(
                        base.sourceKind(),
                        base.documentReference(),
                        base.contractVersion(),
                        base.metadata(),
                        new ContractResponse(
                                base.response().recordRoot(),
                                base.response().rootCardinality(),
                                base.response().observationState(),
                                base.response().keyPath(),
                                baselineFields));
        final List<ContractResponse.Field> candidateFields = new ArrayList<>(baselineFields);
        candidateFields.add(
                ContractTestSupport.field(
                        "/extra",
                        ContractResponse.Presence.OPTIONAL,
                        false,
                        ContractResponse.JsonType.STRING));
        final ContractResponse candidate =
                new ContractResponse(
                        release.response().recordRoot(),
                        release.response().rootCardinality(),
                        release.response().observationState(),
                        release.response().keyPath(),
                        candidateFields);
        final ContractChange change =
                new ContractValidator(release, ContractTestSupport.policy(release))
                        .classifyResponse(candidate)
                        .changes()
                        .get(0);
        final ContractCompatibilityPolicy policy =
                ContractCompatibilityPolicy.create(
                        "policy-golden-v1",
                        release.contractFingerprint(),
                        List.of(ContractAllowance.forChange(change)),
                        List.of(
                                new ContractOpaquePath(
                                        ContractResponse.FieldScope.RECORD, "/attributes")));
        final ContractResponsePathBoundary boundary =
                ContractResponsePathBoundary.forRuntime(release, policy);

        assertEquals(
                java.util.Optional.of(ContractResponse.FieldScope.RECORD),
                change.responseFieldScope());
        org.junit.jupiter.api.Assertions.assertAll(
                () ->
                        assertEquals(
                                "d0996725b20b2698cbe56b46ebd7e646f8449ebcf38f34baa0e9a7e676f7a2de",
                                change.signature().sha256()),
                () ->
                        assertEquals(
                                "199bc9e231731200a26e2ec3417b5d2413574310d7c972f1b508a02415134848",
                                policy.fingerprint().sha256()),
                () ->
                        assertEquals(
                                "ede2c831c20e3d2f6b9dba72e41a5a2b7dbc9dd1d2b6d88795d5bffdacf15657",
                                boundary.fingerprint().sha256()));

        final ContractAllowance recordScoped =
                new ContractAllowance(
                        change.component(),
                        change.kind(),
                        Optional.of(ContractResponse.FieldScope.RECORD),
                        Optional.of(
                                new ContractResponse.Field(
                                        ContractResponse.FieldScope.RECORD,
                                        "/envelope/extra",
                                        ContractResponse.Cardinality.SCALAR,
                                        ContractResponse.Presence.OPTIONAL,
                                        false,
                                        List.of(ContractResponse.JsonType.STRING))),
                        "/envelope/extra",
                        change.signature());
        final ContractAllowance envelopeScoped =
                new ContractAllowance(
                        change.component(),
                        change.kind(),
                        Optional.of(ContractResponse.FieldScope.ENVELOPE),
                        Optional.of(
                                new ContractResponse.Field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/extra",
                                        ContractResponse.Cardinality.SCALAR,
                                        ContractResponse.Presence.OPTIONAL,
                                        false,
                                        List.of(ContractResponse.JsonType.STRING))),
                        "/envelope/extra",
                        change.signature());
        final ContractCompatibilityPolicy recordPolicy =
                ContractCompatibilityPolicy.create(
                        "policy-scope-v1", release.contractFingerprint(), List.of(recordScoped));
        final ContractCompatibilityPolicy envelopePolicy =
                ContractCompatibilityPolicy.create(
                        "policy-scope-v1", release.contractFingerprint(), List.of(envelopeScoped));
        assertNotEquals(recordPolicy.fingerprint(), envelopePolicy.fingerprint());
        assertNotEquals(
                ContractResponsePathBoundary.forRuntime(release, recordPolicy).fingerprint(),
                ContractResponsePathBoundary.forRuntime(release, envelopePolicy).fingerprint());
    }

    @Test
    void detectsAnyFingerprintTamperingAtConstruction() {
        final SourceContractRelease release = ContractTestSupport.release();
        final ImmutableFingerprint forged = new ImmutableFingerprint("forged", "f".repeat(64));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceContractRelease(
                                release.sourceKind(),
                                release.documentReference(),
                                release.contractVersion(),
                                release.metadata(),
                                release.response(),
                                forged,
                                release.responseFingerprint(),
                                release.contractFingerprint()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceContractRelease(
                                release.sourceKind(),
                                release.documentReference(),
                                release.contractVersion(),
                                release.metadata(),
                                release.response(),
                                release.metadataFingerprint(),
                                forged,
                                release.contractFingerprint()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceContractRelease(
                                release.sourceKind(),
                                release.documentReference(),
                                release.contractVersion(),
                                release.metadata(),
                                release.response(),
                                release.metadataFingerprint(),
                                release.responseFingerprint(),
                                forged));
    }

    @Test
    void approvesOnlyNamedReadOnlyGraphQlDocumentsAndNormalizesBomAndLineEndings() {
        final ApprovedGraphQlDocument windows =
                ApprovedGraphQlDocument.approve(
                        "\uFEFFquery Individual($enabled: Boolean!) {\r\n"
                                + "  individual(enabled: $enabled) { id }\r\n}");
        final ApprovedGraphQlDocument unix =
                ApprovedGraphQlDocument.approve(
                        "query Individual($enabled: Boolean!) {\n"
                                + "  individual(enabled: $enabled) { id }\n}");
        final ApprovedGraphQlDocument differentWhitespace =
                ApprovedGraphQlDocument.approve(
                        "query Individual($enabled: Boolean!) { individual(enabled: $enabled) { id } }");

        assertEquals(windows, unix);
        assertEquals(windows, differentWhitespace);
        assertEquals("graphql-document-v1", windows.fingerprint().version());
        assertFalse(windows.toString().contains("individual(enabled"));

        for (final String rejected :
                List.of(
                        "{ individual { id } }",
                        "mutation Update { update { id } }",
                        "subscription Events { events { id } }",
                        "query Schema { __schema { types { name } } }",
                        "query Type { __type(name: \"X\") { name } }",
                        "query Nested { viewer { __typename } }",
                        "query Bad($enabled: Boolean = true) { individual(enabled: $enabled) { id } }",
                        "query Bad { individual(enabled: true) { id } }",
                        "query Bad($enabled: Boolean!) { individual(enabled: $missing) { id } }",
                        "query Bad($enabled: Boolean!, $enabled: Boolean!) { individual(enabled: $enabled) { id } }",
                        "query Bad { alias: individual { id } }",
                        "query Bad { individual { ...Fields } } fragment Fields on Individual { id }",
                        "query Bad { individual { id } } # comment",
                        "query\u00A0Bad { individual { id } }",
                        "query Bad { field\u0000 }")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ApprovedGraphQlDocument.approve(rejected));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ApprovedGraphQlDocument.approve(
                                "query TooLarge { "
                                        + "a".repeat(ApprovedGraphQlDocument.MAXIMUM_UTF8_BYTES)
                                        + " }"));
    }

    @Test
    void buildsAnOfflineGraphQlReleaseAndRejectsCrossDomainMetadata() {
        final ApprovedGraphQlDocument document =
                ApprovedGraphQlDocument.approve(
                        "query Individual($enabled: Boolean!) { individual(enabled: $enabled) { id } }");
        final ContractMetadata metadata =
                new ContractMetadata(
                        List.of(
                                new ContractMetadata.Element(
                                        ContractMetadata.ElementKind.GRAPHQL_SELECTION,
                                        "/individual/id",
                                        ContractMetadata.DeclaredType.STRING),
                                new ContractMetadata.Element(
                                        ContractMetadata.ElementKind.GRAPHQL_ARGUMENT,
                                        "/individual/enabled",
                                        ContractMetadata.DeclaredType.BOOLEAN)),
                        Optional.of(document));
        final ContractResponse response =
                new ContractResponse(
                        "/data/individual",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/id",
                        List.of(
                                new ContractResponse.Field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data",
                                        ContractResponse.Cardinality.OBJECT,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        List.of(ContractResponse.JsonType.OBJECT)),
                                new ContractResponse.Field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data/individual",
                                        ContractResponse.Cardinality.ARRAY,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        List.of(ContractResponse.JsonType.ARRAY)),
                                ContractTestSupport.field(
                                        "/id",
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        ContractResponse.JsonType.STRING)));

        final SourceContractRelease release =
                SourceContractRelease.create(
                        ContractSourceKind.GRAPHQL,
                        "graphql-individual",
                        "query-v1",
                        metadata,
                        response);

        assertEquals(ContractSourceKind.GRAPHQL, release.sourceKind());
        assertTrue(release.metadata().approvedDocument().isPresent());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SourceContractRelease.create(
                                ContractSourceKind.DATA_EXPORT,
                                "dataexport-synthetic",
                                "v1",
                                metadata,
                                response));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SourceContractRelease.create(
                                ContractSourceKind.GRAPHQL,
                                "graphql-individual",
                                "v1",
                                ContractTestSupport.metadata(),
                                response));
    }

    @Test
    void refusesAnEmptyBaselineOrAnInvalidKeyContract() {
        final ContractResponse empty =
                new ContractResponse(
                        "/data",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.EMPTY,
                        "/id",
                        List.of(
                                new ContractResponse.Field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data",
                                        ContractResponse.Cardinality.ARRAY,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        List.of(ContractResponse.JsonType.ARRAY))));
        final ContractResponse nullableKey =
                new ContractResponse(
                        "/data",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/id",
                        List.of(
                                new ContractResponse.Field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data",
                                        ContractResponse.Cardinality.ARRAY,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        List.of(ContractResponse.JsonType.ARRAY)),
                                ContractTestSupport.field(
                                        "/id",
                                        ContractResponse.Presence.REQUIRED,
                                        true,
                                        ContractResponse.JsonType.STRING)));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SourceContractRelease.create(
                                ContractSourceKind.DATA_EXPORT,
                                "dataexport-synthetic",
                                "v1",
                                ContractTestSupport.metadata(),
                                empty));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SourceContractRelease.create(
                                ContractSourceKind.DATA_EXPORT,
                                "dataexport-synthetic",
                                "v1",
                                ContractTestSupport.metadata(),
                                nullableKey));
    }

    @Test
    void acceptsAnExplicitClosedScalarUnionWithoutClaimingIdentityCanonicalization() {
        final ContractResponse unionKey =
                new ContractResponse(
                        "/data",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/id",
                        List.of(
                                new ContractResponse.Field(
                                        ContractResponse.FieldScope.ENVELOPE,
                                        "/data",
                                        ContractResponse.Cardinality.ARRAY,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        List.of(ContractResponse.JsonType.ARRAY)),
                                new ContractResponse.Field(
                                        "/id",
                                        ContractResponse.Cardinality.SCALAR,
                                        ContractResponse.Presence.REQUIRED,
                                        false,
                                        List.of(
                                                ContractResponse.JsonType.STRING,
                                                ContractResponse.JsonType.INTEGER))));

        final SourceContractRelease release =
                SourceContractRelease.create(
                        ContractSourceKind.DATA_EXPORT,
                        "dataexport-synthetic",
                        "v1-union-key",
                        ContractTestSupport.metadata(),
                        unionKey);

        assertEquals(
                List.of(ContractResponse.JsonType.STRING, ContractResponse.JsonType.INTEGER),
                release.response().find("/id").orElseThrow().jsonTypes());
    }

    @Test
    void normalizesOnlyClosedDeclaredTypeCategories() {
        assertEquals(
                ContractMetadata.DeclaredType.UNDECLARED,
                ContractMetadata.DeclaredType.from(Optional.empty()));
        assertEquals(
                ContractMetadata.DeclaredType.DATE_TIME,
                ContractMetadata.DeclaredType.from(Optional.of("timestamp")));
        assertEquals(
                ContractMetadata.DeclaredType.DATE,
                ContractMetadata.DeclaredType.from(Optional.of("date")));
        assertEquals(
                ContractMetadata.DeclaredType.BOOLEAN,
                ContractMetadata.DeclaredType.from(Optional.of("bool")));
        assertEquals(
                ContractMetadata.DeclaredType.INTEGER,
                ContractMetadata.DeclaredType.from(Optional.of("long")));
        assertEquals(
                ContractMetadata.DeclaredType.NUMBER,
                ContractMetadata.DeclaredType.from(Optional.of("decimal")));
        assertEquals(
                ContractMetadata.DeclaredType.ARRAY,
                ContractMetadata.DeclaredType.from(Optional.of("list")));
        assertEquals(
                ContractMetadata.DeclaredType.OBJECT,
                ContractMetadata.DeclaredType.from(Optional.of("json")));
        assertEquals(
                ContractMetadata.DeclaredType.STRING,
                ContractMetadata.DeclaredType.from(Optional.of("varchar")));
        assertEquals(
                ContractMetadata.DeclaredType.OTHER,
                ContractMetadata.DeclaredType.from(Optional.of("opaque")));
        assertEquals(
                ContractMetadata.DeclaredType.OTHER,
                ContractMetadata.DeclaredType.from(Optional.of("notadate")));
        final ContractMetadata.Element varchar10 =
                ContractMetadata.Element.fromDeclaredType(
                        ContractMetadata.ElementKind.DATA_FIELD,
                        "value",
                        Optional.of("varchar(10)"));
        final ContractMetadata.Element varchar20 =
                ContractMetadata.Element.fromDeclaredType(
                        ContractMetadata.ElementKind.DATA_FIELD,
                        "value",
                        Optional.of("varchar(20)"));
        assertEquals(varchar10.declaredType(), varchar20.declaredType());
        assertNotEquals(varchar10.declaredTypeFingerprint(), varchar20.declaredTypeFingerprint());
        assertThrows(
                IllegalArgumentException.class,
                () -> ContractMetadata.DeclaredType.from(Optional.of("bad\nvalue")));
    }
}
