package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Offline-only. Writes only synthetic NONSECRET CONFIG, never source/mapper/projection bodies. */
final class ObservedCaptureDriverFixtures {
    private ObservedCaptureDriverFixtures() {}

    static final ObjectMapper JSON = new ObjectMapper();
    static final String BUSINESS_MARKER = "SYNTHETIC_BUSINESS_ONLY_IN_RAM_2036";

    static Path configuration(final Path directory) throws Exception {
        final Path path = directory.resolve("driver-nonsecret-runtime.properties");
        Files.writeString(
                path,
                """
                runtime.environment=LOCAL_SHADOW
                runtime.business-timezone=America/Sao_Paulo
                dataexport.enabled=true
                dataexport.base-url=https://synthetic.invalid/v1
                dataexport.source-instance=source-one
                dataexport.tenant-scope=tenant-one
                dataexport.timezone=America/Sao_Paulo
                dataexport.transport=GET_WITH_BODY
                dataexport.timeout-seconds=2
                dataexport.max-response-bytes=8388608
                dataexport.retry.max-attempts=1
                dataexport.retry.initial-delay-ms=0
                dataexport.retry.max-delay-ms=0
                dataexport.resilience.minimum-request-interval-ms=3000
                dataexport.resilience.max-in-flight=1
                dataexport.resilience.max-requests-per-cycle=20
                dataexport.resilience.max-requests-per-workload=5
                dataexport.resilience.step-timeout-seconds=180
                dataexport.resilience.cycle-timeout-seconds=1080
                dataexport.resilience.max-retry-after-ms=0
                dataexport.resilience.max-repartitions=0
                dataexport.resilience.circuit-failure-threshold=3
                dataexport.resilience.circuit-cooldown-seconds=1
                """,
                StandardCharsets.UTF_8);
        return path;
    }

    static ObservedExpansionCaptureDriver driver(
            final ObservedHttpTransportTestSupport.Fixture fixture, final Path configurationFile) {
        return driver(fixture, configurationFile, fixture.plan);
    }

    static ObservedExpansionCaptureDriver driver(
            final ObservedHttpTransportTestSupport.Fixture fixture,
            final Path configurationFile,
            final ObservedExpansionCapturePlan plan) {
        return ObservedExpansionCaptureDriver.loadAndPrepare(
                new RuntimeConfigurationFactory(),
                configurationFile,
                new Properties(),
                Map.of(),
                Clock.systemUTC(),
                fixture.factory,
                fixture.governor,
                fixture.cycle,
                fixture.cancellation,
                plan,
                DataExportHttpAttemptObserver.noop());
    }

    static ObjectNode row(final ObservedExpansionCaptureTemplate template) {
        return ObservedExpansionSchemaFixtures.row(template);
    }

    static String rows(final ObjectNode row, final int count) {
        return "[" + String.join(",", java.util.Collections.nCopies(count, row.toString())) + "]";
    }

    static void json(final ObservedHttpTransportTestSupport.Fixture fixture, final String text) {
        fixture.client.responses.add(
                ObservedHttpTransportTestSupport.Wire.json(text.getBytes(StandardCharsets.UTF_8)));
    }

    static void info(final ObservedHttpTransportTestSupport.Fixture fixture) {
        final ObjectNode document = JsonNodeFactory.instance.objectNode();
        final var fields = document.putArray("fields");
        final var filters = document.putArray("filters");
        final var metadata =
                ObservedExpansionSchemaFixtures.schema(fixture.plan.template()).metadata();
        for (final ContractMetadata.Element element : metadata.elements()) {
            final var target =
                    element.kind() == ContractMetadata.ElementKind.DATA_FIELD ? fields : filters;
            final var field = target.addObject().put("name", element.path());
            if (element.declaredType() != ContractMetadata.DeclaredType.UNDECLARED) {
                field.put("type", declaredType(element));
            }
        }
        json(fixture, document.toString());
    }

    private static String declaredType(final ContractMetadata.Element expected) {
        // Technical text vocabulary reconciled from the CLOSED stage1 metadata, not business data.
        for (final String type :
                List.of("select", "numeric", "date", "datetime", "string", "month_range")) {
            final var candidate =
                    ContractMetadata.Element.fromDeclaredType(
                            expected.kind(), expected.path(), Optional.of(type));
            if (candidate.equals(expected)) {
                return type;
            }
        }
        throw new AssertionError("DRIVER_OFFLINE_INFO_TYPE_FIXTURE_INCOMPLETE");
    }
}
