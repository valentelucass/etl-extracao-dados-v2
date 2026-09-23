package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Qfnd02ClosedSchemaTest {

    private final Qfnd02Loader loader = new Qfnd02Loader();

    @Test
    void rejectsUnknownMembersMissingOrExtraPathsAndInventedProviderTypes() throws IOException {
        final String freight = resource("fretes-6389.profile.json");
        assertRejected(freight.replaceFirst("\\{", "{\"unknownMember\":true,"));
        assertRejected(freight.replace(",\n        \"/updated_at\"", ""));
        assertRejected(freight.replace("\"/updated_at\"", "\"/unexpected_path\""));
        assertRejected(freight.replaceFirst("\"UNVERIFIED_ORACLE_REQUIRED\"", "\"STRING\""));
    }

    @Test
    void rejectsWrongKeysSequenceNumberAndSourcedLegacyStatusBranch() throws IOException {
        final String location = resource("localizacao-8656.profile.json");
        assertRejected(
                location.replace("\"/corporation_sequence_number\"", "\"/sequence_number\""));
        assertRejected(
                location.replace("\"ABSENT_UNSOURCED_LEGACY\"", "\"SOURCED_FROM_SIMILAR_FIELD\""));
    }

    @Test
    void rejectsMixedOrAuthoritativeSidecarAndEnabledUnsafeCapabilities() throws IOException {
        final String sidecar = resource("fretes-6389.profile.json");
        assertRejected(sidecar.replace("\"GRAPHQL_SIDECAR\"", "\"DATA_EXPORT\""));
        assertRejected(
                sidecar.replace(
                        "\"rootOrFreshnessAuthority\": false",
                        "\"rootOrFreshnessAuthority\": true"));
        assertRejected(
                sidecar.replace("\"publicationBlocked\": true", "\"publicationBlocked\": false"));

        final String freight = sidecar;
        assertRejected(
                freight.replace("\"relationshipEnabled\": false", "\"relationshipEnabled\": true"));
        assertRejected(freight.replace("\"sweepEnabled\": false", "\"sweepEnabled\": true"));
        assertRejected(
                freight.replace("\"completenessProven\": false", "\"completenessProven\": true"));
        assertRejected(freight.replace("\"snapshotProven\": false", "\"snapshotProven\": true"));
        assertRejected(freight.replace("\"PREPARED_NOT_EXECUTED\"", "\"EXECUTED\""));
    }

    @Test
    void rejectsScalarAndEnumCoercionAtTheJsonBoundary() throws IOException {
        final String freight = resource("fretes-6389.profile.json");
        assertRejected(freight.replace("\"maximumPageSize\": 100", "\"maximumPageSize\": \"100\""));
        assertRejected(
                freight.replaceFirst("\"sourceProfile\": \"DATA_EXPORT\"", "\"sourceProfile\": 0"));
        assertRejected(
                freight.replace(
                        "\"explicitScopeRequired\": true", "\"explicitScopeRequired\": \"true\""));
    }

    private static String resource(final String file) throws IOException {
        try (var stream =
                Qfnd02ClosedSchemaTest.class.getResourceAsStream(
                        "/contracts/v2-012/extensions/q-fnd-02/profiles/" + file)) {
            assertTrue(stream != null, "Recurso de teste ausente: " + file);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertRejected(final String json) {
        assertThrows(IllegalArgumentException.class, () -> loader.parseProfile(json));
    }
}
