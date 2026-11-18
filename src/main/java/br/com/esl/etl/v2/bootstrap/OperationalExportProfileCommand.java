package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ColetasIndependentRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportRequest;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Map;
import java.util.Properties;

/** Explicit official-entry opt-in. No source activation, authority or new transport. */
final class OperationalExportProfileCommand {
    private OperationalExportProfileCommand() {}

    static int run(
            final String[] args,
            final PrintStream out,
            final PrintStream err,
            final RuntimeConfigurationFactory loader,
            final Properties properties,
            final Map<String, String> environment,
            final Clock clock) {
        return run(
                args,
                out,
                err,
                loader,
                properties,
                environment,
                clock,
                RuntimeIndependentExportCommand.productionInvocation());
    }

    static int run(
            final String[] args,
            final PrintStream out,
            final PrintStream err,
            final RuntimeConfigurationFactory loader,
            final Properties properties,
            final Map<String, String> environment,
            final Clock clock,
            final RuntimeIndependentExportCommand.Invocation invocation) {
        if (args.length != 11
                || !"run".equals(args[0])
                || !"--profile".equals(args[1])
                || !"--config".equals(args[3])
                || !"--operational-request".equals(args[5])
                || !"--selection".equals(args[7])
                || !"--sha256".equals(args[9])) {
            err.println("OPERATIONAL_EXPORT_PROFILE_USAGE");
            return 2;
        }
        final String profile = args[2];
        if (!ColetasIndependentRequest.PROFILE.equals(profile)
                && !IndependentExportRequest.PROFILE.equals(profile)) {
            err.println("OPERATIONAL_EXPORT_PROFILE_NOT_SUPPORTED");
            return 2;
        }
        final String[] canonical = {
            "independent-export",
            "run",
            "--config",
            args[4],
            "--operational-request",
            args[6],
            "--selection",
            args[8],
            "--sha256",
            args[10]
        };
        return RuntimeIndependentExportCommand.run(
                canonical, out, err, loader, properties, environment, clock, profile, invocation);
    }
}
