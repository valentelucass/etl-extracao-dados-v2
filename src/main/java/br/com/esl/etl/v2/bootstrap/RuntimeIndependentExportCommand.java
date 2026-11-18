package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

/**
 * Concrete opt-in caller. Existing operational authorization is consumed before source
 * materialization.
 */
final class RuntimeIndependentExportCommand {
    private RuntimeIndependentExportCommand() {}

    @FunctionalInterface
    interface Invocation {
        com.fasterxml.jackson.databind.node.ObjectNode execute(
                br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration configuration,
                Path operationalRequest,
                ExportValidationSupport.Pin selectionPin,
                com.fasterxml.jackson.databind.JsonNode selection,
                long startedNanos,
                java.time.Instant startedAt,
                String officialProfile)
                throws Exception;
    }

    static Invocation productionInvocation() {
        return (configuration, operational, pin, selection, nanos, instant, profile) ->
                new RuntimeCompositionRoot(configuration)
                        .executeIndependentExport(
                                operational, pin, selection, nanos, instant, profile);
    }

    static int run(
            final String[] args,
            final PrintStream out,
            final PrintStream err,
            final RuntimeConfigurationFactory loader,
            final Properties properties,
            final Map<String, String> environment,
            final Clock clock) {
        return run(args, out, err, loader, properties, environment, clock, null);
    }

    static int run(
            final String[] args,
            final PrintStream out,
            final PrintStream err,
            final RuntimeConfigurationFactory loader,
            final Properties properties,
            final Map<String, String> environment,
            final Clock clock,
            final String requiredProfile) {
        return run(
                args,
                out,
                err,
                loader,
                properties,
                environment,
                clock,
                requiredProfile,
                productionInvocation());
    }

    static int run(
            final String[] args,
            final PrintStream out,
            final PrintStream err,
            final RuntimeConfigurationFactory loader,
            final Properties properties,
            final Map<String, String> environment,
            final Clock clock,
            final String requiredProfile,
            final Invocation invocation) {
        final long invocationStartedNanos = System.nanoTime();
        final var invocationStartedAt = clock.instant();
        if (args.length != 10
                || !"run".equals(args[1])
                || !"--config".equals(args[2])
                || !"--operational-request".equals(args[4])
                || !"--selection".equals(args[6])
                || !"--sha256".equals(args[8])) {
            err.println("INDEPENDENT_EXPORT_OPERATIONAL_USAGE");
            return 2;
        }
        try {
            final var path = Path.of(args[7]).toAbsolutePath().normalize();
            final var pin = new ExportValidationSupport.Pin(path, args[9], Files.size(path));
            final var selection =
                    pin.json(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
            if (requiredProfile != null
                    && !requiredProfile.equals(selection.path("profile").asText())) {
                err.println("OPERATIONAL_EXPORT_PROFILE_SELECTION_MISMATCH");
                return 2;
            }
            final var configuration = loader.load(Path.of(args[3]), properties, environment, clock);
            final var result =
                    invocation.execute(
                            configuration,
                            Path.of(args[5]),
                            pin,
                            selection,
                            invocationStartedNanos,
                            invocationStartedAt,
                            requiredProfile);
            out.println(ExportValidationSupport.JSON.writeValueAsString(result));
            if (requiredProfile != null) {
                return "COMPLETED_GOVERNED_CAPTURE_DELIVERY_FOR_THIS_INVOCATION"
                                        .equals(result.path("status").asText())
                                && result.path("governedDeliveryComplete").asBoolean()
                        ? 0
                        : 3;
            }
            return "CONTENT_EQUAL_FOR_THIS_PAIR".equals(result.path("status").asText()) ? 0 : 3;
        } catch (final Exception deniedOrUnknown) {
            err.println("INDEPENDENT_EXPORT_OPERATIONAL_REFUSED_OR_UNCERTAIN");
            return 2;
        }
    }
}
