package br.com.esl.etl.v2.plataforma.identidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ScopedSourceIdentityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SOURCE = "esl-account-synthetic";
    private static final String TENANT = "tenant-synthetic-a";

    @Test
    void integerAndStringTokensNeverCollapseForUsers() throws IOException {
        final FirstWaveIdentityContract users =
                FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.USUARIOS);
        final ScopedSourceIdentity integer = from(users, "42");
        final ScopedSourceIdentity textual = from(users, "\"42\"");
        final ScopedSourceIdentity textualWithLeadingZero = from(users, "\"042\"");

        assertEquals(ScopedSourceIdentity.WireType.INTEGER, integer.sourceKey().wireType());
        assertEquals("INTEGER:42", integer.sourceKey().storageValue());
        assertEquals(ScopedSourceIdentity.WireType.STRING, textual.sourceKey().wireType());
        assertEquals("STRING:42", textual.sourceKey().storageValue());
        assertEquals("STRING:042", textualWithLeadingZero.sourceKey().storageValue());
        assertNotEquals(integer, textual);
        assertNotEquals(textual, textualWithLeadingZero);
    }

    @Test
    void integralCanonicalizationIsExactAndBoundToTheContract() throws IOException {
        final FirstWaveIdentityContract coletas =
                FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.COLETAS);
        final ScopedSourceIdentity negative = from(coletas, "-12");
        final ScopedSourceIdentity huge = from(coletas, "123456789012345678901234567890");

        assertEquals("INTEGER:-12", negative.sourceKey().storageValue());
        assertEquals("INTEGER:123456789012345678901234567890", huge.sourceKey().storageValue());
        assertReason(
                IdentityQuarantineException.Reason.INVALID_SOURCE_KEY_TYPE,
                () -> from(coletas, "\"12\""));
        assertReason(
                IdentityQuarantineException.Reason.INVALID_SOURCE_KEY_TYPE,
                () -> from(coletas, "12.5"));
        assertReason(
                IdentityQuarantineException.Reason.INVALID_SOURCE_KEY_TYPE,
                () -> from(coletas, "true"));
    }

    @Test
    void missingInvalidAndOversizedKeysFailClosedWithoutLeakingValues() throws IOException {
        final FirstWaveIdentityContract users =
                FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.USUARIOS);
        assertReason(
                IdentityQuarantineException.Reason.MISSING_SOURCE_KEY,
                () -> ScopedSourceIdentity.fromJson(SOURCE, TENANT, users, null));
        assertReason(
                IdentityQuarantineException.Reason.MISSING_SOURCE_KEY,
                () -> ScopedSourceIdentity.fromJson(SOURCE, TENANT, users, NullNode.instance));
        assertReason(
                IdentityQuarantineException.Reason.INVALID_SOURCE_KEY_TYPE,
                () -> from(users, "{}"));
        assertReason(
                IdentityQuarantineException.Reason.INVALID_SOURCE_KEY_VALUE,
                () -> from(users, "\"   \""));
        assertReason(
                IdentityQuarantineException.Reason.INVALID_SOURCE_KEY_VALUE,
                () -> from(users, "\" value \""));
        assertReason(
                IdentityQuarantineException.Reason.INVALID_SOURCE_KEY_VALUE,
                () -> from(users, "\"line\\nbreak\""));
        final String oversized =
                "x".repeat(ScopedSourceIdentity.SourceKey.MAXIMUM_STORAGE_CHARACTERS);
        assertReason(
                IdentityQuarantineException.Reason.SOURCE_KEY_TOO_LONG,
                () ->
                        ScopedSourceIdentity.fromJson(
                                SOURCE,
                                TENANT,
                                users,
                                MAPPER.getNodeFactory().textNode(oversized)));

        final String sensitiveMarker = "synthetic-sensitive-marker";
        final IdentityQuarantineException failure =
                assertThrows(
                        IdentityQuarantineException.class,
                        () ->
                                ScopedSourceIdentity.fromJson(
                                        SOURCE,
                                        TENANT,
                                        users,
                                        MAPPER.getNodeFactory()
                                                .textNode(" " + sensitiveMarker + " ")));
        assertFalse(failure.getMessage().contains(sensitiveMarker));
        assertFalse(failure.toString().contains(sensitiveMarker));
    }

    @Test
    void fullTupleIsCaseSensitiveAndIsolatesSourceTenantAndEntity() throws IOException {
        final FirstWaveIdentityContract coletas =
                FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.COLETAS);
        final ScopedSourceIdentity baseline = from(coletas, "7");
        final ScopedSourceIdentity otherSource =
                ScopedSourceIdentity.fromJson(
                        "esl-account-synthetic-b", TENANT, coletas, MAPPER.readTree("7"));
        final ScopedSourceIdentity otherTenant =
                ScopedSourceIdentity.fromJson(
                        SOURCE, "tenant-synthetic-b", coletas, MAPPER.readTree("7"));
        final ScopedSourceIdentity otherCase =
                ScopedSourceIdentity.fromJson(
                        SOURCE.toUpperCase(), TENANT, coletas, MAPPER.readTree("7"));
        final ScopedSourceIdentity frete =
                ScopedSourceIdentity.fromJson(
                        SOURCE,
                        TENANT,
                        FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.FRETES),
                        MAPPER.readTree("7"));

        assertNotEquals(baseline, otherSource);
        assertNotEquals(baseline, otherTenant);
        assertNotEquals(baseline, otherCase);
        assertNotEquals(baseline, frete);
        assertEquals(baseline, from(coletas, "7"));
        assertFalse(baseline.toString().contains(SOURCE));
        assertFalse(baseline.toString().contains(TENANT));
        assertFalse(baseline.sourceKey().toString().contains("INTEGER:7"));
    }

    @Test
    void namespaceRejectsImplicitGlobalScopesAndNormalization() throws IOException {
        final FirstWaveIdentityContract coletas =
                FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.COLETAS);
        for (final String forbidden : new String[] {"GLOBAL", "singleton", "Default"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ScopedSourceIdentity.fromJson(
                                    SOURCE, forbidden, coletas, MAPPER.readTree("7")));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScopedSourceIdentity.fromJson(
                                " source", TENANT, coletas, MAPPER.readTree("7")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScopedSourceIdentity.fromJson(SOURCE, "", coletas, MAPPER.readTree("7")));
        assertThrows(
                NullPointerException.class,
                () -> ScopedSourceIdentity.fromJson(SOURCE, TENANT, null, MAPPER.readTree("7")));
    }

    @Test
    void directSourceKeyConstructionCannotBypassTheCodec() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.INTEGER, "INTEGER:01"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.INTEGER, "STRING:1"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.STRING, "INTEGER:1"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.STRING, "STRING:"));
        assertThrows(
                NullPointerException.class,
                () -> new ScopedSourceIdentity.SourceKey(null, "INTEGER:1"));
    }

    private static ScopedSourceIdentity from(
            final FirstWaveIdentityContract contract, final String json) throws IOException {
        return ScopedSourceIdentity.fromJson(SOURCE, TENANT, contract, MAPPER.readTree(json));
    }

    private static void assertReason(
            final IdentityQuarantineException.Reason expected, final ThrowingCall call) {
        final IdentityQuarantineException failure =
                assertThrows(IdentityQuarantineException.class, call::execute);
        assertEquals(expected, failure.reason());
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void execute() throws Exception;
    }
}
