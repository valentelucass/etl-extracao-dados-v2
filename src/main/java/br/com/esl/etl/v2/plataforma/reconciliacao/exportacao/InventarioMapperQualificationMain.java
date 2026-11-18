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
 * Source0 only. A Consumer receipt supplements immutable Producer facts; it never rewrites them.
 */
public final class InventarioMapperQualificationMain {
    private InventarioMapperQualificationMain() {}

    public static void main(final String[] args) {
        final int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(final String[] args, final PrintStream out, final PrintStream err) {
        if (args == null
                || args.length != 7
                || !"qualify".equals(args[0])
                || !"--input".equals(args[1])
                || !"--sha256".equals(args[3])
                || !"--output".equals(args[5])) {
            err.println("INVENTARIO_REQUALIFICATION_USAGE");
            return 2;
        }
        try {
            final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(150));
            final Path path = Path.of(args[2]).toAbsolutePath().normalize();
            final var inputPin =
                    new ExportValidationSupport.Pin(
                            path, args[4].toLowerCase(java.util.Locale.ROOT), Files.size(path));
            final var receipt = execute(inputPin, Path.of(args[6]), budget);
            out.println(ExportValidationSupport.JSON.writeValueAsString(receipt));
            return receipt.path("success").booleanValue() ? 0 : 3;
        } catch (final Exception failure) {
            final String code = failure.getMessage();
            err.println(
                    code != null && code.matches("[A-Z][A-Z0-9_]{1,159}")
                            ? code
                            : "INVENTARIO_REQUALIFICATION_PRIVATE_FAILURE_WITHHELD");
            return 2;
        }
    }

    public static ObjectNode execute(
            final ExportValidationSupport.Pin inputPin,
            final Path destination,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        final JsonNode input = inputPin.json(262144, budget);
        fields(
                input,
                Set.of(
                        "schema",
                        "privateRoot",
                        "consumerTestPin",
                        "codeUnitPin",
                        "catalogPin",
                        "captures"));
        require(
                "ROOT_INVENTARIO_RAW_REQUALIFICATION_INPUT_V1".equals(text(input, "schema")),
                "INVENTARIO_REQUALIFICATION_INPUT_SCHEMA");
        final Path root = Path.of(text(input, "privateRoot"));
        require(
                root.isAbsolute()
                        && root.normalize().equals(root)
                        && Files.isDirectory(root)
                        && destination != null
                        && destination.isAbsolute()
                        && destination.normalize().equals(destination)
                        && destination.startsWith(root)
                        && !destination.equals(root)
                        && Files.isDirectory(destination.getParent()),
                "INVENTARIO_REQUALIFICATION_PRIVATE_OUTPUT");
        final Path receiptPath =
                destination.resolveSibling(destination.getFileName() + ".receipt.json");
        require(
                !Files.exists(destination) && !Files.exists(receiptPath),
                "INVENTARIO_REQUALIFICATION_FRESH_OUTPUT");
        for (Path path = destination; path != null; path = path.getParent()) {
            require(!Files.isSymbolicLink(path), "INVENTARIO_REQUALIFICATION_OUTPUT_LINK");
        }
        require(
                destination.getParent().toRealPath().startsWith(root.toRealPath()),
                "INVENTARIO_REQUALIFICATION_REAL_PRIVATE_OUTPUT");
        final var testPin = InventarioRawMapperQualifier.pin(input.required("consumerTestPin"));
        final var unitPin = InventarioRawMapperQualifier.pin(input.required("codeUnitPin"));
        final var catalogPin = InventarioRawMapperQualifier.pin(input.required("catalogPin"));
        final var test = testPin.json(1048576, budget);
        for (final String flag :
                Set.of("success", "syntheticTestsPassed", "originalClosedKnown", "stateRestored")) {
            require(
                    test.path(flag).isBoolean() && test.get(flag).booleanValue(),
                    "INVENTARIO_REQUALIFICATION_CLOSED_CONSUMER_TEST_REQUIRED");
        }
        require(
                unitPin.equals(InventarioRawMapperQualifier.pin(test.required("unit"))),
                "INVENTARIO_REQUALIFICATION_EXACT_CONSUMER_UNIT");
        unitPin.read(8388608, budget);
        final JsonNode catalog = catalogPin.json(65536, budget);
        require(
                "INVENTARIO_NATIVE26_CANDIDATE_CATALOG_V1".equals(text(catalog, "schema"))
                        && "INVENTARIO".equals(text(catalog, "entity"))
                        && catalog.path("reportId").intValue() == 10633
                        && catalog.path("columns").isArray()
                        && catalog.path("columns").size() == 26,
                "INVENTARIO_REQUALIFICATION_CATALOG_CONTEXT");
        require(
                input.path("captures").isArray()
                        && input.path("captures").size() > 0
                        && input.path("captures").size() <= 3,
                "INVENTARIO_REQUALIFICATION_ONE_TO_THREE_CAPTURES");
        final var pins = new ArrayList<ExportValidationSupport.Pin>();
        pins.add(inputPin);
        pins.add(testPin);
        pins.add(unitPin);
        pins.add(catalogPin);
        final var sourcePaths = new HashSet<Path>();
        final var requestPaths = new HashSet<Path>();
        final var captures = ExportValidationSupport.JSON.createArrayNode();
        long rows = 0;
        long rejected = 0;
        boolean allTypes = true;
        boolean allMappers = true;
        for (final var entry : input.required("captures")) {
            fields(entry, Set.of("producerSummaryPin", "requestPin", "sourcePin"));
            final var producerPin =
                    InventarioRawMapperQualifier.pin(entry.required("producerSummaryPin"));
            final var requestPin = InventarioRawMapperQualifier.pin(entry.required("requestPin"));
            final var sourcePin = InventarioRawMapperQualifier.pin(entry.required("sourcePin"));
            require(
                    sourcePaths.add(sourcePin.path()) && requestPaths.add(requestPin.path()),
                    "INVENTARIO_REQUALIFICATION_DUPLICATE_CAPTURE");
            pins.add(producerPin);
            pins.add(requestPin);
            pins.add(sourcePin);
            final var producer = producerPin.json(262144, budget);
            require(
                    "ROOT_INVENTARIO_FRESH_PART_CAPTURE_CLOSED_CANDIDATE_V1"
                            .equals(text(producer, "schema")),
                    "INVENTARIO_REQUALIFICATION_PRODUCER_SCHEMA");
            for (final String flag :
                    Set.of(
                            "success",
                            "sourceCountersFinal",
                            "allOccurrencesRetained",
                            "terminalEmptyPageObserved")) {
                require(
                        producer.path(flag).isBoolean() && producer.get(flag).booleanValue(),
                        "INVENTARIO_REQUALIFICATION_CLOSED_COMPLETE_PRODUCER");
            }
            require(
                    producer.path("mapperQualified").isBoolean()
                            && requestPin.equals(
                                    InventarioRawMapperQualifier.pin(producer.required("request")))
                            && sourcePin.equals(
                                    InventarioRawMapperQualifier.pin(producer.required("dataPin"))),
                    "INVENTARIO_REQUALIFICATION_EXACT_RAW_REQUEST_BINDING");
            final var sourceResultPin =
                    InventarioRawMapperQualifier.pin(producer.required("sourceResult"));
            pins.add(sourceResultPin);
            final var sourceResult = sourceResultPin.json(262144, budget);
            require(
                    "INVENTARIO_SOURCE_COMPLETE".equals(text(sourceResult, "status"))
                            && sourceResult.path("terminalEmptyPageObserved").asBoolean(false)
                            && sourcePin.equals(
                                    InventarioRawMapperQualifier.pin(
                                            sourceResult.required("dataPin")))
                            && sourceResult.path("responses").isArray()
                            && sourceResult.path("responses").size() > 1
                            && sourceResult.path("responses").size() <= 17,
                    "INVENTARIO_REQUALIFICATION_SOURCE_RESULT");
            int responseIndex = 0;
            long responseRows = 0;
            for (final var response : sourceResult.required("responses")) {
                require(
                        response.path("status").intValue() == 200
                                && response.path("page").intValue() == responseIndex,
                        "INVENTARIO_REQUALIFICATION_CONTIGUOUS_RESPONSES");
                final var bodyPin =
                        InventarioRawMapperQualifier.pin(response.required("rawBodyPin"));
                pins.add(bodyPin);
                final var body = bodyPin.json(8388608, budget);
                if (responseIndex > 0) {
                    require(body.isArray(), "INVENTARIO_REQUALIFICATION_DATA_ARRAY_REQUIRED");
                    final boolean terminal =
                            responseIndex == sourceResult.path("responses").size() - 1;
                    require(
                            terminal ? body.isEmpty() : !body.isEmpty(),
                            "INVENTARIO_REQUALIFICATION_REAL_TERMINAL_ARRAY");
                    responseRows = Math.addExact(responseRows, body.size());
                }
                responseIndex++;
            }
            final var current = InventarioRawMapperQualifier.qualify(requestPin, sourcePin, budget);
            require(
                    current.path("rows").longValue() == responseRows
                            && current.path("rows").longValue()
                                    == producer.path("rows").longValue(),
                    "INVENTARIO_REQUALIFICATION_RAW_RESPONSE_ROW_BINDING");
            current.put("producerMapperQualified", producer.get("mapperQualified").booleanValue())
                    .put("historicalProducerFactRewritten", false)
                    .put("actualTerminalArrayVerified", true)
                    .put("allOriginalResponseBodiesReadToEof", true);
            current.set(
                    "producerSummaryPin", ExportValidationSupport.JSON.valueToTree(producerPin));
            current.set(
                    "producerTestPin",
                    ExportValidationSupport.JSON.valueToTree(
                            InventarioRawMapperQualifier.pin(producer.required("test"))));
            current.set("consumerTestPin", ExportValidationSupport.JSON.valueToTree(testPin));
            current.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(unitPin));
            current.set("catalogPin", ExportValidationSupport.JSON.valueToTree(catalogPin));
            current.set(
                    "sourceResultPin", ExportValidationSupport.JSON.valueToTree(sourceResultPin));
            captures.add(current);
            rows = Math.addExact(rows, current.path("rows").longValue());
            rejected = Math.addExact(rejected, current.path("rejectedRows").longValue());
            allTypes &= current.path("rowTypesQualified").booleanValue();
            allMappers &= current.path("mapperQualified").booleanValue();
        }
        for (final var pin : pins) {
            require(
                    !pin.path().equals(destination) && !pin.path().equals(receiptPath),
                    "INVENTARIO_REQUALIFICATION_INPUT_OUTPUT_COLLISION");
            pin.read(pin == catalogPin ? 65536 : 8388608, budget);
        }
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INVENTARIO_SOURCE_REQUALIFICATION_V1")
                        .put("success", rejected == 0)
                        .put("captureCount", captures.size())
                        .put("rows", rows)
                        .put("rejectedRows", rejected)
                        .put("rowTypesQualified", allTypes)
                        .put("mapperQualified", allMappers)
                        .put("currentMapperInvocations", rows)
                        .put("allRawRowsReadToEof", true)
                        .put("actualTerminalArraysVerified", true)
                        .put("pinsVerifiedBeforeAndAfter", true)
                        .put("historicalProducerFactsRewritten", false)
                        .put("nativePresentationUsedForMapperQualification", false)
                        .put("businessValuesPublished", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("mainResumeAllowed", false);
        result.set("captures", captures);
        result.set("consumerTestPin", ExportValidationSupport.JSON.valueToTree(testPin));
        result.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(unitPin));
        result.set("catalogPin", ExportValidationSupport.JSON.valueToTree(catalogPin));
        result.set("executionInputPin", ExportValidationSupport.JSON.valueToTree(inputPin));
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(result);
        require(bytes.length <= 262144, "INVENTARIO_REQUALIFICATION_RESULT_BOUND");
        write(destination, bytes);
        final var diagnosticPin =
                new ExportValidationSupport.Pin(
                        destination, ExportValidationSupport.sha256(bytes), bytes.length);
        diagnosticPin.read(262144, budget);
        final var receipt =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INVENTARIO_OFFLINE_REQUALIFICATION_EXECUTED_RECEIPT_V1")
                        .put("operation", "qualify")
                        .put("success", rejected == 0)
                        .put("rows", rows)
                        .put("sourceRows", rows)
                        .put("captureCount", captures.size())
                        .put("rejectedRows", rejected)
                        .put("rowTypesQualified", allTypes)
                        .put("mapperQualified", allMappers)
                        .put("allRawRowsReadToEof", true)
                        .put("pinsVerifiedBeforeAndAfter", true)
                        .put("businessValuesPublished", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0)
                        .put("contentAccepted", false)
                        .put("entityQualified", false);
        receipt.set("diagnosticPin", ExportValidationSupport.JSON.valueToTree(diagnosticPin));
        receipt.set("consumerTestPin", ExportValidationSupport.JSON.valueToTree(testPin));
        receipt.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(unitPin));
        receipt.set("catalogPin", ExportValidationSupport.JSON.valueToTree(catalogPin));
        receipt.set("executionInputPin", ExportValidationSupport.JSON.valueToTree(inputPin));
        final byte[] receiptBytes = ExportValidationSupport.JSON.writeValueAsBytes(receipt);
        require(receiptBytes.length <= 262144, "INVENTARIO_REQUALIFICATION_RECEIPT_BOUND");
        write(receiptPath, receiptBytes);
        final var receiptPin =
                new ExportValidationSupport.Pin(
                        receiptPath,
                        ExportValidationSupport.sha256(receiptBytes),
                        receiptBytes.length);
        receiptPin.read(262144, budget);
        diagnosticPin.read(262144, budget);
        receipt.set("receiptPin", ExportValidationSupport.JSON.valueToTree(receiptPin));
        return receipt;
    }

    private static void fields(final JsonNode object, final Set<String> expected) {
        require(object != null && object.isObject(), "INVENTARIO_REQUALIFICATION_OBJECT");
        final var actual = new HashSet<String>();
        object.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected), "INVENTARIO_REQUALIFICATION_EXPLICIT_FIELDS");
    }

    private static void write(final Path path, final byte[] bytes) throws Exception {
        try (var file =
                FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                require(file.write(buffer) > 0, "INVENTARIO_REQUALIFICATION_WRITE_PROGRESS");
            }
            file.force(true);
        }
    }
}
