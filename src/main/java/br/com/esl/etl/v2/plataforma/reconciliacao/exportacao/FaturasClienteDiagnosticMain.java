package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Source0 only. Full diagnostics are private files; standard output carries only a bounded receipt.
 */
public final class FaturasClienteDiagnosticMain {
    private static final int DIAGNOSTIC_BOUND = 2097152;

    private FaturasClienteDiagnosticMain() {}

    public static void main(final String[] args) {
        final int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(final String[] args, final PrintStream out, final PrintStream err) {
        if (args == null
                || args.length != 7
                || !"compare".equals(args[0])
                || !"--input".equals(args[1])
                || !"--sha256".equals(args[3])
                || !"--output".equals(args[5])) {
            err.println("FATURAS_CLIENTE_DIAGNOSTIC_USAGE");
            return 2;
        }
        try {
            final var clock = Clock.systemUTC();
            final var started = clock.instant();
            final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(150));
            final Path path = Path.of(args[2]).toAbsolutePath().normalize();
            final var pin =
                    new ExportValidationSupport.Pin(
                            path, args[4].toLowerCase(java.util.Locale.ROOT), Files.size(path));
            final var input = pin.json(262144, budget);
            final var receipt = execute(input, pin, Path.of(args[6]), started, clock, budget);
            out.println(ExportValidationSupport.JSON.writeValueAsString(receipt));
            return receipt.path("contentEqualUnderCandidatePresentation").booleanValue() ? 0 : 3;
        } catch (final Exception refused) {
            final String code = refused.getMessage();
            err.println(
                    code != null && code.matches("[A-Z][A-Z0-9_]{1,159}")
                            ? code
                            : "FATURAS_CLIENTE_DIAGNOSTIC_REFUSED_PRIVATE_DETAILS_WITHHELD");
            return 2;
        }
    }

    public static ObjectNode execute(
            final JsonNode input,
            final ExportValidationSupport.Pin manifest,
            final Path destination,
            final Instant started,
            final Clock clock,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                input != null && input.isObject() && manifest != null,
                "FATURAS_CLIENTE_DIAGNOSTIC_INPUT_OBJECT");
        final var names = new HashSet<String>();
        input.fieldNames().forEachRemaining(names::add);
        require(
                names.equals(
                                Set.of(
                                        "schema",
                                        "privateRoot",
                                        "requestPin",
                                        "sourcePin",
                                        "workbookPin",
                                        "catalogPin"))
                        && "FATURAS_CLIENTE_OFFLINE_DIAGNOSTIC_INPUT_V1"
                                .equals(text(input, "schema")),
                "FATURAS_CLIENTE_DIAGNOSTIC_INPUT_SCOPE");
        require(
                Arrays.equals(
                        ExportValidationSupport.canonicalBytes(input),
                        ExportValidationSupport.canonicalBytes(manifest.json(262144, budget))),
                "FATURAS_CLIENTE_DIAGNOSTIC_INPUT_PIN_BINDING");
        final var request = exactPin(input.required("requestPin"));
        final var source = exactPin(input.required("sourcePin"));
        final var workbook = exactPin(input.required("workbookPin"));
        final var catalog = exactPin(input.required("catalogPin"));
        final Path root = Path.of(text(input, "privateRoot"));
        require(
                root.isAbsolute()
                        && root.normalize().equals(root)
                        && Files.isDirectory(root)
                        && destination != null
                        && destination.isAbsolute()
                        && destination.normalize().equals(destination),
                "FATURAS_CLIENTE_PRIVATE_ROOT_AND_OUTPUT_ABSOLUTE");
        final Path target = destination;
        final Path receiptPath = target.resolveSibling(target.getFileName() + ".receipt.json");
        require(
                target.startsWith(root)
                        && !target.equals(root)
                        && Files.isDirectory(target.getParent())
                        && !Files.exists(target)
                        && !Files.exists(receiptPath),
                "FATURAS_CLIENTE_FRESH_PRIVATE_DIAGNOSTIC_REQUIRED");
        for (Path part = target; part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "FATURAS_CLIENTE_OUTPUT_LINK_REFUSED");
        }
        require(
                target.getParent().toRealPath().startsWith(root.toRealPath()),
                "FATURAS_CLIENTE_OUTPUT_REAL_PRIVATE_ROOT");
        final var inputs = List.of(manifest, request, source, workbook, catalog);
        final var paths = new HashSet<Path>();
        for (final var pin : inputs) {
            require(
                    paths.add(pin.path())
                            && !pin.path().equals(target)
                            && !pin.path().equals(receiptPath),
                    "FATURAS_CLIENTE_DISTINCT_INPUTS_OUTPUT_COLLISION");
        }
        final var diagnostic =
                FaturasClienteContentAdapter.compare(
                        request.json(262144, budget), source, workbook, catalog, budget);
        for (final var pin : inputs) {
            pin.read(
                    pin == catalog ? 65536 : pin == manifest || pin == request ? 262144 : 8388608,
                    budget);
        }
        final String execution = UUID.randomUUID().toString();
        diagnostic.put("executionId", execution);
        diagnostic.set("manifestPin", ExportValidationSupport.JSON.valueToTree(manifest));
        diagnostic.set("requestPin", ExportValidationSupport.JSON.valueToTree(request));
        final byte[] diagnosticBytes = ExportValidationSupport.JSON.writeValueAsBytes(diagnostic);
        require(diagnosticBytes.length <= DIAGNOSTIC_BOUND, "FATURAS_CLIENTE_DIAGNOSTIC_SIZE");
        budget.check();
        writeFresh(target, diagnosticBytes);
        final var diagnosticPin =
                new ExportValidationSupport.Pin(
                        target,
                        ExportValidationSupport.sha256(diagnosticBytes),
                        diagnosticBytes.length);
        diagnosticPin.read(DIAGNOSTIC_BOUND, budget);
        final var receipt =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "FATURAS_CLIENTE_OFFLINE_DIAGNOSTIC_EXECUTED_RECEIPT_V1")
                        .put("profile", FaturasClienteIndependentRequest.PROFILE)
                        .put("filterScopePolicy", "SERVICE_AT_WITH_EXPLICIT_EMPTY_UPDATED_AT_V1")
                        .put("updatedAtFilter", "")
                        .put("updatedAtScopeEnforced", false)
                        .put("serviceAtScopePolicy", "SOURCE_OFFSET_CIVIL_DATE_V1")
                        .put("executionId", execution)
                        .put("attemptId", diagnostic.path("attemptId").asText())
                        .put("status", diagnostic.path("status").asText())
                        .put("startedAt", started.toString())
                        .put("finishedAt", clock.instant().toString())
                        .put("sourceRows", diagnostic.required("sourceRows").longValue())
                        .put("uiRows", diagnostic.required("uiRows").longValue())
                        .put("columns", 52)
                        .put("totalColumns", diagnostic.required("totalColumns").intValue())
                        .put("catalogNature", diagnostic.required("catalogNature").textValue())
                        .put("catalogBindingPoliciesQualified", false)
                        .put(
                                "contentEqualUnderCandidatePresentation",
                                diagnostic
                                        .required("contentEqualUnderCandidatePresentation")
                                        .booleanValue())
                        .put("diagnosticOnly", true)
                        .put("trustedSourceCompleteness", false)
                        .put("presentationPoliciesQualified", false)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("productionAuthorized", false)
                        .put("mainResumeAllowed", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0);
        receipt.set("manifestPin", ExportValidationSupport.JSON.valueToTree(manifest));
        receipt.set("requestPin", ExportValidationSupport.JSON.valueToTree(request));
        receipt.set("catalogPin", ExportValidationSupport.JSON.valueToTree(catalog));
        receipt.set("diagnosticPin", ExportValidationSupport.JSON.valueToTree(diagnosticPin));
        for (final var pin : inputs) {
            pin.read(
                    pin == catalog ? 65536 : pin == manifest || pin == request ? 262144 : 8388608,
                    budget);
        }
        FaturasClienteContentAdapter.catalog(catalog, budget);
        final byte[] receiptBytes = ExportValidationSupport.JSON.writeValueAsBytes(receipt);
        require(receiptBytes.length <= 262144, "FATURAS_CLIENTE_RECEIPT_SIZE");
        writeFresh(receiptPath, receiptBytes);
        final var receiptPin =
                new ExportValidationSupport.Pin(
                        receiptPath,
                        ExportValidationSupport.sha256(receiptBytes),
                        receiptBytes.length);
        receiptPin.read(262144, budget);
        diagnosticPin.read(DIAGNOSTIC_BOUND, budget);
        final var published = receipt.deepCopy();
        published.set("receiptPin", ExportValidationSupport.JSON.valueToTree(receiptPin));
        return published;
    }

    private static ExportValidationSupport.Pin exactPin(final JsonNode value) {
        require(value != null && value.isObject(), "FATURAS_CLIENTE_DIAGNOSTIC_PIN_OBJECT");
        final var names = new HashSet<String>();
        value.fieldNames().forEachRemaining(names::add);
        require(
                names.equals(Set.of("path", "sha256", "bytes")),
                "FATURAS_CLIENTE_DIAGNOSTIC_PIN_FIELDS");
        return ExportValidationSupport.Pin.from(value);
    }

    private static void writeFresh(final Path path, final byte[] bytes) throws Exception {
        try (var channel =
                FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                require(channel.write(buffer) > 0, "FATURAS_CLIENTE_DIAGNOSTIC_WRITE_PROGRESS");
            }
            channel.force(true);
        }
    }
}
