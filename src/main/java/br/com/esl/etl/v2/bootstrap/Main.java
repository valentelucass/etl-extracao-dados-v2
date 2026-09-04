package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimePreflightReport;
import br.com.esl.etl.v2.plataforma.configuracao.SecretInputPolicy;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Entry point do ETL Data Export V2. */
public final class Main {

    private static final Set<String> NON_SECRET_RUNTIME_ENVIRONMENT =
            Set.of(
                    "V2_RUNTIME_ENVIRONMENT",
                    "V2_RUNTIME_BUSINESS_TIMEZONE",
                    "V2_DATAEXPORT_ENABLED",
                    "V2_DATAEXPORT_BASE_URL",
                    "V2_DATAEXPORT_SOURCE_INSTANCE",
                    "V2_DATAEXPORT_TENANT_SCOPE",
                    "V2_DATAEXPORT_TIMEZONE",
                    "V2_DATAEXPORT_TRANSPORT",
                    "V2_DATAEXPORT_TIMEOUT_SECONDS",
                    "V2_DATAEXPORT_MAX_RESPONSE_BYTES",
                    "V2_DATAEXPORT_RETRY_MAX_ATTEMPTS",
                    "V2_DATAEXPORT_RETRY_INITIAL_DELAY_MS",
                    "V2_DATAEXPORT_RETRY_MAX_DELAY_MS",
                    "V2_DATAEXPORT_RESILIENCE_MINIMUM_REQUEST_INTERVAL_MS",
                    "V2_DATAEXPORT_RESILIENCE_MAX_IN_FLIGHT",
                    "V2_DATAEXPORT_RESILIENCE_MAX_REQUESTS_PER_CYCLE",
                    "V2_DATAEXPORT_RESILIENCE_MAX_REQUESTS_PER_WORKLOAD",
                    "V2_DATAEXPORT_RESILIENCE_STEP_TIMEOUT_SECONDS",
                    "V2_DATAEXPORT_RESILIENCE_CYCLE_TIMEOUT_SECONDS",
                    "V2_DATAEXPORT_RESILIENCE_MAX_RETRY_AFTER_MS",
                    "V2_DATAEXPORT_RESILIENCE_MAX_REPARTITIONS",
                    "V2_DATAEXPORT_RESILIENCE_CIRCUIT_FAILURE_THRESHOLD",
                    "V2_DATAEXPORT_RESILIENCE_CIRCUIT_COOLDOWN_SECONDS",
                    "V2_GRAPHQL_ENABLED",
                    "V2_GRAPHQL_ENDPOINT",
                    "V2_GRAPHQL_SOURCE_INSTANCE",
                    "V2_GRAPHQL_TENANT_SCOPE",
                    "V2_GRAPHQL_TIMEOUT_SECONDS",
                    "V2_GRAPHQL_MAX_RESPONSE_BYTES",
                    "V2_GRAPHQL_RETRY_MAX_ATTEMPTS",
                    "V2_GRAPHQL_RETRY_INITIAL_DELAY_MS",
                    "V2_GRAPHQL_RETRY_MAX_DELAY_MS",
                    "V2_GRAPHQL_RESILIENCE_MINIMUM_REQUEST_INTERVAL_MS",
                    "V2_GRAPHQL_RESILIENCE_MAX_IN_FLIGHT",
                    "V2_GRAPHQL_RESILIENCE_MAX_REQUESTS_PER_CYCLE",
                    "V2_GRAPHQL_RESILIENCE_MAX_REQUESTS_PER_WORKLOAD",
                    "V2_GRAPHQL_RESILIENCE_STEP_TIMEOUT_SECONDS",
                    "V2_GRAPHQL_RESILIENCE_CYCLE_TIMEOUT_SECONDS",
                    "V2_GRAPHQL_RESILIENCE_MAX_RETRY_AFTER_MS",
                    "V2_GRAPHQL_RESILIENCE_MAX_REPARTITIONS",
                    "V2_GRAPHQL_RESILIENCE_CIRCUIT_FAILURE_THRESHOLD",
                    "V2_GRAPHQL_RESILIENCE_CIRCUIT_COOLDOWN_SECONDS",
                    "V2_SHADOW_AUDIT_ENABLED",
                    "V2_SHADOW_TARGET_KIND",
                    "V2_SHADOW_JDBC_URL",
                    "V2_SHADOW_APPROVAL_REFERENCE");

    private Main() {}

    public static void main(final String[] args) {
        final int exitCode =
                run(
                        args,
                        new PrintStream(System.out, true, StandardCharsets.UTF_8),
                        new PrintStream(System.err, true, StandardCharsets.UTF_8));
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(
            final String[] args, final PrintStream standardOut, final PrintStream standardError) {
        return run(
                args,
                standardOut,
                standardError,
                new RuntimeDependencies(
                        System.getProperties(),
                        System.getenv(),
                        Clock.systemUTC(),
                        new RuntimeConfigurationFactory()));
    }

    static int run(
            final String[] args,
            final PrintStream standardOut,
            final PrintStream standardError,
            final RuntimeDependencies dependencies) {
        Objects.requireNonNull(args, "Os argumentos são obrigatórios.");
        Objects.requireNonNull(standardOut, "A saída padrão é obrigatória.");
        Objects.requireNonNull(standardError, "A saída de erro é obrigatória.");
        Objects.requireNonNull(dependencies, "As dependências de runtime são obrigatórias.");
        try {
            validateCommandLine(args);
        } catch (final IllegalArgumentException exception) {
            standardError.println("Argumentos recusados pela política de segurança.");
            return 2;
        }
        final String command = args.length == 0 ? "--help" : args[0];
        return switch (command) {
            case "--help" -> {
                if (args.length != 1 && args.length != 0) {
                    yield invalidUsage(standardOut, standardError, "Uso inválido.");
                }
                printHelp(standardOut);
                yield 0;
            }
            case "--version" -> {
                if (args.length != 1) {
                    yield invalidUsage(standardOut, standardError, "Uso inválido.");
                }
                standardOut.println("ETL Data Export V2 0.1.0-SNAPSHOT");
                yield 0;
            }
            case "config" -> runConfigValidation(args, standardOut, standardError, dependencies);
            case "dry-run" -> runDryRun(args, standardOut, standardError, dependencies);
            default -> {
                standardError.println("Comando desconhecido.");
                printHelp(standardOut);
                yield 2;
            }
        };
    }

    private static void validateCommandLine(final String[] args) {
        for (final String argument : args) {
            Objects.requireNonNull(argument, "Argumento inválido.");
            if (argument.startsWith("--")) {
                SecretInputPolicy.rejectSecretCommandLineOption(argument);
            }
        }
    }

    private static int runConfigValidation(
            final String[] args,
            final PrintStream standardOut,
            final PrintStream standardError,
            final RuntimeDependencies dependencies) {
        if (args.length < 2 || !"validate".equals(args[1])) {
            return invalidUsage(
                    standardOut, standardError, "Use: config validate --config <arquivo>.");
        }
        final RuntimePreflightReport report =
                loadAndPreflight(args, 2, standardError, dependencies);
        if (report == null) {
            return 2;
        }
        standardOut.println(report.summary());
        return 0;
    }

    private static int runDryRun(
            final String[] args,
            final PrintStream standardOut,
            final PrintStream standardError,
            final RuntimeDependencies dependencies) {
        final RuntimePreflightReport report =
                loadAndPreflight(args, 1, standardError, dependencies);
        if (report == null) {
            return 2;
        }
        standardOut.println("Dry-run sem efeitos: " + report.summary());
        return 0;
    }

    private static RuntimePreflightReport loadAndPreflight(
            final String[] args,
            final int configurationArgumentIndex,
            final PrintStream standardError,
            final RuntimeDependencies dependencies) {
        try {
            final Path configurationFile = configurationPath(args, configurationArgumentIndex);
            final RuntimeConfiguration configuration =
                    dependencies
                            .configurationFactory()
                            .load(
                                    configurationFile,
                                    dependencies.systemProperties(),
                                    dependencies.environment(),
                                    dependencies.clock());
            final RuntimeCompositionRoot compositionRoot =
                    new RuntimeCompositionRoot(configuration);
            return configurationArgumentIndex == 1
                    ? compositionRoot.createDryRunPlan()
                    : compositionRoot.validateConfiguration();
        } catch (final IllegalArgumentException | IllegalStateException exception) {
            standardError.println("Configuração inválida.");
            return null;
        }
    }

    private static Path configurationPath(
            final String[] args, final int configurationArgumentIndex) {
        if (args.length != configurationArgumentIndex + 2
                || !"--config".equals(args[configurationArgumentIndex])) {
            throw new IllegalArgumentException(
                    "Use --config <arquivo> e não informe opções extras.");
        }
        try {
            return Path.of(args[configurationArgumentIndex + 1]);
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException("O caminho de configuração é inválido.");
        }
    }

    private static int invalidUsage(
            final PrintStream standardOut, final PrintStream standardError, final String message) {
        standardError.println(message);
        printHelp(standardOut);
        return 2;
    }

    private static void printHelp(final PrintStream standardOut) {
        standardOut.println("ETL Data Export V2 — modo de sombra");
        standardOut.println("Comandos disponíveis:");
        standardOut.println("  --help     Exibe esta ajuda.");
        standardOut.println("  --version  Exibe a versão.");
        standardOut.println(
                "  config validate --config <arquivo>  Valida configuração sem efeitos.");
        standardOut.println(
                "  dry-run --config <arquivo>           Executa somente o preflight seguro.");
        standardOut.println(
                "As cargas de Coletas e Fretes exigem contrato e storage de sombra aprovados.");
    }

    private static Map<String, String> nonSecretRuntimeEnvironment(
            final Map<String, String> environment) {
        final Map<String, String> snapshot = new HashMap<>();
        environment.forEach(
                (key, value) -> {
                    if (key.startsWith("V2_")) {
                        snapshot.put(
                                key,
                                NON_SECRET_RUNTIME_ENVIRONMENT.contains(key) && value != null
                                        ? value
                                        : "");
                    }
                });
        return Map.copyOf(snapshot);
    }

    record RuntimeDependencies(
            Properties systemProperties,
            Map<String, String> environment,
            Clock clock,
            RuntimeConfigurationFactory configurationFactory) {

        RuntimeDependencies {
            systemProperties =
                    Objects.requireNonNull(systemProperties, "As propriedades são obrigatórias.");
            environment =
                    nonSecretRuntimeEnvironment(
                            Objects.requireNonNull(environment, "O ambiente é obrigatório."));
            clock = Objects.requireNonNull(clock, "O relógio é obrigatório.");
            configurationFactory =
                    Objects.requireNonNull(
                            configurationFactory, "A factory de configuração é obrigatória.");
        }
    }
}
