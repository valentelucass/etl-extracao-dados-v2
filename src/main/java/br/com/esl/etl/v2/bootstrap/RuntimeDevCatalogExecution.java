package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.EnvironmentSecretProvider;
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogInspector;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogReportStore;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;

/** Concrete production binding. Executed exclusively by the separately admitted Bank parent. */
final class RuntimeDevCatalogExecution {
    private RuntimeDevCatalogExecution() {}

    static String execute(
            final RuntimeDevPreparedInvocation prepared,
            final RuntimeDevWorkspaceBundle bundle,
            final Path dispatchPath,
            final String dispatchSHA256,
            final PrintStream output)
            throws Exception {
        try (var dispatch =
                        RuntimeDevCatalogDispatch.open(
                                dispatchPath, dispatchSHA256, bundle, prepared);
                var control = new RuntimeDevCatalogControl(dispatch, System.in, output)) {
            final var plan =
                    planForRequest(
                            dispatch.descriptor.path("request").textValue(),
                            dispatch.invocation(),
                            prepared.target);
            // Lazy, separate credential namespace. Configuration never receives this value.
            final SecretProvider provider =
                    key -> {
                        if (key != SecretKey.SHADOW_SAMPLE_SA_PASSWORD) {
                            throw new IllegalArgumentException("DEV_CATALOG_SECRET_SCOPE");
                        }
                        final String value = System.getenv("C78_DEV_SA_SECRET");
                        if (value == null) {
                            throw new IllegalStateException("DEV_CATALOG_SECRET_ABSENT");
                        }
                        return new EnvironmentSecretProvider(
                                        Map.of("V2_SHADOW_SAMPLE_SA_PASSWORD", value))
                                .require(key);
                    };
            final var report = new C78DevCatalogInspector(control).inspect(plan, provider, control);
            final var stored =
                    C78DevCatalogReportStore.persist(report, dispatch.occurrence, plan.mode());
            return new ObjectMapper()
                    .writeValueAsString(
                            Map.of(
                                    "status",
                                    "DEV_C78_CATALOG_COMPLETE_REPORT_STORED",
                                    "reportSHA256",
                                    stored.sha256(),
                                    "reportBytes",
                                    stored.bytes(),
                                    "selectsAttempted",
                                    report.selectsAttempted(),
                                    "connectionClosed",
                                    true,
                                    "sourceExecuted",
                                    false,
                                    "captureExecuted",
                                    false,
                                    "engineExecuted",
                                    false));
        }
    }

    static C78DevCatalogPlan planForRequest(
            final String request,
            final java.util.UUID invocation,
            final br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget target) {
        return switch (request) {
            case "HERMES_f4eda62e83d1" -> C78DevCatalogPlan.prepareCoverage(invocation, target);
            case "HERMES_8fdb85e78657" -> C78DevCatalogPlan.prepare(invocation, target);
            default -> throw new IllegalArgumentException("DEV_CATALOG_COVERAGE_SCOPE_REQUIRED");
        };
    }
}
