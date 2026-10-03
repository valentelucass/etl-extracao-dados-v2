package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Properties;

/** Entrada separada do Main operacional; SAMPLE exige gates e transacao local revertida. */
public final class Coletas6908PilotMain {
    private Coletas6908PilotMain() {}

    public static void main(final String[] args) {
        final int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(final String[] args, final PrintStream out, final PrintStream error) {
        return run(
                args,
                out,
                error,
                System.getProperties(),
                System.getenv(),
                Clock.systemUTC(),
                Coletas6908SampleRunner::runInLocalShadow);
    }

    static int run(
            final String[] args,
            final PrintStream out,
            final PrintStream error,
            final Properties properties,
            final Map<String, String> environment,
            final Clock clock,
            final SampleExecutor executor) {
        if (args.length == 9
                && "--sample".equals(args[0])
                && "--config".equals(args[1])
                && "--plan".equals(args[3])
                && "--request".equals(args[5])
                && "--bank-preflight".equals(args[7])) {
            return sample(args, out, error, properties, environment, clock, executor);
        }
        if (args.length != 2 || !"--plan".equals(args[0])) {
            error.println("COL_PILOT_EXECUTION_NOT_AUTHORIZED");
            return 3;
        }
        try {
            final Coletas6908PilotPlan plan = Coletas6908PilotPlan.read(Path.of(args[1]));
            out.println(
                    "COL_PILOT_PLAN_VALID template=6908 firstPage=1 maxPages="
                            + plan.maxPages()
                            + " per="
                            + plan.per()
                            + " maxPhysicalRows="
                            + plan.maxPhysicalRows()
                            + " maxResponseBytes="
                            + plan.maxResponseBytes()
                            + " deadlineSeconds="
                            + plan.deadline().toSeconds()
                            + " provenance=BOUNDED_TRAVERSAL_OBSERVED_ONLY execution=DISABLED");
            return 0;
        } catch (final RuntimeException failure) {
            error.println("COL_PILOT_INVALID_PLAN");
            return 2;
        }
    }

    private static int sample(
            final String[] args,
            final PrintStream out,
            final PrintStream error,
            final Properties properties,
            final Map<String, String> environment,
            final Clock clock,
            final SampleExecutor executor) {
        if (!"true".equals(properties.getProperty("shadow.local.integration.enabled"))
                || !"true".equals(properties.getProperty("shadow.local.integration.profile.active"))
                || !"true".equals(properties.getProperty("coletas6908.sample.enabled"))
                || !"1".equals(properties.getProperty("jdk.httpclient.redirects.retrylimit"))
                || !"true".equals(properties.getProperty("jdk.httpclient.disableRetryConnect"))
                || !"false".equals(properties.getProperty("jdk.httpclient.enableAllMethodRetry"))) {
            error.println("COL_SAMPLE_OPT_IN_REQUIRED");
            return 3;
        }
        String phase = "PREFLIGHT";
        try {
            final Path config = Path.of(args[2]);
            final Path planFile = Path.of(args[4]);
            final Path requestFile = Path.of(args[6]);
            final var configuration =
                    new RuntimeConfigurationFactory()
                            .load(config, configurationProperties(properties), environment, clock);
            final var plan = Coletas6908PilotPlan.read(planFile);
            final var request = RuntimeOperationalRequest.read(configuration, requestFile);
            Coletas6908SampleRunner.preflight(plan, configuration, request);
            Coletas6908SamplePreflight.validate(
                    Path.of(args[8]), config, planFile, requestFile, configuration, request);
            phase = "TRIAL";
            final var receipt = executor.execute(plan, configuration, request, environment);
            out.println(
                    "COL_SAMPLE_RESULT status="
                            + receipt.status()
                            + " scope="
                            + receipt.scope()
                            + " pages="
                            + receipt.pagesFetched()
                            + " physicalRows="
                            + receipt.physicalRows()
                            + " distinctRoots="
                            + receipt.distinctRoots()
                            + " presenceComparedCells=0"
                            + " promoted=false windowCompleteness=false childCompleteness=false");
            out.println(receipt.httpAttempts());
            return 0;
        } catch (final Exception failure) {
            error.println("COL_SAMPLE_STOP phase=" + phase);
            return 2;
        }
    }

    private static Properties configurationProperties(final Properties properties) {
        final var selected = new Properties();
        for (final String key : properties.stringPropertyNames()) {
            // Validated test opt-ins are not runtime settings; all other policy checks remain.
            if (!key.equals("shadow.local.integration.enabled")
                    && !key.equals("shadow.local.integration.profile.active")) {
                selected.setProperty(key, properties.getProperty(key));
            }
        }
        return selected;
    }

    @FunctionalInterface
    interface SampleExecutor {
        Coletas6908SampleRunner.Receipt execute(
                Coletas6908PilotPlan plan,
                RuntimeConfiguration configuration,
                RuntimeOperationalRequest request,
                Map<String, String> environment)
                throws Exception;
    }
}
