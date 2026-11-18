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

/** Separate bounded 8636 capture. Planning never grants Source or claims native parity. */
public final class ContasPagarSourceCaptureMain {
    private ContasPagarSourceCaptureMain() {}

    public static void main(final String[] args) {
        final int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(final String[] args, final PrintStream out, final PrintStream err) {
        if (args == null
                || args.length != 5
                || !("plan".equals(args[0]) || "capture".equals(args[0]))
                || !"--input".equals(args[1])
                || !"--sha256".equals(args[3])) {
            err.println("CONTAS_A_PAGAR_SOURCE_USAGE");
            return 2;
        }
        try {
            final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(30));
            final Path path = Path.of(args[2]).toAbsolutePath().normalize();
            final var inputPin = new ExportValidationSupport.Pin(path, args[4], Files.size(path));
            final var input = inputPin.json(262144, budget);
            final ObjectNode result =
                    "plan".equals(args[0]) ? plan(input, budget) : capture(input, inputPin, budget);
            inputPin.read(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(30)));
            out.println(ExportValidationSupport.JSON.writeValueAsString(result));
            return "plan".equals(args[0]) || result.path("captureComplete").asBoolean() ? 0 : 3;
        } catch (final Exception refused) {
            final String code = refused.getMessage();
            err.println(
                    code != null && code.matches("[A-Z][A-Z0-9_]{1,159}")
                            ? code
                            : "CONTAS_A_PAGAR_SOURCE_REFUSED_PRIVATE_DETAILS_WITHHELD");
            return 2;
        }
    }

    public static ObjectNode plan(final JsonNode input, final ExportValidationSupport.Budget budget)
            throws Exception {
        fields(input, Set.of("schema", "requestPin", "sourceSettings"));
        require(
                "CONTAS_A_PAGAR_SOURCE_PLAN_INPUT_V1".equals(text(input, "schema")),
                "CONTAS_A_PAGAR_SOURCE_PLAN_SCHEMA");
        final var requestPin = ExportValidationSupport.Pin.from(input.required("requestPin"));
        final var request = requestPin.json(262144, budget);
        final var source = configuration(input.required("sourceSettings"));
        final var result = ContasPagarIndependentRequest.plan(request);
        result.put("schema", "CONTAS_A_PAGAR_FINITE_SOURCE_PLAN_V1")
                .put(
                        "sourceConfigurationSha256",
                        DataExportRuntimeConfigurationFingerprint.from(source).sha256())
                .put("maximumHttpRequests", 17)
                .put("maximumDataPages", 16)
                .put("sourceSeconds", 210)
                .put("maximumRows", 12000)
                .put("maximumResponseBytes", 8388608)
                .put("maximumRetainedBytes", 8388608)
                .put("mapperValidationChunkRows", 100)
                .put("originalMaximumBytes", 1073741824L)
                .put("originalMaximumSeconds", 300)
                .put("retries", 0)
                .put("redirects", 0);
        final var urls = result.putArray("finiteDataRequests");
        for (int page = 1; page <= 16; page++) {
            urls.add(
                    ContasPagarIndependentRequest.query(
                                    source.settings()
                                            .baseUri()
                                            .resolve("/api/analytics/reports/8636/data"),
                                    request,
                                    page,
                                    100)
                            .toASCIIString());
        }
        requestPin.read(262144, budget);
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
                "ROOT_CONTAS_A_PAGAR_SOURCE_EXECUTION_INPUT_V1".equals(text(input, "schema")),
                "CONTAS_A_PAGAR_SOURCE_INPUT_SCHEMA");
        final var requestPin = ExportValidationSupport.Pin.from(input.required("requestPin"));
        final var request = requestPin.json(262144, budget);
        ContasPagarIndependentRequest.validate(request);
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
                "CONTAS_A_PAGAR_SOURCE_PHYSICAL_BINDING");
        final var grantPin =
                ExportValidationSupport.Pin.from(context.required("contasPagarGrantPin"));
        final var grant = grantPin.json(262144, budget);
        requireGrant(grant, context, requestPin, request, codePin, fingerprint);
        final Path root = Path.of(text(context, "privateRoot"));
        require(
                root.isAbsolute() && root.normalize().equals(root),
                "CONTAS_A_PAGAR_PRIVATE_ROOT_ABSOLUTE");
        final Path output = output(input, "outputDirectory", root);
        final Path journalPath = output(input, "journalDirectory", root);
        final Path resultPath = output(input, "resultPath", root);
        require(
                Files.isDirectory(output)
                        && Files.isDirectory(journalPath.getParent())
                        && !Files.exists(journalPath)
                        && !Files.exists(resultPath),
                "CONTAS_A_PAGAR_SOURCE_FRESH_PATHS");
        final Instant started = Instant.now();
        final Instant deadline = started.plusSeconds(210);
        require(
                deadline.isBefore(Instant.parse(text(grant, "expiresUtc"))),
                "CONTAS_A_PAGAR_SOURCE_GRANT_REMAINING_WINDOW");
        try (var journal =
                PersistentExportJournal.create(
                        journalPath,
                        requestPin.sha256(),
                        codePin.sha256(),
                        UUID.fromString(text(request, "attemptId")))) {
            journal.record("CONTAS_A_PAGAR_ROOT_SOURCE_GRANT", grant);
            final var channel = new RootOwnedWireChannel(contextPin, journal, fingerprint);
            final var transport =
                    new ContasPagarTransport(
                            source, journal, channel, Clock.systemUTC(), deadline, 17);
            final var port =
                    new ContasPagarIntervalCapture.Port() {
                        @Override
                        public CurlExportTransport.Wire info() throws Exception {
                            return transport.api(
                                    "GET",
                                    "/api/analytics/reports/8636/info",
                                    null,
                                    Duration.ofSeconds(30));
                        }

                        @Override
                        public CurlExportTransport.Wire page(
                                final JsonNode selected, final int page, final int per)
                                throws Exception {
                            return transport.page(selected, page, per);
                        }
                    };
            ObjectNode result;
            try {
                result =
                        ContasPagarIntervalCapture.capture(
                                request, port, journal, output, 16, 100, 12000, deadline);
            } catch (final Exception partial) {
                final var retained = journal.latest("CONTAS_A_PAGAR_PARTIAL");
                if (retained == null || !retained.isObject()) {
                    throw partial;
                }
                result = ((ObjectNode) retained).deepCopy();
                final String code = partial.getMessage();
                result.put(
                        "refusalCode",
                        code != null && code.matches("[A-Z][A-Z0-9_]{1,159}")
                                ? code
                                : "CONTAS_A_PAGAR_SOURCE_PRIVATE_REFUSAL_DETAILS_WITHHELD");
            }
            final boolean complete =
                    "CONTAS_A_PAGAR_SOURCE_COMPLETE".equals(result.path("status").asText());
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
            result.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(codePin));
            final var postcheck = new ExportValidationSupport.Budget(Duration.ofSeconds(30));
            requestPin.read(262144, postcheck);
            contextPin.read(262144, postcheck);
            grantPin.read(262144, postcheck);
            codePin.read(8388608, postcheck);
            inputPin.read(262144, postcheck);
            final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(result);
            require(bytes.length <= 262144, "CONTAS_A_PAGAR_SOURCE_RESULT_BOUND");
            try (var file =
                    FileChannel.open(
                            resultPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                final var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    require(file.write(buffer) > 0, "CONTAS_A_PAGAR_SOURCE_WRITE_PROGRESS");
                }
                file.force(true);
            }
            return result;
        }
    }

    static void requireGrant(
            final JsonNode grant,
            final JsonNode context,
            final ExportValidationSupport.Pin requestPin,
            final JsonNode request,
            final ExportValidationSupport.Pin codePin,
            final String fingerprint) {
        require(
                "ROOT_CONTAS_A_PAGAR_FINITE_SOURCE_GRANT_V1".equals(text(grant, "schema"))
                        && grant.path("approved").isBoolean()
                        && grant.get("approved").booleanValue()
                        && "CONTAS_A_PAGAR".equals(text(grant, "entity"))
                        && grant.path("reportId").isIntegralNumber()
                        && grant.path("reportId").canConvertToInt()
                        && grant.path("reportId").intValue() == 8636
                        && ContasPagarIndependentRequest.PROFILE.equals(text(grant, "profile"))
                        && ContasPagarIndependentRequest.FILTER_SCOPE_POLICY.equals(
                                text(grant, "filterScopePolicy"))
                        && grant.path("createdAtFilter").isTextual()
                        && grant.get("createdAtFilter").textValue().isEmpty()
                        && request.required("filters")
                                .path(ContasPagarIndependentRequest.CREATED_AT_FILTER)
                                .isTextual()
                        && request.required("filters")
                                .get(ContasPagarIndependentRequest.CREATED_AT_FILTER)
                                .textValue()
                                .isEmpty()
                        && text(context, "executingRootUUID")
                                .equals(text(grant, "executingRootUUID"))
                        && text(request, "attemptId").equals(text(grant, "attemptId"))
                        && requestPin.equals(
                                ExportValidationSupport.Pin.from(grant.required("requestPin")))
                        && fingerprint.equals(text(grant, "sourceConfigurationSha256"))
                        && codePin.sha256().equals(text(grant, "codeUnitSha256"))
                        && text(request, "periodStart").equals(text(grant, "periodStart"))
                        && text(request, "periodEnd").equals(text(grant, "periodEnd"))
                        && text(request, "zone").equals(text(grant, "zone"))
                        && !grant.has("pageIndex")
                        && !grant.has("effectiveHTTPMaximum")
                        && !grant.has("pageScopePolicy"),
                "CONTAS_A_PAGAR_SOURCE_EXACT_ROOT_GRANT");
        for (final String cap : Set.of("OriginalMaximum", "SourceMaximum", "INFOHTTPMaximum")) {
            exact(grant, cap, 1);
        }
        exact(grant, "HTTPMaximum", 17);
        exact(grant, "DATAHTTPMaximum", 16);
        exact(grant, "SourceSeconds", 210);
        exact(grant, "OriginalSeconds", 300);
        exact(grant, "OriginalBytes", 1073741824L);
        exact(grant, "per", 100);
        for (final String cap : Set.of("SQL", "retryMaximum", "redirectMaximum")) {
            exact(grant, cap, 0);
        }
        final Instant created = Instant.parse(text(grant, "createdUtc"));
        final Instant expires = Instant.parse(text(grant, "expiresUtc"));
        require(
                !created.isAfter(Instant.now())
                        && expires.isAfter(Instant.now())
                        && expires.isAfter(created)
                        && Duration.between(created, expires).compareTo(Duration.ofSeconds(300))
                                <= 0,
                "CONTAS_A_PAGAR_SOURCE_FRESH_GRANT_WINDOW");
    }

    private static void exact(final JsonNode node, final String name, final long expected) {
        final var value = node.path(name);
        require(
                value.isIntegralNumber()
                        && value.canConvertToLong()
                        && value.longValue() == expected,
                "CONTAS_A_PAGAR_SOURCE_GRANT_FINITE_CAPS");
    }

    private static Path output(final JsonNode input, final String name, final Path root) {
        final Path path = Path.of(text(input, name));
        require(
                path.isAbsolute()
                        && path.normalize().equals(path)
                        && path.startsWith(root)
                        && !path.equals(root),
                "CONTAS_A_PAGAR_SOURCE_PRIVATE_PATH");
        for (Path at = path; at != null; at = at.getParent()) {
            require(!Files.isSymbolicLink(at), "CONTAS_A_PAGAR_SOURCE_PATH_LINK");
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
                "CONTAS_A_PAGAR_SOURCE_HTTPS_ORIGIN");
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
        require(input != null && input.isObject(), "CONTAS_A_PAGAR_SOURCE_OBJECT_REQUIRED");
        final var fields = new HashSet<String>();
        input.fieldNames().forEachRemaining(fields::add);
        require(fields.equals(expected), "CONTAS_A_PAGAR_SOURCE_EXPLICIT_FIELDS");
    }
}
