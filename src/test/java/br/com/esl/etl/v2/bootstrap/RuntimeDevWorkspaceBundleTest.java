package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTlsScope;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeEnvironment;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeDevWorkspaceBundleTest {
    @Test
    void manifestCannotClaimSqlSourceOrRootAuthorityEvenWithValidLookingPins() throws Exception {
        final var mapper = new ObjectMapper();
        for (final String name : List.of("authorityAdmitted", "sourceAdmitted", "sqlAdmitted")) {
            final var root = mapper.readTree(publicStructure());
            ((com.fasterxml.jackson.databind.node.ObjectNode) root).put(name, true);
            assertEquals(
                    "DEV_WORKSPACE_BUNDLE_IS_NOT_AUTHORITY",
                    assertThrows(
                                    RuntimeDevWorkspaceBundle.Failure.class,
                                    () ->
                                            RuntimeDevWorkspaceBundle.parse(
                                                    mapper.writeValueAsBytes(root)))
                            .getMessage());
        }
    }

    @Test
    void duplicateTraversalForeignLeafAndOversizeDocumentsAreRefused() throws Exception {
        final String valid = publicStructure();
        final var mapper = new ObjectMapper();
        assertThrows(
                RuntimeDevWorkspaceBundle.Failure.class,
                () ->
                        RuntimeDevWorkspaceBundle.parse(
                                valid.replace("\"schema\":", "\"schema\":\"duplicate\",\"schema\":")
                                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                RuntimeDevWorkspaceBundle.Failure.class,
                () ->
                        RuntimeDevWorkspaceBundle.parse(
                                valid.replace("lib/control.jar", "lib/../control.jar")
                                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                RuntimeDevWorkspaceBundle.Failure.class,
                () ->
                        RuntimeDevWorkspaceBundle.parse(
                                valid.replace(C78LocalTlsScope.LEAF_SHA256, "0".repeat(64))
                                        .getBytes(StandardCharsets.UTF_8)));
        final var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(valid);
        root.withArray("files").add(root.get("files").get(0).deepCopy());
        assertThrows(
                RuntimeDevWorkspaceBundle.Failure.class,
                () -> RuntimeDevWorkspaceBundle.parse(mapper.writeValueAsBytes(root)));
        assertThrows(
                RuntimeDevWorkspaceBundle.Failure.class,
                () -> RuntimeDevWorkspaceBundle.parse(new byte[65_537]));
    }

    @Test
    void effectiveConfigurationIsC78BeforeAnyRequestOrFingerprintIsDerived() {
        final var base =
                new RuntimeConfiguration(
                        RuntimeEnvironment.LOCAL_SHADOW,
                        ZoneId.of("America/Sao_Paulo"),
                        Clock.systemUTC(),
                        Optional.empty(),
                        Optional.empty(),
                        ShadowStorageProperties.disabled());
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        final var effective = RuntimeDevPreparedInvocation.composeC78(base, target);
        assertFalse(base.shadowStorage().auditEnabled());
        assertTrue(effective.shadowStorage().auditEnabled());
        assertTrue(effective.shadowStorage().usesC78LocalAuthentication());
        assertFalse(effective.shadowStorage().usesIntegratedSecurity());
        assertFalse(effective.shadowStorage().usesSampleSaAuthentication());
        assertEquals(target.jdbcUrl(), effective.shadowStorage().jdbcUrl());
    }

    private static String publicStructure() throws Exception {
        return new ObjectMapper()
                .writeValueAsString(
                        Map.of(
                                "schema",
                                RuntimeDevWorkspaceBundle.SCHEMA,
                                "mode",
                                "PREPARE_ONLY",
                                "profile",
                                C78LocalSqlTarget.PROFILE,
                                "target",
                                "localhost:1433/ETL_SISTEMA_V2_SHADOW",
                                "leafSHA256",
                                C78LocalTlsScope.LEAF_SHA256,
                                "authorityAdmitted",
                                false,
                                "sourceAdmitted",
                                false,
                                "sqlAdmitted",
                                false,
                                "files",
                                List.of(
                                        Map.of(
                                                "path",
                                                RuntimeDevWorkspaceBundle.JAR,
                                                "sha256",
                                                "0".repeat(64)),
                                        Map.of(
                                                "path",
                                                RuntimeDevWorkspaceBundle.CONFIGURATION,
                                                "sha256",
                                                "0".repeat(64)),
                                        Map.of(
                                                "path",
                                                RuntimeDevWorkspaceBundle.REQUEST,
                                                "sha256",
                                                "0".repeat(64)),
                                        Map.of(
                                                "path",
                                                "lib/control.jar",
                                                "sha256",
                                                "0".repeat(64)))));
    }
}
