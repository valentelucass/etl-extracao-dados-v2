package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeEnvironment;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Typed preparation denials without a bundle, a physical capability or any source client. */
class RuntimePreparationBoundaryTest {
    private static final Instant NOW = Instant.parse("2036-03-19T18:42:10Z");
    @TempDir Path temporary;

    @Test
    void aNonObjectCannotBeInterpretedAsAnOperationalRequest() {
        assertEquals(
                "OPERATIONAL_REQUEST_SCHEMA",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        RuntimeOperationalRequest.fromDocument(
                                                disabledBase(RuntimeEnvironment.LOCAL_SHADOW),
                                                JsonNodeFactory.instance.arrayNode()))
                        .getMessage());
    }

    @Test
    void numericJsonCannotReplaceARequiredWireString() {
        final var document = document().put("pageSize", 3);
        assertEquals(
                "OPERATIONAL_REQUEST_STRING_REQUIRED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        RuntimeOperationalRequest.fromDocument(
                                                disabledBase(RuntimeEnvironment.LOCAL_SHADOW),
                                                document))
                        .getMessage());
    }

    @Test
    void oversizedRequestRefusesAtTheReadBoundAndPreservesTheOriginalFile() throws Exception {
        final var file = temporary.resolve("oversized.json");
        final byte[] bytes = " ".repeat(16385).getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);
        final var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                RuntimeOperationalRequest.read(
                                        disabledBase(RuntimeEnvironment.LOCAL_SHADOW), file));
        assertEquals("OPERATIONAL_REQUEST_INVALID", failure.getMessage());
        assertEquals("OPERATIONAL_REQUEST_LIMIT", failure.getCause().getMessage());
        assertEquals(16385, Files.size(file));
        assertEquals(new String(bytes, StandardCharsets.UTF_8), Files.readString(file));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMILLISECOND", "SWEEP"})
    void invalidOperationalWindowRefusesBeforeMissingSourceConfiguration(final String invalid) {
        final var document = document();
        if ("SWEEP".equals(invalid)) {
            document.put("mode", "SWEEP");
        } else {
            document.put("start", NOW.plusNanos(1).toString());
        }
        assertEquals(
                "OPERATIONAL_WINDOW_INVALID",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        RuntimeOperationalRequest.fromDocument(
                                                disabledBase(RuntimeEnvironment.LOCAL_SHADOW),
                                                document))
                        .getMessage());
    }

    @Test
    void validDataExportShapeCannotCreateARequestWithoutSourceConfiguration() {
        assertEquals(
                "DATAEXPORT_CONFIGURATION_REQUIRED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        RuntimeOperationalRequest.fromDocument(
                                                disabledBase(RuntimeEnvironment.LOCAL_SHADOW),
                                                document()))
                        .getMessage());
    }

    @Test
    void quotesRequireAPositiveExplicitReferenceBeforeResolvingSourceContracts() {
        final var document = document().put("template", "COTACOES").put("referenceReleaseId", "0");
        assertEquals(
                "EXPLICIT_TARIFF_RELEASE_REQUIRED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        RuntimeOperationalRequest.fromDocument(
                                                disabledBase(RuntimeEnvironment.LOCAL_SHADOW),
                                                document))
                        .getMessage());
    }

    @Test
    void backfillOnlyVerticalCannotUseIncrementalModeBeforeResolvingItsProfile() {
        final var document = document().put("template", "MANIFESTOS").put("mode", "INCREMENTAL");
        assertEquals(
                "VERTICAL_BACKFILL_ONLY",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        RuntimeOperationalRequest.fromDocument(
                                                disabledBase(RuntimeEnvironment.LOCAL_SHADOW),
                                                document))
                        .getMessage());
    }

    @Test
    void usersPreparationDoesNotCreateADataExportPageOrGrantAnotherProtocol() throws Exception {
        final var request =
                RuntimeOperationalRequest.fromDocument(
                        RuntimeUsersOperationalRequestTest.configuration(),
                        RuntimeUsersOperationalRequestTest.document());
        assertEquals(
                "USERS_HAS_NO_DATA_EXPORT_REQUEST",
                assertThrows(IllegalStateException.class, request::firstPage).getMessage());
        assertFalse(request.toString().contains("source.example.test"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OTHER_ENVIRONMENT", "ACTIVE_STORAGE"})
    void c78CompositionCannotInheritAnotherEnvironmentOrAnAlreadyActiveStorage(
            final String invalid) {
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        final boolean activeStorage = "ACTIVE_STORAGE".equals(invalid);
        final var base =
                new RuntimeConfiguration(
                        activeStorage
                                ? RuntimeEnvironment.LOCAL_SHADOW
                                : RuntimeEnvironment.NON_PRODUCTION_SHADOW,
                        ZoneId.of("America/Sao_Paulo"),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Optional.empty(),
                        Optional.empty(),
                        activeStorage
                                ? ShadowStorageProperties.enabledC78Local(target)
                                : ShadowStorageProperties.disabled());
        assertEquals(
                "DEV_SOURCE_BASE_WITHOUT_STORAGE_REQUIRED",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> RuntimeDevPreparedInvocation.composeC78(base, target))
                        .getMessage());
        assertEquals(activeStorage, base.shadowStorage().auditEnabled());
    }

    @Test
    void anOrdinaryPreparedRequestCannotTurnWindowsMetadataIntoC78Admission() throws Exception {
        final var configuration = RuntimeUsersOperationalRequestTest.configuration();
        final var request =
                RuntimeOperationalRequest.fromDocument(
                        configuration, RuntimeUsersOperationalRequestTest.document());
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        assertEquals(
                "DEV_C78_EFFECTIVE_TARGET_REQUIRED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new RuntimeDevPreparedInvocation(
                                                configuration, request, target))
                        .getMessage());
        assertFalse(configuration.shadowStorage().usesC78LocalAuthentication());
    }

    private static RuntimeConfiguration disabledBase(final RuntimeEnvironment environment) {
        return new RuntimeConfiguration(
                environment,
                ZoneId.of("America/Sao_Paulo"),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Optional.empty(),
                Optional.empty(),
                ShadowStorageProperties.disabled());
    }

    private static ObjectNode document() {
        return JsonNodeFactory.instance
                .objectNode()
                .put("invocationId", UUID.randomUUID().toString())
                .put("executionId", UUID.randomUUID().toString())
                .put("cycleId", UUID.randomUUID().toString())
                .put("template", "COLETAS")
                .put("mode", "BACKFILL")
                .put("start", NOW.toString())
                .put("endExclusive", NOW.plusSeconds(3600).toString())
                .put("replayOf", "")
                .put("idempotencyKey", UUID.randomUUID().toString())
                .put("businessStart", "2036-03-19")
                .put("businessEnd", "2036-03-19")
                .put("leaseSeconds", "60")
                .put("pageSize", "3")
                .put("maximumPages", "4")
                .put("maximumRows", "100")
                .put("maximumDistinctRoots", "100")
                .put("qualityVersion", "synthetic-quality-1")
                .put("qualityFingerprint", "b".repeat(64))
                .put("compatibilityVersion", "strict-1");
    }
}
