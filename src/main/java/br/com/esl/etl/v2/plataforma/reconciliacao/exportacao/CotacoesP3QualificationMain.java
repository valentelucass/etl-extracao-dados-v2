package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline administrative batch only; no source admission, completeness inference or operational
 * Main adoption.
 */
public final class CotacoesP3QualificationMain {
    private static final int MAX_BYTES = 8388608;
    private static final int PRIVATE_BOUND = 2097152;

    private record Daily(
            ExportValidationSupport.Pin requestPin,
            JsonNode request,
            JsonNode payloadPin,
            ExportValidationSupport.Pin terminalPin) {}

    private CotacoesP3QualificationMain() {}

    public static void main(final String[] args) {
        final int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(final String[] args, final PrintStream out, final PrintStream err) {
        if (args == null
                || args.length != 7
                || !"qualify".equals(args[0])
                || !"--input".equals(args[1])
                || !"--sha256".equals(args[3])
                || !"--output".equals(args[5])) {
            err.println("COTACOES_P3_BATCH_USAGE");
            return 2;
        }
        try {
            final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(150));
            final Path path = Path.of(args[2]).toAbsolutePath().normalize();
            final var inputPin = new ExportValidationSupport.Pin(path, args[4], Files.size(path));
            final var receipt =
                    execute(inputPin.json(262144, budget), inputPin, Path.of(args[6]), budget);
            out.println(ExportValidationSupport.JSON.writeValueAsString(receipt));
            return receipt.path("typesQualifiedAll21").booleanValue() ? 0 : 3;
        } catch (final Exception failure) {
            final String code = failure.getMessage();
            err.println(
                    code != null && code.matches("[A-Z][A-Z0-9_]{1,159}")
                            ? code
                            : "COTACOES_P3_BATCH_REFUSED_PRIVATE_DETAILS_WITHHELD");
            return 2;
        }
    }

    public static ObjectNode execute(
            final JsonNode input,
            final ExportValidationSupport.Pin inputPin,
            final Path destination,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        return executeWithPostcheckProbe(input, inputPin, destination, budget, () -> {});
    }

    static ObjectNode executeWithPostcheckProbe(
            final JsonNode input,
            final ExportValidationSupport.Pin inputPin,
            final Path destination,
            final ExportValidationSupport.Budget budget,
            final Runnable beforePostcheck)
            throws Exception {
        fields(input, Set.of("schema", "privateRoot", "captures"));
        require(
                "COTACOES_P3_BATCH_QUALIFICATION_INPUT_V1".equals(text(input, "schema")),
                "COTACOES_P3_BATCH_SCHEMA");
        require(
                Arrays.equals(
                        ExportValidationSupport.canonicalBytes(input),
                        ExportValidationSupport.canonicalBytes(inputPin.json(262144, budget))),
                "COTACOES_P3_BATCH_INPUT_PIN_BINDING");
        final JsonNode admitted = input.deepCopy();
        require(
                admitted.path("captures").isArray() && admitted.get("captures").size() == 21,
                "COTACOES_P3_BATCH_EXACT21");
        final List<Daily> days = new ArrayList<>();
        final Set<Path> inputPaths = new HashSet<>();
        inputPaths.add(inputPin.path());
        LocalDate first = null;
        long payloadBytes = 0;
        long terminalBytes = 0;
        for (final var entry : admitted.required("captures")) {
            budget.check();
            fields(entry, Set.of("requestPin", "payloadPin", "terminalPin"));
            final var requestPin = normalizedPin(entry.required("requestPin"));
            final JsonNode request = requestPin.json(262144, budget);
            CotacoesIndependentRequest.validate(request);
            require(
                    text(request, "periodStart").equals(text(request, "periodEnd")),
                    "COTACOES_P3_BATCH_DAILY_REQUEST");
            final LocalDate day = LocalDate.parse(text(request, "periodStart"));
            if (first == null) {
                first = day;
            }
            require(
                    day.equals(first.plusDays(days.size())),
                    "COTACOES_P3_BATCH_CONSECUTIVE_UNIQUE_DAYS");
            final var payload = entry.required("payloadPin").deepCopy();
            final long bytes = payloadSize(payload);
            final var terminal = normalizedPin(entry.required("terminalPin"));
            payloadBytes = Math.addExact(payloadBytes, bytes);
            terminalBytes = Math.addExact(terminalBytes, terminal.bytes());
            require(payloadBytes + terminalBytes <= MAX_BYTES, "COTACOES_P3_BATCH_AGGREGATE_8MIB");
            inputPaths.add(requestPin.path());
            inputPaths.add(Path.of(text(payload, "path")).normalize());
            inputPaths.add(terminal.path());
            days.add(new Daily(requestPin, request, payload, terminal));
        }
        final Path root = Path.of(text(admitted, "privateRoot"));
        final Path receiptPath = privateOutput(root, destination, inputPaths);
        final var diagnostic =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "COTACOES_P3_BATCH_QUALIFICATION_PRIVATE_V1")
                        .put("p3ShapeSha256", CotacoesP3RawQualifier.SHAPE_SHA)
                        .put("diagnosticOnly", true)
                        .put("captureCount", 21)
                        .put("aggregatePayloadBytes", payloadBytes)
                        .put("aggregateTerminalBytes", terminalBytes)
                        .put("sourceCompletenessProven", false)
                        .put("producerReceiptsModified", false)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("mainResumeAllowed", false)
                        .put("runtimeAdopted", false)
                        .put("productionAuthorized", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0);
        diagnostic.set("inputPin", ExportValidationSupport.JSON.valueToTree(inputPin));
        final var results = diagnostic.putArray("results");
        final var publicResults = ExportValidationSupport.JSON.createArrayNode();
        long rows = 0;
        long rejected = 0;
        boolean allQualified = true;
        for (int index = 0; index < days.size(); index++) {
            budget.check();
            final var day = days.get(index);
            final var qualified =
                    CotacoesP3RawQualifier.qualify(
                            day.request(),
                            day.payloadPin(),
                            ExportValidationSupport.JSON.valueToTree(day.terminalPin()),
                            budget);
            rows = Math.addExact(rows, qualified.required("rows").longValue());
            rejected = Math.addExact(rejected, qualified.required("rejectedRows").longValue());
            require(rows <= 12000, "COTACOES_P3_BATCH_AGGREGATE_12000_ROWS");
            final boolean acceptedTypes = qualified.required("rowTypesQualified").booleanValue();
            allQualified &= acceptedTypes;
            qualified
                    .put("index", index + 1)
                    .put("periodStart", text(day.request(), "periodStart"))
                    .put("periodEnd", text(day.request(), "periodEnd"));
            qualified.set("requestPin", ExportValidationSupport.JSON.valueToTree(day.requestPin()));
            results.add(qualified);
            publicResults
                    .addObject()
                    .put("index", index + 1)
                    .put("rows", qualified.required("rows").longValue())
                    .put("rejectedRows", qualified.required("rejectedRows").longValue())
                    .put("typesQualified", acceptedTypes)
                    .put("requestFileSha256", day.requestPin().sha256())
                    .put("payloadSha256", text(day.payloadPin(), "sha256").toLowerCase(Locale.ROOT))
                    .put("terminalSha256", day.terminalPin().sha256());
        }
        beforePostcheck.run();
        postcheck(days, inputPin, budget);
        diagnostic
                .put("rows", rows)
                .put("rejectedRows", rejected)
                .put("typesQualifiedAll21", allQualified)
                .put("pinsVerifiedBeforeAndAfter", true);
        final byte[] privateBytes = ExportValidationSupport.JSON.writeValueAsBytes(diagnostic);
        require(privateBytes.length <= PRIVATE_BOUND, "COTACOES_P3_BATCH_PRIVATE_BOUND");
        budget.check();
        writeFresh(destination, privateBytes);
        final var privatePin =
                new ExportValidationSupport.Pin(
                        destination,
                        ExportValidationSupport.sha256(privateBytes),
                        privateBytes.length);
        privatePin.read(PRIVATE_BOUND, budget);
        final var receipt =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "COTACOES_P3_BATCH_QUALIFICATION_RECEIPT_V1")
                        .put("success", true)
                        .put("businessValuesPublished", false)
                        .put("diagnosticOnly", true)
                        .put("typesQualifiedAll21", allQualified)
                        .put("captureCount", 21)
                        .put("rows", rows)
                        .put("rejectedRows", rejected)
                        .put("aggregatePayloadBytes", payloadBytes)
                        .put("aggregateTerminalBytes", terminalBytes)
                        .put("pinsVerifiedBeforeAndAfter", true)
                        .put("p3ShapeSha256", CotacoesP3RawQualifier.SHAPE_SHA)
                        .put("inputSha256", inputPin.sha256())
                        .put("diagnosticSha256", privatePin.sha256())
                        .put("sourceCompletenessProven", false)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("mainResumeAllowed", false)
                        .put("productionAuthorized", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0);
        receipt.set("results", publicResults);
        postcheck(days, inputPin, budget);
        final byte[] receiptBytes = ExportValidationSupport.JSON.writeValueAsBytes(receipt);
        require(receiptBytes.length <= 262144, "COTACOES_P3_BATCH_RECEIPT_BOUND");
        writeFresh(receiptPath, receiptBytes);
        final var receiptPin =
                new ExportValidationSupport.Pin(
                        receiptPath,
                        ExportValidationSupport.sha256(receiptBytes),
                        receiptBytes.length);
        receiptPin.read(262144, budget);
        privatePin.read(PRIVATE_BOUND, budget);
        return receipt;
    }

    private static void postcheck(
            final List<Daily> days,
            final ExportValidationSupport.Pin input,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        input.read(262144, budget);
        for (final var day : days) {
            day.requestPin().read(262144, budget);
            readPayload(day.payloadPin(), budget);
            day.terminalPin().read(MAX_BYTES, budget);
        }
        CotacoesP3RawQualifier.contract();
    }

    private static long payloadSize(final JsonNode node) {
        fields(node, Set.of("path", "bytes", "sha256"));
        require(
                node.path("bytes").isIntegralNumber()
                        && node.get("bytes").canConvertToLong()
                        && node.get("bytes").longValue() >= 0
                        && node.get("bytes").longValue() <= MAX_BYTES,
                "COTACOES_P3_BATCH_PAYLOAD_SIZE");
        require(
                text(node, "sha256").matches("[a-fA-F0-9]{64}"),
                "COTACOES_P3_BATCH_PAYLOAD_DIGEST");
        final Path path = Path.of(text(node, "path"));
        require(
                path.isAbsolute() && path.normalize().equals(path),
                "COTACOES_P3_BATCH_PAYLOAD_PATH");
        return node.get("bytes").longValue();
    }

    private static void readPayload(
            final JsonNode node, final ExportValidationSupport.Budget budget) throws Exception {
        if (payloadSize(node) > 0) {
            normalizedPin(node).read(MAX_BYTES, budget);
            return;
        }
        final Path path = Path.of(text(node, "path"));
        for (Path part = path; part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "COTACOES_P3_BATCH_EMPTY_LINK");
        }
        budget.check();
        require(
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                "COTACOES_P3_BATCH_EMPTY_FILE");
        final byte[] bytes;
        try (var stream = Files.newInputStream(path)) {
            bytes = stream.readNBytes(1);
        }
        require(
                bytes.length == 0
                        && ExportValidationSupport.sha256(bytes)
                                .equals(text(node, "sha256").toLowerCase(Locale.ROOT)),
                "COTACOES_P3_BATCH_EMPTY_PIN_CHANGED");
    }

    private static ExportValidationSupport.Pin normalizedPin(final JsonNode node) {
        fields(node, Set.of("path", "bytes", "sha256"));
        require(
                node.path("bytes").isIntegralNumber() && node.get("bytes").canConvertToLong(),
                "COTACOES_P3_BATCH_PIN_SIZE");
        return new ExportValidationSupport.Pin(
                Path.of(text(node, "path")),
                text(node, "sha256").toLowerCase(Locale.ROOT),
                node.get("bytes").longValue());
    }

    private static Path privateOutput(final Path root, final Path output, final Set<Path> inputs)
            throws Exception {
        require(
                root.isAbsolute()
                        && root.normalize().equals(root)
                        && Files.isDirectory(root)
                        && output != null
                        && output.isAbsolute()
                        && output.normalize().equals(output)
                        && output.startsWith(root)
                        && !output.equals(root)
                        && Files.isDirectory(output.getParent()),
                "COTACOES_P3_BATCH_PRIVATE_ROOT");
        final Path receipt = output.resolveSibling(output.getFileName() + ".receipt.json");
        require(
                !Files.exists(output)
                        && !Files.exists(receipt)
                        && !inputs.contains(output)
                        && !inputs.contains(receipt),
                "COTACOES_P3_BATCH_FRESH_OUTPUT_NO_COLLISION");
        for (Path part = output; part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "COTACOES_P3_BATCH_OUTPUT_LINK");
        }
        require(
                output.getParent().toRealPath().startsWith(root.toRealPath()),
                "COTACOES_P3_BATCH_REAL_PRIVATE_ROOT");
        return receipt;
    }

    private static void fields(final JsonNode node, final Set<String> expected) {
        require(node != null && node.isObject(), "COTACOES_P3_BATCH_OBJECT");
        final var names = new HashSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        require(names.equals(expected), "COTACOES_P3_BATCH_EXACT_FIELDS");
    }

    private static void writeFresh(final Path path, final byte[] bytes) throws Exception {
        try (var channel =
                FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }
}
