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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Administrative raw-page capture through the unchanged physical/native/privacy Source gates. */
public final class ManifestosRawPageSourceMain {
    private ManifestosRawPageSourceMain() {}

    public static void main(final String[] args) {
        final int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(final String[] args, final PrintStream out, final PrintStream err) {
        if (args.length != 4 || !"--input".equals(args[0]) || !"--sha256".equals(args[2])) {
            err.println("MANIFESTOS_RAW_PAGE_SOURCE_USAGE");
            return 2;
        }
        try {
            final Path path = Path.of(args[1]).toAbsolutePath().normalize();
            final var pin = new ExportValidationSupport.Pin(path, args[3], Files.size(path));
            final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(30));
            final var result = capture(pin.json(262144, budget), pin, budget);
            pin.read(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(30)));
            out.println(ExportValidationSupport.JSON.writeValueAsString(result));
            return result.path("pageComplete").asBoolean() ? 0 : 3;
        } catch (final Exception refused) {
            final String code = refused.getMessage();
            err.println(
                    code != null && code.matches("[A-Z][A-Z0-9_]{1,159}")
                            ? code
                            : "MANIFESTOS_RAW_PAGE_SOURCE_PRIVATE_REFUSAL");
            return 2;
        }
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
                        "pageIndex",
                        "journalDirectory",
                        "resultPath"));
        require(
                "ROOT_MANIFESTOS_RAW_PAGE_SOURCE_INPUT_V1".equals(text(input, "schema")),
                "MANIFESTOS_RAW_PAGE_SOURCE_SCHEMA");
        final var pageNode = input.required("pageIndex");
        require(
                pageNode.isIntegralNumber()
                        && pageNode.canConvertToInt()
                        && pageNode.intValue() >= 1
                        && pageNode.intValue() <= 16,
                "MANIFESTOS_RAW_PAGE_SOURCE_INDEX");
        final int page = pageNode.intValue();
        final var requestPin = ExportValidationSupport.Pin.from(input.required("requestPin"));
        final var request = requestPin.json(262144, budget);
        ManifestosIndependentRequest.validate(request);
        require(
                text(request, "periodStart").equals(text(request, "periodEnd")),
                "MANIFESTOS_RAW_PAGE_SINGLE_DAY");
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
                "MANIFESTOS_RAW_PAGE_PHYSICAL_BINDING");
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
                        && text(request, "periodEnd").equals(text(grant, "periodEnd"))
                        && grant.path("pageIndex").isIntegralNumber()
                        && grant.path("pageIndex").intValue() == page
                        && grant.path("effectiveHTTPMaximum").isIntegralNumber()
                        && grant.path("effectiveHTTPMaximum").intValue() == 2
                        && "EXACT_INFO_THEN_ONE_RAW_PAGE_V1".equals(text(grant, "pageScopePolicy")),
                "MANIFESTOS_RAW_PAGE_EXACT_ROOT_GRANT");
        final Path privateRoot = Path.of(text(context, "privateRoot")).toAbsolutePath().normalize();
        final Path journalPath = output(input, "journalDirectory", privateRoot);
        final Path resultPath = output(input, "resultPath", privateRoot);
        require(
                Files.isDirectory(journalPath.getParent())
                        && !Files.exists(journalPath)
                        && !Files.exists(resultPath),
                "MANIFESTOS_RAW_PAGE_FRESH_PATHS");
        final Instant started = Instant.now();
        final Instant deadline = started.plusSeconds(210);
        require(
                deadline.isBefore(Instant.parse(text(grant, "expiresUtc"))),
                "MANIFESTOS_RAW_PAGE_GRANT_WINDOW");
        try (var journal =
                PersistentExportJournal.create(
                        journalPath,
                        requestPin.sha256(),
                        codePin.sha256(),
                        UUID.fromString(text(request, "attemptId")))) {
            journal.record("MANIFESTOS_ROOT_SOURCE_GRANT", grant);
            final var channel = new RootOwnedWireChannel(contextPin, journal, fingerprint);
            final var scoped =
                    ManifestosRawPageCapture.scopedWire(
                            channel, source.settings().baseUri(), request, page);
            final var transport =
                    new CurlExportTransport(
                            source, journal, scoped, Clock.systemUTC(), deadline, 2, Set.of());
            final var port =
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
                                final JsonNode selected, final int selectedPage, final int per)
                                throws Exception {
                            return transport.manifestosPage(selected, selectedPage, per);
                        }
                    };
            ObjectNode result;
            try {
                result = ManifestosRawPageCapture.capture(request, page, port, journal, deadline);
            } catch (final Exception refused) {
                final String code = refused.getMessage();
                result =
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("schema", "MANIFESTOS_RAW_PAGE_CAPTURE_V1")
                                .put("pageComplete", false)
                                .put("pageIndex", page)
                                .put("per", 100)
                                .put("intervalComplete", false)
                                .put("terminalEmptyPageObserved", false)
                                .put("contentAccepted", false)
                                .put(
                                        "refusalCode",
                                        code != null && code.matches("[A-Z][A-Z0-9_]{1,159}")
                                                ? code
                                                : "MANIFESTOS_RAW_PAGE_PRIVATE_REFUSAL");
                journal.record("MANIFESTOS_RAW_PAGE_PARTIAL", result);
            }
            result.put("HTTP", journal.count("HTTP_COMPLETE"))
                    .put("httpAdmitted", journal.count("HTTP_ADMITTED"))
                    .put(
                            "wireOutcomeUnknown",
                            journal.count("HTTP_ADMITTED") != journal.count("HTTP_COMPLETE"))
                    .put("Source", 1)
                    .put("SQL", 0)
                    .put("sourceConfigurationSha256", fingerprint)
                    .put("startedAt", started.toString())
                    .put("finishedAt", Instant.now().toString())
                    .put("entityQualified", false)
                    .put("productionAuthorized", false);
            result.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
            result.set("executionInputPin", ExportValidationSupport.JSON.valueToTree(inputPin));
            result.set("grantPin", ExportValidationSupport.JSON.valueToTree(grantPin));
            result.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(codePin));
            final var postcheck = new ExportValidationSupport.Budget(Duration.ofSeconds(30));
            requestPin.read(262144, postcheck);
            contextPin.read(262144, postcheck);
            grantPin.read(262144, postcheck);
            final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(result);
            require(bytes.length <= 262144, "MANIFESTOS_RAW_PAGE_RESULT_BOUND");
            try (var file =
                    FileChannel.open(
                            resultPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                final var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    require(file.write(buffer) > 0, "MANIFESTOS_RAW_PAGE_WRITE_PROGRESS");
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
                "MANIFESTOS_RAW_PAGE_PRIVATE_PATH");
        for (Path at = path; at != null; at = at.getParent()) {
            require(!Files.isSymbolicLink(at), "MANIFESTOS_RAW_PAGE_PATH_LINK");
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
                "MANIFESTOS_RAW_PAGE_HTTPS_ORIGIN");
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
        require(input != null && input.isObject(), "MANIFESTOS_RAW_PAGE_OBJECT_REQUIRED");
        final Set<String> fields = new HashSet<>();
        input.fieldNames().forEachRemaining(fields::add);
        require(fields.equals(expected), "MANIFESTOS_RAW_PAGE_EXPLICIT_FIELDS");
    }
}
