package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportFileLifecycle;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportResult;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportValidation;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ManifestosIndependentRequest;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Explicit offline product route. No source client, credential provider or SQL capability. */
final class IndependentExportCommand {
    private IndependentExportCommand() {}

    static int run(
            final String[] args, final PrintStream out, final PrintStream err, final Clock clock) {
        final boolean historical = args.length > 1 && "compare-content".equals(args[1]);
        final boolean manifestos = args.length > 1 && "diagnose-manifestos".equals(args[1]);
        final boolean preparation = args.length > 1 && "prepare-content-controls".equals(args[1]);
        final boolean comparison = args.length > 1 && ("compare".equals(args[1]) || historical);
        final boolean writesResult = comparison || preparation || manifestos;
        if (args.length < 2
                || !("plan".equals(args[1]) || "status-plan".equals(args[1]) || writesResult)
                || (writesResult ? args.length != 8 : args.length != 6)
                || !"--input".equals(args[2])
                || !"--sha256".equals(args[4])
                || writesResult && !"--output".equals(args[6])) {
            err.println("INDEPENDENT_EXPORT_USAGE");
            return 2;
        }
        try {
            final Instant started = clock.instant();
            final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(150));
            final var path = Path.of(args[3]).toAbsolutePath().normalize();
            final var pin = new ExportValidationSupport.Pin(path, args[5], Files.size(path));
            final var input = pin.json(262144, budget);
            if (manifestos) {
                final var published =
                        br.com.esl.etl.v2.plataforma.reconciliacao.exportacao
                                .ManifestosOfflineDiagnostic.execute(
                                input, pin, Path.of(args[7]), started, clock, budget);
                out.println(ExportValidationSupport.JSON.writeValueAsString(published));
                return published.path("contentEqualUnderCandidatePresentation").booleanValue()
                        ? 0
                        : 3;
            }
            final var result =
                    switch (args[1]) {
                        case "plan" ->
                                ManifestosIndependentRequest.PROFILE.equals(
                                                input.path("profile").asText())
                                        ? ManifestosIndependentRequest.plan(input)
                                        : br.com.esl.etl.v2.plataforma.reconciliacao.exportacao
                                                        .ColetasIndependentRequest.PROFILE
                                                        .equals(input.path("profile").asText())
                                                ? br.com.esl.etl.v2.plataforma.reconciliacao
                                                        .exportacao.ColetasIndependentRequest.plan(
                                                        input)
                                                : IndependentExportRequest.plan(input);
                        case "status-plan" ->
                                ExportFileLifecycle.statusPlan(input, clock.instant());
                        case "prepare-content-controls" ->
                                br.com.esl.etl.v2.plataforma.reconciliacao.exportacao
                                        .HistoricalContentControlPreparation.prepare(input, budget);
                        case "compare-content" ->
                                br.com.esl.etl.v2.plataforma.reconciliacao.exportacao
                                        .HistoricalExportContentValidation.compare(input, budget);
                        default -> IndependentExportValidation.compare(input, budget);
                    };
            pin.read(262144, budget);
            final var published =
                    preparation
                            ? IndependentExportResult.persistControlPreparation(
                                    Path.of(args[7]), result, pin, started, clock, budget)
                            : historical
                                    ? IndependentExportResult.persistHistorical(
                                            Path.of(args[7]), result, pin, started, clock, budget)
                                    : comparison
                                            ? IndependentExportResult.persist(
                                                    Path.of(args[7]),
                                                    result,
                                                    pin,
                                                    started,
                                                    clock,
                                                    budget)
                                            : result;
            out.println(ExportValidationSupport.JSON.writeValueAsString(published));
            return !comparison
                            || (historical
                                            ? "HISTORICAL_CONTENT_EQUAL_FOR_THIS_PAIR"
                                            : "CONTENT_EQUAL_FOR_THIS_PAIR")
                                    .equals(result.path("status").asText())
                    ? 0
                    : 3;
        } catch (final Exception refused) {
            // Parser messages can contain cells or local input paths; never emit them here.
            err.println("INDEPENDENT_EXPORT_REFUSED_NO_RESULT");
            return 2;
        }
    }
}
