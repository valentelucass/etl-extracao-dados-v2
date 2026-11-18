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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Offline page/day diagnostics. Private evidence is written once; stdout receives only a bounded
 * receipt/summary.
 */
public final class ManifestosPageDiagnosticMain {
    private ManifestosPageDiagnosticMain() {}

    public static void main(final String[] args) {
        final int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(final String[] args, final PrintStream out, final PrintStream err) {
        if (args.length != 7
                || !("page".equals(args[0]) || "day".equals(args[0]))
                || !"--input".equals(args[1])
                || !"--sha256".equals(args[3])
                || !"--output".equals(args[5])) {
            err.println("MANIFESTOS_PAGE_DIAGNOSTIC_USAGE");
            return 2;
        }
        try {
            final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(150));
            final Path path = Path.of(args[2]).toAbsolutePath().normalize();
            final var inputPin = new ExportValidationSupport.Pin(path, args[4], Files.size(path));
            final var input = inputPin.json(262144, budget);
            final Path root = Path.of(text(input, "privateRoot"));
            final Path output = Path.of(args[6]);
            require(
                    root.isAbsolute()
                            && root.normalize().equals(root)
                            && Files.isDirectory(root)
                            && output.isAbsolute()
                            && output.normalize().equals(output)
                            && output.startsWith(root)
                            && !output.equals(root)
                            && Files.isDirectory(output.getParent())
                            && !Files.exists(output),
                    "MANIFESTOS_PAGE_PRIVATE_FRESH_OUTPUT");
            for (Path at = output; at != null; at = at.getParent()) {
                require(!Files.isSymbolicLink(at), "MANIFESTOS_PAGE_OUTPUT_LINK");
            }
            require(
                    output.getParent().toRealPath().startsWith(root.toRealPath()),
                    "MANIFESTOS_PAGE_OUTPUT_REAL_ROOT");
            final ObjectNode result =
                    "page".equals(args[0])
                            ? page(input, budget)
                            : ManifestosDayAssembler.assemble(input, budget);
            inputPin.read(262144, budget);
            result.set("diagnosticInputPin", ExportValidationSupport.JSON.valueToTree(inputPin));
            final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(result);
            require(
                    bytes.length > 0 && bytes.length <= 8388608,
                    "MANIFESTOS_PAGE_DIAGNOSTIC_RESULT_BOUND");
            try (var file =
                    FileChannel.open(
                            output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                final var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    require(file.write(buffer) > 0, "MANIFESTOS_PAGE_DIAGNOSTIC_WRITE");
                }
                file.force(true);
            }
            final var resultPin =
                    new ExportValidationSupport.Pin(
                            output, ExportValidationSupport.sha256(bytes), bytes.length);
            resultPin.read(8388608, budget);
            inputPin.read(262144, budget);
            final var receipt =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "MANIFESTOS_PAGE_DIAGNOSTIC_EXECUTED_V1")
                            .put("mode", args[0])
                            .put("privateResultVerified", true)
                            .put("contentAccepted", false)
                            .put("entityQualified", false)
                            .put("productionAuthorized", false)
                            .put("Source", 0)
                            .put("HTTP", 0)
                            .put("SQL", 0);
            receipt.set("resultPin", ExportValidationSupport.JSON.valueToTree(resultPin));
            receipt.set("inputPin", ExportValidationSupport.JSON.valueToTree(inputPin));
            receipt.set("summary", result.required("summary").deepCopy());
            require(
                    ExportValidationSupport.JSON.writeValueAsBytes(receipt).length <= 262144,
                    "MANIFESTOS_PAGE_RECEIPT_BOUND");
            out.println(ExportValidationSupport.JSON.writeValueAsString(receipt));
            return result.required("summary").path("pageValuesEqual").asBoolean()
                            || result.required("summary").path("comparisonPassed").asBoolean()
                    ? 0
                    : 3;
        } catch (final Exception refused) {
            final String code = refused.getMessage();
            err.println(
                    code != null && code.matches("[A-Z][A-Z0-9_]{1,159}")
                            ? code
                            : "MANIFESTOS_PAGE_DIAGNOSTIC_PRIVATE_REFUSAL");
            return 2;
        }
    }

    static ObjectNode page(final JsonNode input, final ExportValidationSupport.Budget budget)
            throws Exception {
        final var expectedFields =
                new HashSet<>(
                        Set.of(
                                "schema",
                                "capturePin",
                                "captureClosurePin",
                                "nativeWorkbookPin",
                                "codeUnitPin",
                                "sourceConfigurationSha256",
                                "privateRoot"));
        if (input.has("rawRevalidationBindingPin")) {
            expectedFields.add("rawRevalidationBindingPin");
        }
        ManifestosDayAssembler.fields(input, expectedFields);
        require(
                "MANIFESTOS_PAGE_DIAGNOSTIC_INPUT_V1".equals(text(input, "schema")),
                "MANIFESTOS_PAGE_DIAGNOSTIC_SCHEMA");
        final var pins = new ArrayList<ExportValidationSupport.Pin>();
        final var capturePin = ManifestosDayAssembler.track(input.required("capturePin"), pins);
        final var closurePin =
                ManifestosDayAssembler.track(input.required("captureClosurePin"), pins);
        final var codePin = ManifestosDayAssembler.track(input.required("codeUnitPin"), pins);
        codePin.read(8388608, budget);
        final String fingerprint =
                ManifestosDayAssembler.digest(input, "sourceConfigurationSha256");
        final var revalidation =
                ManifestosRawRevalidationBinding.open(input, codePin, fingerprint, pins, budget);
        final var capture =
                ManifestosDayAssembler.capture(
                        capturePin, closurePin, codePin, fingerprint, pins, budget, revalidation);
        final var requestPin = ManifestosDayAssembler.track(capture.required("requestPin"), pins);
        final var workbookPin =
                ManifestosDayAssembler.track(input.required("nativeWorkbookPin"), pins);
        final var dataPin = ManifestosDayAssembler.track(capture.required("dataPin"), pins);
        final var result =
                ManifestosPageAdapter.comparePage(
                        requestPin.json(262144, budget), dataPin, workbookPin, budget);
        require(
                ManifestosDayAssembler.number(result.required("summary"), "sourceRows")
                                == ManifestosDayAssembler.number(capture, "rows")
                        && ManifestosDayAssembler.number(
                                        result.required("summary"), "sourceDistinctRoots")
                                == ManifestosDayAssembler.number(capture, "roots"),
                "MANIFESTOS_PAGE_CAPTURE_ROW_BINDING");
        final var binding =
                result.putObject("executionBinding")
                        .put("schema", "MANIFESTOS_PAGE_DIAGNOSTIC_BINDING_V1")
                        .put("pageIndex", ManifestosDayAssembler.number(capture, "pageIndex"))
                        .put("sourceConfigurationSha256", fingerprint);
        binding.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(codePin));
        binding.set("capturePin", ExportValidationSupport.JSON.valueToTree(capturePin));
        binding.set("captureClosurePin", ExportValidationSupport.JSON.valueToTree(closurePin));
        binding.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
        ((ObjectNode) result.required("summary"))
                .put("sourceConfigurationSha256", fingerprint)
                .put("codeUnitSha256", codePin.sha256());
        if (revalidation != null) {
            revalidation.bind(binding, (ObjectNode) result.required("summary"));
        }
        for (final var pin : pins) {
            pin.read(8388608, budget);
        }
        return result;
    }
}
