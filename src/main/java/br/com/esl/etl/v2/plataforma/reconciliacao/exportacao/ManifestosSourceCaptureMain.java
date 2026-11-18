package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Root-administered finite capture only. It never consumes SQL authority or activates normal ETL.
 */
public final class ManifestosSourceCaptureMain {
    private static final int MAXIMUM_COMPARISON_PINS = 7;

    private ManifestosSourceCaptureMain() {}

    public static void main(final String[] args) {
        final int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(final String[] args, final PrintStream out, final PrintStream err) {
        if (args.length != 5
                || args[0] == null
                || !Set.of("plan", "capture", "bounded-plan", "bounded-daily-plan", "verify-week")
                        .contains(args[0])
                || !"--input".equals(args[1])
                || !"--sha256".equals(args[3])) {
            err.println("MANIFESTOS_SOURCE_USAGE");
            return 2;
        }
        try {
            final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(30));
            final Path path = Path.of(args[2]).toAbsolutePath().normalize();
            final var inputPin = new ExportValidationSupport.Pin(path, args[4], Files.size(path));
            final var input = inputPin.json(262144, budget);
            final var result =
                    switch (args[0]) {
                        case "plan" -> plan(input, budget);
                        case "bounded-plan" -> boundedPlan(input, budget);
                        case "bounded-daily-plan" -> boundedDailyPlan(input, budget);
                        case "verify-week" -> verifyWeek(input, budget);
                        default -> capture(input, inputPin, budget);
                    };
            inputPin.read(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(30)));
            out.println(ExportValidationSupport.JSON.writeValueAsString(result));
            return !"capture".equals(args[0]) || result.path("captureComplete").booleanValue()
                    ? 0
                    : 3;
        } catch (final Exception refused) {
            final String code = refused.getMessage();
            err.println(
                    code != null && code.matches("[A-Z][A-Z0-9_]{1,159}")
                            ? code
                            : "MANIFESTOS_SOURCE_REFUSED_PRIVATE_DETAILS_WITHHELD");
            return 2;
        }
    }

    public static ObjectNode plan(final JsonNode input, final ExportValidationSupport.Budget budget)
            throws Exception {
        fields(input, Set.of("schema", "requestPin", "sourceSettings"));
        require(
                "MANIFESTOS_SOURCE_PLAN_INPUT_V1".equals(text(input, "schema")),
                "MANIFESTOS_SOURCE_PLAN_SCHEMA");
        final var request =
                ExportValidationSupport.Pin.from(input.required("requestPin")).json(262144, budget);
        ManifestosIndependentRequest.validate(request);
        final var source = configuration(input.required("sourceSettings"));
        return finitePlan(request, source);
    }

    public static ObjectNode boundedPlan(
            final JsonNode input, final ExportValidationSupport.Budget budget) throws Exception {
        return boundedPlan(input, budget, false);
    }

    public static ObjectNode boundedDailyPlan(
            final JsonNode input, final ExportValidationSupport.Budget budget) throws Exception {
        return boundedPlan(input, budget, true);
    }

    private static ObjectNode boundedPlan(
            final JsonNode input, final ExportValidationSupport.Budget budget, final boolean daily)
            throws Exception {
        fields(input, Set.of("schema", "requestPin", "sourceSettings"));
        require(
                "MANIFESTOS_BOUNDED_WEEK_PLAN_INPUT_V1".equals(text(input, "schema")),
                "MANIFESTOS_BOUNDED_PLAN_SCHEMA");
        final var requestPin = ExportValidationSupport.Pin.from(input.required("requestPin"));
        final var request = requestPin.json(262144, budget);
        final var source = configuration(input.required("sourceSettings"));
        final var result =
                daily
                        ? ManifestosBoundedWeek.planDaily(request)
                        : ManifestosBoundedWeek.plan(request);
        result.put(
                "sourceConfigurationSha256",
                DataExportRuntimeConfigurationFingerprint.from(source).sha256());
        result.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
        for (final var part : result.required("parts")) {
            final var capturePlan = finitePlan(part.required("request"), source);
            capturePlan
                    .put("maximumRows", 12000)
                    .put("maximumResponseBytes", 8388608)
                    .put("maximumRetainedBytes", 8388608)
                    .put("originalMaximumBytes", 1073741824L)
                    .put("originalMaximumSeconds", 300)
                    .put("retries", 0);
            ((ObjectNode) part).set("capturePlan", capturePlan);
        }
        requestPin.read(262144, budget);
        return result;
    }

    public static ObjectNode verifyWeek(
            final JsonNode input, final ExportValidationSupport.Budget budget) throws Exception {
        return verifyWeekWithPostcheckProbe(input, budget, () -> {});
    }

    /** Package-private integrity fixture only; neither public entry nor CLI accepts a probe. */
    static ObjectNode verifyWeekWithPostcheckProbe(
            final JsonNode input,
            final ExportValidationSupport.Budget budget,
            final Runnable beforePostcheck)
            throws Exception {
        fields(input, Set.of("schema", "requestPin", "comparisonPins"));
        require(
                "MANIFESTOS_BOUNDED_WEEK_VERIFY_INPUT_V1".equals(text(input, "schema")),
                "MANIFESTOS_WEEK_VERIFY_SCHEMA");
        final var requestPin = ExportValidationSupport.Pin.from(input.required("requestPin"));
        final var request = requestPin.json(262144, budget);
        final var comparisonPins = input.required("comparisonPins");
        require(
                comparisonPins.isArray()
                        && (comparisonPins.size() == 2
                                || comparisonPins.size() == MAXIMUM_COMPARISON_PINS),
                "MANIFESTOS_WEEK_EXACT_TWO_OR_SEVEN_PINS");
        final var pins = new ArrayList<ExportValidationSupport.Pin>();
        final var comparisons = ExportValidationSupport.JSON.createArrayNode();
        for (final var pin : comparisonPins) {
            final var selected = ExportValidationSupport.Pin.from(pin);
            pins.add(selected);
            comparisons.add(selected.json(262144, budget));
        }
        final var result = ManifestosBoundedWeek.verify(request, comparisons);
        result.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
        result.set("comparisonPins", ExportValidationSupport.JSON.valueToTree(pins));
        beforePostcheck.run();
        for (final var pin : pins) {
            pin.read(262144, budget);
        }
        requestPin.read(262144, budget);
        return result;
    }

    private static ObjectNode finitePlan(
            final JsonNode request, final DataExportSourceConfiguration source) {
        final var result = ManifestosIndependentRequest.plan(request);
        result.put("schema", "MANIFESTOS_FINITE_SOURCE_PLAN_V1")
                .put(
                        "sourceConfigurationSha256",
                        DataExportRuntimeConfigurationFingerprint.from(source).sha256())
                .put("maximumHttpRequests", 17)
                .put("maximumDataPages", 16)
                .put("sourceSeconds", 210)
                .put("sourceQueryAuthorized", false);
        final var urls = result.putArray("finiteDataRequests");
        for (int page = 1; page <= 16; page++) {
            urls.add(
                    ManifestosIndependentRequest.query(
                                    source.settings()
                                            .baseUri()
                                            .resolve("/api/analytics/reports/6399/data"),
                                    request,
                                    page,
                                    100)
                            .toASCIIString());
        }
        return result;
    }

    private static ObjectNode capture(
            final JsonNode input,
            final ExportValidationSupport.Pin inputPin,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        fields(
                input,
                Set.of(
                        "schema",
                        "requestPin",
                        "sourceSettings",
                        "physicalContextPin",
                        "codeUnitPin",
                        "outputDirectory",
                        "journalDirectory",
                        "resultPath"));
        require(
                "ROOT_MANIFESTOS_SOURCE_EXECUTION_INPUT_V1".equals(text(input, "schema")),
                "MANIFESTOS_SOURCE_INPUT_SCHEMA");
        final var requestPin = ExportValidationSupport.Pin.from(input.required("requestPin"));
        final var request = requestPin.json(262144, budget);
        ManifestosIndependentRequest.validate(request);
        final var source = configuration(input.required("sourceSettings"));
        final String fingerprint = DataExportRuntimeConfigurationFingerprint.from(source).sha256();
        final var contextPin =
                ExportValidationSupport.Pin.from(input.required("physicalContextPin"));
        final var context = contextPin.json(262144, budget);
        final var codePin = ExportValidationSupport.Pin.from(input.required("codeUnitPin"));
        codePin.read(8388608, budget);
        require(
                fingerprint.equals(text(context, "sourceConfigurationSha256"))
                        && codePin.sha256().equals(text(context, "codeUnitSha256")),
                "MANIFESTOS_SOURCE_PHYSICAL_BINDING");
        final var grantPin =
                ExportValidationSupport.Pin.from(context.required("manifestosGrantPin"));
        final var grant = grantPin.json(262144, budget);
        require(
                "ROOT_MANIFESTOS_FINITE_SOURCE_GRANT_V1".equals(text(grant, "schema"))
                        && grant.path("approved").isBoolean()
                        && grant.get("approved").booleanValue()
                        && text(request, "attemptId").equals(text(grant, "attemptId"))
                        && requestPin.equals(
                                ExportValidationSupport.Pin.from(grant.required("requestPin")))
                        && fingerprint.equals(text(grant, "sourceConfigurationSha256"))
                        && codePin.sha256().equals(text(grant, "codeUnitSha256"))
                        && text(request, "periodStart").equals(text(grant, "periodStart"))
                        && text(request, "periodEnd").equals(text(grant, "periodEnd")),
                "MANIFESTOS_SOURCE_FRESH_GRANT_REQUIRED");
        final Path privateRoot = Path.of(text(context, "privateRoot")).toAbsolutePath().normalize();
        final Path output = output(input, "outputDirectory", privateRoot);
        final Path journalPath = output(input, "journalDirectory", privateRoot);
        final Path resultPath = output(input, "resultPath", privateRoot);
        require(
                Files.isDirectory(output)
                        && Files.isDirectory(journalPath.getParent())
                        && !Files.exists(journalPath)
                        && !Files.exists(resultPath),
                "MANIFESTOS_SOURCE_FRESH_EXECUTION_PATHS");
        final Instant started = Instant.now();
        final Instant deadline = started.plusSeconds(210);
        require(
                deadline.isBefore(Instant.parse(text(grant, "expiresUtc"))),
                "MANIFESTOS_SOURCE_GRANT_REMAINING_WINDOW");
        try (var journal =
                PersistentExportJournal.create(
                        journalPath,
                        requestPin.sha256(),
                        codePin.sha256(),
                        UUID.fromString(text(request, "attemptId")))) {
            journal.record("MANIFESTOS_ROOT_SOURCE_GRANT", grant);
            final var channel = new RootOwnedWireChannel(contextPin, journal, fingerprint);
            final var transport =
                    new CurlExportTransport(
                            source, journal, channel, Clock.systemUTC(), deadline, 17, Set.of());
            final var capturePort =
                    new ManifestosIntervalCapture.Port() {
                        @Override
                        public CurlExportTransport.Wire info() throws Exception {
                            return transport.api(
                                    "GET",
                                    "/api/analytics/reports/6399/info",
                                    null,
                                    Duration.ofSeconds(30));
                        }

                        @Override
                        public CurlExportTransport.Wire page(
                                final JsonNode selected, final int page, final int per)
                                throws Exception {
                            return transport.manifestosPage(selected, page, per);
                        }
                    };
            ObjectNode result;
            try {
                result =
                        ManifestosIntervalCapture.capture(
                                request, capturePort, journal, output, 16, 100, 12000, deadline);
            } catch (final Exception refusedOrPartial) {
                final var retained = journal.latest("MANIFESTOS_PARTIAL");
                if (retained == null || !retained.isObject()) {
                    throw refusedOrPartial;
                }
                result = ((ObjectNode) retained).deepCopy();
                final String code = refusedOrPartial.getMessage();
                result.put(
                        "refusalCode",
                        code != null && code.matches("[A-Z][A-Z0-9_]{1,159}")
                                ? code
                                : "MANIFESTOS_SOURCE_PRIVATE_REFUSAL_DETAILS_WITHHELD");
            }
            final boolean complete =
                    "MANIFESTOS_SOURCE_COMPLETE".equals(result.path("status").asText());
            result.put("captureComplete", complete)
                    .put("sourceConfigurationSha256", fingerprint)
                    .put("Source", 1)
                    .put("HTTP", journal.count("HTTP_COMPLETE"))
                    .put("SQL", 0)
                    .put("httpAdmitted", journal.count("HTTP_ADMITTED"))
                    .put(
                            "wireOutcomeUnknown",
                            journal.count("HTTP_ADMITTED") != journal.count("HTTP_COMPLETE"))
                    .put("startedAt", started.toString())
                    .put("finishedAt", Instant.now().toString())
                    .put("contentAccepted", false)
                    .put("entityQualified", false)
                    .put("mainResumeAllowed", false)
                    .put("productionAuthorized", false);
            result.set("executionInputPin", ExportValidationSupport.JSON.valueToTree(inputPin));
            result.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
            result.set("grantPin", ExportValidationSupport.JSON.valueToTree(grantPin));
            final var postcheck = new ExportValidationSupport.Budget(Duration.ofSeconds(30));
            requestPin.read(262144, postcheck);
            contextPin.read(262144, postcheck);
            grantPin.read(262144, postcheck);
            final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(result);
            require(bytes.length <= 262144, "MANIFESTOS_SOURCE_RESULT_BOUND");
            try (var file =
                    FileChannel.open(
                            resultPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                final var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    file.write(buffer);
                }
                file.force(true);
            }
            return result;
        }
    }

    private static Path output(final JsonNode input, final String name, final Path root) {
        final var path = Path.of(text(input, name));
        require(
                path.isAbsolute()
                        && path.normalize().equals(path)
                        && path.startsWith(root)
                        && !path.equals(root),
                "MANIFESTOS_SOURCE_PRIVATE_PATH");
        for (Path at = path; at != null; at = at.getParent()) {
            require(!Files.isSymbolicLink(at), "MANIFESTOS_SOURCE_PATH_LINK");
        }
        return path;
    }

    private static DataExportSourceConfiguration configuration(final JsonNode settings) {
        fields(settings, Set.of("sourceInstance", "tenantScope", "apiOrigin"));
        final URI origin = URI.create(text(settings, "apiOrigin"));
        require(
                "https".equals(origin.getScheme())
                        && origin.getHost() != null
                        && origin.getRawQuery() == null
                        && origin.getRawFragment() == null
                        && origin.getRawUserInfo() == null
                        && (origin.getPort() == -1 || origin.getPort() == 443)
                        && (origin.getRawPath().isEmpty() || "/".equals(origin.getRawPath())),
                "MANIFESTOS_SOURCE_HTTPS_ORIGIN");
        return new DataExportSourceConfiguration(
                text(settings, "sourceInstance"),
                text(settings, "tenantScope"),
                new DataExportClientSettings(
                        origin,
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(30),
                        DataExportTransport.GET_WITH_QUERY,
                        new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                        8388608),
                new EslResiliencePolicy(
                        Duration.ofSeconds(3),
                        1,
                        17,
                        17,
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(210),
                        Duration.ofSeconds(210),
                        Duration.ZERO,
                        0,
                        1,
                        Duration.ofSeconds(60)));
    }

    private static void fields(final JsonNode input, final Set<String> expected) {
        require(input != null && input.isObject(), "MANIFESTOS_SOURCE_OBJECT_REQUIRED");
        final var fields = new HashSet<String>();
        input.fieldNames().forEachRemaining(fields::add);
        require(fields.equals(expected), "MANIFESTOS_SOURCE_EXPLICIT_FIELDS");
    }
}
