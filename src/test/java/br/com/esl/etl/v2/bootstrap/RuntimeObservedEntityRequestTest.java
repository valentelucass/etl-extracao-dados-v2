package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportObservedEntityContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRequestProfile;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RuntimeObservedEntityRequestTest {
    private static final Instant NOW = Instant.parse("2036-03-19T03:00:00Z");
    @TempDir Path directory;

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    void principalRequestBindsReleaseFiltersPerPolicyAndEffectiveMapper(
            final DataExportTemplate template) throws Exception {
        final var config = configuration("GET_WITH_BODY", "1");
        final var doc = document(template);
        final var request = RuntimeOperationalRequest.read(config, write(doc));
        assertEquals(DataExportObservedEntityContractCatalog.release(template), request.release);
        assertTrue(request.firstPage().orderBy().isEmpty());
        assertTrue(request.requestProfile.observed());
        assertFalse(request.laboratory());
        assertNotEquals(
                DataExportRuntimeConfigurationFingerprint.from(config.dataExport().orElseThrow()),
                request.binding.runtimeConfigurationFingerprint());
        assertEquals(
                request.requestProfile.runtimeFingerprint(
                        DataExportRuntimeConfigurationFingerprint.from(
                                config.dataExport().orElseThrow())),
                request.binding.runtimeConfigurationFingerprint());
        assertEquals(
                template == DataExportTemplate.LOCALIZACAO_CARGAS,
                request.requestProfile.rawRecordsRequired());
        doc.put("invocationId", UUID.randomUUID().toString());
        final var nextInvocation = RuntimeOperationalRequest.read(config, write(doc));
        assertEquals(request.plan.fingerprint(), nextInvocation.plan.fingerprint());
        assertEquals(
                request.binding.configurationFingerprint(),
                nextInvocation.binding.configurationFingerprint());
        doc.put("pageSize", "51");
        final var changedPer = RuntimeOperationalRequest.read(config, write(doc));
        assertNotEquals(request.plan.fingerprint(), changedPer.plan.fingerprint());
        assertNotEquals(
                request.binding.configurationFingerprint(),
                changedPer.binding.configurationFingerprint());
        doc.put("businessStart", "2036-03-18");
        final var changedFilter = RuntimeOperationalRequest.read(config, write(doc));
        assertNotEquals(
                changedPer.binding.configurationFingerprint(),
                changedFilter.binding.configurationFingerprint());
        assertSame(
                changedFilter.firstPage().profile(),
                changedFilter.firstPage().withPage(2).profile());
    }

    @Test
    void explicitUnknownCrossTemplateAndFreeFlagsAreRejectedWithoutFallback() throws Exception {
        final var config = configuration("GET_WITH_BODY", "1");
        final var doc = document(DataExportTemplate.MANIFESTOS);
        doc.put("sourceContractVersion", "unknown");
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.read(config, write(doc)));
        doc.put(
                "sourceContractVersion",
                DataExportObservedEntityContractCatalog.release(DataExportTemplate.COTACOES)
                        .contractVersion());
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.read(config, write(doc)));
        final var flag =
                document(DataExportTemplate.LOCALIZACAO_CARGAS).put("writePermission", "true");
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.read(config, write(flag)));
        final var wrongType =
                document(DataExportTemplate.LOCALIZACAO_CARGAS).put("sourceContractVersion", true);
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.read(config, write(wrongType)));
    }

    @Test
    void effectiveBodyAndOneAttemptAreRequiredAndFretesKeepsItsHistoricalBinding()
            throws Exception {
        final var doc = document(DataExportTemplate.LOCALIZACAO_CARGAS);
        final var query = configuration("GET_WITH_QUERY", "1");
        final var retry = configuration("GET_WITH_BODY", "2");
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.read(query, write(doc)));
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.read(retry, write(doc)));
        final var body = configuration("GET_WITH_BODY", "1");
        final var fretes =
                baseDocument(DataExportTemplate.FRETES)
                        .put("sourceContractVersion", "2026-10-24.fretes-observed-curl.v1");
        final var request = RuntimeOperationalRequest.read(body, write(fretes));
        assertSame(DataExportRequestProfile.LEGACY, request.requestProfile);
        assertEquals(List.of("id asc"), request.firstPage().orderBy());
        assertEquals(
                DataExportRuntimeConfigurationFingerprint.from(body.dataExport().orElseThrow()),
                request.binding.runtimeConfigurationFingerprint());
    }

    private RuntimeConfiguration configuration(final String transport, final String attempts)
            throws Exception {
        String text = Files.readString(Path.of("config/application.example.properties"));
        text =
                text.replace("dataexport.enabled=false", "dataexport.enabled=true")
                        .replace("shadow.audit.enabled=false", "shadow.audit.enabled=true")
                        .replaceAll("(?m)^# (dataexport\\.[^\\r\\n]+)$", "$1")
                        .replaceAll("(?m)^# (shadow\\.[^\\r\\n]+)$", "$1")
                        .replace("<host-autorizado>", "source.example.test")
                        .replace("<identificador-estavel-nao-secreto>", "synthetic-source")
                        .replace("<escopo-nao-secreto>", "synthetic-tenant")
                        .replace("127.0.0.1:<porta-local>", "localhost")
                        .replace("trustServerCertificate=true", "trustServerCertificate=false");
        final var file = directory.resolve(UUID.randomUUID() + ".properties");
        Files.writeString(file, text);
        return new RuntimeConfigurationFactory()
                .load(
                        file,
                        new Properties(),
                        Map.of(
                                "V2_DATAEXPORT_TRANSPORT",
                                transport,
                                "V2_DATAEXPORT_RETRY_MAX_ATTEMPTS",
                                attempts,
                                "V2_DATAEXPORT_RETRY_INITIAL_DELAY_MS",
                                "0",
                                "V2_DATAEXPORT_RETRY_MAX_DELAY_MS",
                                "0"),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ObjectNode document(final DataExportTemplate template) {
        return baseDocument(template)
                .put(
                        "sourceContractVersion",
                        DataExportObservedEntityContractCatalog.release(template)
                                .contractVersion());
    }

    private ObjectNode baseDocument(final DataExportTemplate template) {
        final var value =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("invocationId", UUID.randomUUID().toString())
                        .put("executionId", UUID.randomUUID().toString())
                        .put("cycleId", UUID.randomUUID().toString())
                        .put("template", template.name())
                        .put("mode", "BACKFILL")
                        .put("start", NOW.toString())
                        .put("endExclusive", NOW.plusSeconds(86400).toString())
                        .put("replayOf", "")
                        .put("idempotencyKey", UUID.randomUUID().toString())
                        .put("businessStart", "2036-03-19")
                        .put("businessEnd", "2036-03-19")
                        .put("leaseSeconds", "60")
                        .put("pageSize", "50")
                        .put("maximumPages", "4")
                        .put("maximumRows", "1000")
                        .put("maximumDistinctRoots", "100")
                        .put("qualityVersion", "synthetic-quality-v1")
                        .put("qualityFingerprint", "b".repeat(64))
                        .put("compatibilityVersion", "strict-v1");
        if (template == DataExportTemplate.COTACOES) {
            value.put("referenceReleaseId", "73");
        }
        return value;
    }

    private Path write(final JsonNode document) throws Exception {
        final var file = directory.resolve(UUID.randomUUID() + ".json");
        Files.writeString(file, document.toString());
        return file;
    }
}
