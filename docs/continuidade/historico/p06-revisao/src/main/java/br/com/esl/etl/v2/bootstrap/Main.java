package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationException;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimePreflightReport;
import br.com.esl.etl.v2.plataforma.configuracao.SecretInputPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

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
        if ((args.length == 5
                        || (args.length == 7 || args.length == 9 && "--window".equals(args[7]))
                                && "plan".equals(command)
                                && "--request".equals(args[5]))
                && "--temporal".equals(args[3])
                && ("plan".equals(command) || "run".equals(command))) {
            return runTemporal(args, standardOut, standardError, dependencies);
        }
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
            case "local-profile" ->
                    LocalProfileCharacterizationMain.run(
                                    java.util.Arrays.copyOfRange(args, 1, args.length), standardOut)
                            .code();
            case "local-data" ->
                    LocalDataLaboratoryMain.run(
                                    java.util.Arrays.copyOfRange(args, 1, args.length), standardOut)
                            .code();
            case "local-scenario" ->
                    LocalArtifactScenarioMain.run(
                                    java.util.Arrays.copyOfRange(args, 1, args.length), standardOut)
                            .code();
            case "local-sweep" ->
                    LocalCollectionSweepMain.run(
                                    java.util.Arrays.copyOfRange(args, 1, args.length), standardOut)
                            .code();
            case "local-raster" ->
                    LocalRasterArtifactMain.run(
                                    java.util.Arrays.copyOfRange(args, 1, args.length), standardOut)
                            .code();
            case "dry-run" -> runDryRun(args, standardOut, standardError, dependencies);
            case "plan" -> runOfflinePlan(args, standardOut, standardError, dependencies);
            case "run" ->
                    runDeniedOperationalAction(
                            args, standardOut, standardError, dependencies, RuntimeAction.RUN);
            case "replay" ->
                    runDeniedOperationalAction(
                            args, standardOut, standardError, dependencies, RuntimeAction.REPLAY);
            case "sweep-preview" ->
                    runDeniedOperationalAction(
                            args,
                            standardOut,
                            standardError,
                            dependencies,
                            RuntimeAction.SWEEP_PREVIEW);
            case "sweep-apply" ->
                    runDeniedOperationalAction(
                            args,
                            standardOut,
                            standardError,
                            dependencies,
                            RuntimeAction.SWEEP_APPLY);
            case "force-run" ->
                    runDeniedOperationalAction(
                            args,
                            standardOut,
                            standardError,
                            dependencies,
                            RuntimeAction.FORCE_RUN);
            case "status" ->
                    runDeniedOperationalAction(
                            args, standardOut, standardError, dependencies, RuntimeAction.STATUS);
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

    private static int runTemporal(
            final String[] args,
            final PrintStream out,
            final PrintStream err,
            final RuntimeDependencies dependencies) {
        try {
            final var configuration =
                    dependencies
                            .configurationFactory()
                            .load(
                                    configurationPath(java.util.Arrays.copyOf(args, 3), 1),
                                            dependencies.systemProperties(),
                                    dependencies.environment(), dependencies.clock());
            new RuntimeCompositionRoot(configuration).validateConfiguration();
            final var operation = RuntimeTemporalOperation.read(Path.of(args[4]));
            if ("plan".equals(args[0])) {
                out.println(
                        args.length >= 7
                                ? operation.operationalRequests(
                                        configuration,
                                        Path.of(args[6]),
                                        args.length == 9 ? positiveWindow(args[8]) : 0)
                                : operation.preview());
            } else {
                out.println(
                        "TEMPORAL_PERSISTED windows="
                                + operation.persist(configuration, out::println)
                                + " extracted=0 scheduler=0");
            }
            return 0;
        } catch (
                final br.com.esl.etl.v2.plataforma.autorizacao.DurableAuthorizationException
                        failure) {
            err.println("Autorização temporal recusada: " + failure.reason());
            return 20;
        } catch (final IllegalArgumentException failure) {
            err.println("Pedido temporal inválido.");
            return 2;
        } catch (final RuntimeException failure) {
            err.println("Persistência temporal não confirmada; consultar janelas duráveis.");
            return 30;
        }
    }

    private static int positiveWindow(final String value) {
        if (!value.matches("[1-4]")) {
            throw new IllegalArgumentException("TEMPORAL_WINDOW_INVALID");
        }
        return Integer.parseInt(value);
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

    private static int runOfflinePlan(
            final String[] args,
            final PrintStream standardOut,
            final PrintStream standardError,
            final RuntimeDependencies dependencies) {
        final RuntimePreflightReport report =
                loadAndPreflight(args, 1, standardError, dependencies);
        if (report == null) {
            return 2;
        }
        standardOut.println("Planejamento offline sem efeitos: " + report.summary());
        return 0;
    }

    private static int runDeniedOperationalAction(
            final String[] args,
            final PrintStream standardOut,
            final PrintStream standardError,
            final RuntimeDependencies dependencies,
            final RuntimeAction action) {
        if ((args.length == 5
                        || args.length == 6
                                && "--control-stdin".equals(args[5])
                                && action != RuntimeAction.STATUS)
                && "--request".equals(args[3])
                && action != RuntimeAction.SWEEP_PREVIEW
                && action != RuntimeAction.SWEEP_APPLY) {
            return runScopedOperationalAction(
                    args, standardOut, standardError, dependencies, action);
        }
        final RuntimePreflightReport report =
                loadAndPreflight(args, 1, standardError, dependencies);
        if (report == null) {
            return 2;
        }
        try {
            final Path configurationFile = configurationPath(args, 1);
            final RuntimeConfiguration configuration =
                    dependencies
                            .configurationFactory()
                            .load(
                                    configurationFile,
                                    dependencies.systemProperties(),
                                    dependencies.environment(),
                                    dependencies.clock());
            new RuntimeCompositionRoot(configuration)
                    .authorizeOperationalAction(UUID.randomUUID(), action);
        } catch (final RuntimeAuthorizationException exception) {
            standardError.println("Operação recusada pela política deny-all.");
            return RuntimeExitCategory.CONFIG_AUTH.code();
        } catch (final IllegalArgumentException | IllegalStateException exception) {
            standardError.println("Configuração inválida.");
            return 2;
        }
        standardError.println("Operação recusada pela política deny-all.");
        return RuntimeExitCategory.CONFIG_AUTH.code();
    }

    private static int runScopedOperationalAction(
            final String[] args,
            final PrintStream standardOut,
            final PrintStream standardError,
            final RuntimeDependencies dependencies,
            final RuntimeAction action) {
        try {
            final String[] configurationArguments = java.util.Arrays.copyOf(args, 3);
            final var configuration =
                    dependencies
                            .configurationFactory()
                            .load(
                                    configurationPath(configurationArguments, 1),
                                            dependencies.systemProperties(),
                                    dependencies.environment(), dependencies.clock());
            final var result =
                    new RuntimeCompositionRoot(configuration)
                            .executeOperationalRequest(
                                    Path.of(args[4]),
                                    action,
                                    standardOut::println,
                                    args.length == 6 ? System.in : null);
            standardOut.println("Runtime: " + result.name());
            return result.code();
        } catch (
                final br.com.esl.etl.v2.plataforma.autorizacao.DurableAuthorizationException
                        failure) {
            standardError.println("Autorização operacional recusada: " + failure.reason());
            return RuntimeExitCategory.CONFIG_AUTH.code();
        } catch (final IllegalArgumentException failure) {
            standardError.println("Configuração ou pedido operacional inválido.");
            return 2;
        } catch (final RuntimeException failure) {
            standardError.println(
                    "Runtime não confirmou a execução; consultar a ocorrência durável.");
            return RuntimeExitCategory.LOCK.code();
        }
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
                "  local-data characterize|capture --artifact <json>  Expansão por artefato sintético local.");
        standardOut.println(
                "  local-profile characterize --artifact <json>  Perfis locais e mapeadores tipados.");
        standardOut.println(
                "  local-scenario run --input <json> --oracle <json>  Composição e comparação independente.");
        standardOut.println(
                "  local-sweep observe --input <json>                Coletas e preview das responsabilidades.");
        standardOut.println(
                "  local-raster characterize|capture --artifact <json>  Raster por artefato sintético local.");
        standardOut.println(
                "  Comandos locais: captura somente no shadow explicitamente habilitado, com rollback obrigatório.");
        standardOut.println(
                "  config validate --config <arquivo>  Valida configuração sem efeitos.");
        standardOut.println(
                "  dry-run --config <arquivo>           Executa somente o preflight seguro.");
        standardOut.println(
                "  plan --config <arquivo>              Planeja offline, sem criar conexões.");
        standardOut.println("  plan|run --config <arquivo> --temporal <json>");
        standardOut.println(
                "                                      plan: preview; run: persiste janelas com recibo por janela, sem extrair.");
        standardOut.println("  plan --config <arquivo> --temporal <json> --request <modelo-json>");
        standardOut.println(
                "                                      --window <1-4>: exporta uma janela com seu predecessor explícito.");
        standardOut.println(
                "                                      Exporta até quatro requests; execute cada janela por run --request.");
        standardOut.println(
                "  run|replay|sweep-preview|sweep-apply|force-run|status --config <arquivo>");
        standardOut.println(
                "                                      Recusado por deny-all até o subgate V2-022b.");
        standardOut.println("  run|replay|force-run|status --config <arquivo> --request <json>");
        standardOut.println(
                "                                      --control-stdin em execução: envie cancel seguido de Enter para cancelar.");
        standardOut.println(
                "                                      Requer authority Windows/SQL administrada e consumo durável.");
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
