package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedStagingFacade;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Properties;

/**
 * Separate proposed Main route. Optional Root code remains external and owns all effect admission.
 */
final class ObservedStagingCommand {
    private ObservedStagingCommand() {}

    static int run(
            final String[] args,
            final PrintStream out,
            final PrintStream err,
            final RuntimeConfigurationFactory loader,
            final Properties properties,
            final Map<String, String> environment,
            final Clock clock) {
        if (args == null
                || args.length != 6
                || !"observed-staging".equals(args[0])
                || !("plan".equals(args[1]) || "rollback-trial".equals(args[1]))
                || !"--config".equals(args[2])
                || !"--request".equals(args[4])
                || args[3] == null
                || args[5] == null) {
            err.println("OBSERVED_STAGING_CLI_INVALID");
            return 2;
        }
        final boolean trial = "rollback-trial".equals(args[1]);
        // Lookup does not initialize external CODE. Missing/incompatible API refuses before file
        // IO.
        // The fixed API's presence is not admission; its own private CODE must authorize the
        // request.
        final var root = trial ? ObservedStagingTrialBridge.locate() : null;
        if (trial && root == null) {
            err.println("OBSERVED_STAGING_GENUINE_ROOT_ISSUER_AND_OWNER_NOT_BOUND");
            return RuntimeExitCategory.CONFIG_AUTH.code();
        }
        try {
            final var request = ObservedStagingRequest.read(Path.of(args[5]));
            final var configuration = loader.load(Path.of(args[3]), properties, environment, clock);
            final var prepared =
                    ObservedStagingFacade.prepare(
                            configuration,
                            request.executionId(),
                            request.template(),
                            request.businessWindow());
            if (trial) {
                final var result = root.invoke(prepared);
                out.println(result.technicalJson());
                return result.exit();
            }
            out.println(prepared.technicalJson());
            return 0; // Offline metadata plan only, explicitly represented by the JSON.
        } catch (final RuntimeException refused) {
            err.println(trial ? "OBSERVED_STAGING_INPUT_REFUSED" : "OBSERVED_STAGING_PLAN_REFUSED");
            return 2;
        }
    }
}
