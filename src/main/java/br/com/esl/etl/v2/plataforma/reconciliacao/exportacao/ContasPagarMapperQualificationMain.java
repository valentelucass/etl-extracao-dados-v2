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
public final class ContasPagarMapperQualificationMain {
    private ContasPagarMapperQualificationMain() {}

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
            err.println("CONTAS_A_PAGAR_REQUALIFICATION_USAGE");
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
                            : "CONTAS_A_PAGAR_REQUALIFICATION_PRIVATE_FAILURE_WITHHELD");
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
                "ROOT_CONTAS_A_PAGAR_RAW_REQUALIFICATION_INPUT_V1".equals(text(input, "schema")),
                "CONTAS_A_PAGAR_REQUALIFICATION_INPUT_SCHEMA");
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
                "CONTAS_A_PAGAR_REQUALIFICATION_PRIVATE_OUTPUT");
        final Path receiptPath =
                destination.resolveSibling(destination.getFileName() + ".receipt.json");
        require(
                !Files.exists(destination) && !Files.exists(receiptPath),
                "CONTAS_A_PAGAR_REQUALIFICATION_FRESH_OUTPUT");
        for (Path path = destination; path != null; path = path.getParent()) {
            require(!Files.isSymbolicLink(path), "CONTAS_A_PAGAR_REQUALIFICATION_OUTPUT_LINK");
        }
        require(
                destination.getParent().toRealPath().startsWith(root.toRealPath()),
                "CONTAS_A_PAGAR_REQUALIFICATION_REAL_PRIVATE_OUTPUT");
        final var testPin = ContasPagarRawMapperQualifier.pin(input.required("consumerTestPin"));
        final var unitPin = ContasPagarRawMapperQualifier.pin(input.required("codeUnitPin"));
        final var catalogPin = ContasPagarRawMapperQualifier.pin(input.required("catalogPin"));
        final var test = testPin.json(1048576, budget);
        for (final String flag :
                Set.of("success", "syntheticTestsPassed", "originalClosedKnown", "stateRestored")) {
            require(
                    test.path(flag).isBoolean() && test.get(flag).booleanValue(),
                    "CONTAS_A_PAGAR_REQUALIFICATION_CLOSED_CONSUMER_TEST_REQUIRED");
        }
        require(
                unitPin.equals(ContasPagarRawMapperQualifier.pin(test.required("unit"))),
                "CONTAS_A_PAGAR_REQUALIFICATION_EXACT_CONSUMER_UNIT");
        unitPin.read(8388608, budget);
        final JsonNode catalog = catalogPin.json(65536, budget);
        require(
                "CONTAS_A_PAGAR_NATIVE28_CANDIDATE_CATALOG_V1".equals(text(catalog, "schema"))
                        && ContasPagarIndependentRequest.PROFILE.equals(text(catalog, "profile"))
                        && "CONTAS_A_PAGAR".equals(text(catalog, "entity"))
                        && catalog.path("reportId").isIntegralNumber()
                        && catalog.path("reportId").canConvertToInt()
                        && catalog.path("reportId").intValue() == 8636
                        && catalog.path("columns").isArray()
                        && catalog.path("columns").size() == 28,
                "CONTAS_A_PAGAR_REQUALIFICATION_CATALOG_CONTEXT");
        require(
                input.path("captures").isArray()
                        && input.path("captures").size() > 0
                        && input.path("captures").size() <= 3,
                "CONTAS_A_PAGAR_REQUALIFICATION_ONE_TO_THREE_CAPTURES");
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
            fields(
                    entry,
                    Set.of("producerSummaryPin", "producerClosurePin", "requestPin", "sourcePin"));
            final var producerPin =
                    ContasPagarRawMapperQualifier.pin(entry.required("producerSummaryPin"));
            final var producerClosurePin =
                    ContasPagarRawMapperQualifier.pin(entry.required("producerClosurePin"));
            final var requestPin = ContasPagarRawMapperQualifier.pin(entry.required("requestPin"));
            final var sourcePin = ContasPagarRawMapperQualifier.pin(entry.required("sourcePin"));
            require(
                    sourcePaths.add(sourcePin.path()) && requestPaths.add(requestPin.path()),
                    "CONTAS_A_PAGAR_REQUALIFICATION_DUPLICATE_CAPTURE");
            pins.add(producerPin);
            pins.add(producerClosurePin);
            pins.add(requestPin);
            pins.add(sourcePin);
            final var producer = producerPin.json(262144, budget);
            require(
                    "ROOT_CONTAS_A_PAGAR_FRESH_PART_CAPTURE_CLOSED_CANDIDATE_V1"
                            .equals(text(producer, "schema")),
                    "CONTAS_A_PAGAR_REQUALIFICATION_PRODUCER_SCHEMA");
            final var producerFlags =
                    Set.of(
                            "success",
                            "physicalSourceCountersFinal",
                            "allOccurrencesRetained",
                            "terminalEmptyPageObserved");
            for (final String flag : producerFlags) {
                require(
                        producer.path(flag).isBoolean() && producer.get(flag).booleanValue(),
                        "CONTAS_A_PAGAR_REQUALIFICATION_CLOSED_COMPLETE_PRODUCER");
            }
            require(
                    producer.path("mapperQualified").isBoolean()
                            && requestPin.equals(
                                    ContasPagarRawMapperQualifier.pin(producer.required("request")))
                            && sourcePin.equals(
                                    ContasPagarRawMapperQualifier.pin(
                                            producer.required("dataPin"))),
                    "CONTAS_A_PAGAR_REQUALIFICATION_EXACT_RAW_REQUEST_BINDING");
            requireProducerClosure(producerPin, producerClosurePin, producer, budget);
            final var request = requestPin.json(262144, budget);
            ContasPagarIndependentRequest.validate(request);
            final var sourceResultPin =
                    ContasPagarRawMapperQualifier.pin(producer.required("sourceResult"));
            pins.add(sourceResultPin);
            final var sourceResult = sourceResultPin.json(262144, budget);
            require(
                    "CONTAS_A_PAGAR_SOURCE_COMPLETE".equals(text(sourceResult, "status"))
                            && ContasPagarIndependentRequest.PROFILE.equals(
                                    text(sourceResult, "profile"))
                            && ContasPagarIndependentRequest.FILTER_SCOPE_POLICY.equals(
                                    text(sourceResult, "filterScopePolicy"))
                            && sourceResult.path("createdAtFilter").isTextual()
                            && sourceResult.get("createdAtFilter").textValue().isEmpty()
                            && sourceResult.path("createdAtScopeEnforced").isBoolean()
                            && !sourceResult.get("createdAtScopeEnforced").booleanValue()
                            && sourceResult.path("terminalEmptyPageObserved").asBoolean(false)
                            && sourceResult.path("sourceRootIdentityQualified").isBoolean()
                            && !sourceResult.path("sourceRootIdentityQualified").booleanValue()
                            && sourceResult
                                    .path("installmentSequenceUsedAsRootIdentity")
                                    .isBoolean()
                            && !sourceResult
                                    .path("installmentSequenceUsedAsRootIdentity")
                                    .booleanValue()
                            && "ACTUAL_EMPTY_DATA_ARRAY_ONLY_V1"
                                    .equals(text(sourceResult, "terminalPolicy"))
                            && ExportValidationSupport.sha256(
                                            ExportValidationSupport.canonicalBytes(request))
                                    .equals(text(sourceResult, "requestSha256"))
                            && sourcePin.equals(
                                    ContasPagarRawMapperQualifier.pin(
                                            sourceResult.required("dataPin")))
                            && sourceResult.path("responses").isArray()
                            && sourceResult.path("responses").size() > 1
                            && sourceResult.path("responses").size() <= 17,
                    "CONTAS_A_PAGAR_REQUALIFICATION_SOURCE_RESULT");
            int responseIndex = 0;
            long responseRows = 0;
            for (final var response : sourceResult.required("responses")) {
                require(
                        response.path("status").isIntegralNumber()
                                && response.path("status").canConvertToInt()
                                && response.path("status").intValue() == 200
                                && response.path("page").isIntegralNumber()
                                && response.path("page").canConvertToInt()
                                && response.path("page").intValue() == responseIndex,
                        "CONTAS_A_PAGAR_REQUALIFICATION_CONTIGUOUS_RESPONSES");
                final var bodyPin =
                        ContasPagarRawMapperQualifier.pin(response.required("rawBodyPin"));
                pins.add(bodyPin);
                final var body = bodyPin.json(8388608, budget);
                if (responseIndex > 0) {
                    require(body.isArray(), "CONTAS_A_PAGAR_REQUALIFICATION_DATA_ARRAY_REQUIRED");
                    final boolean terminal =
                            responseIndex == sourceResult.path("responses").size() - 1;
                    require(
                            terminal ? body.isEmpty() : !body.isEmpty(),
                            "CONTAS_A_PAGAR_REQUALIFICATION_REAL_TERMINAL_ARRAY");
                    responseRows = Math.addExact(responseRows, body.size());
                }
                responseIndex++;
            }
            final var current =
                    ContasPagarRawMapperQualifier.qualify(requestPin, sourcePin, budget);
            require(
                    current.path("rows").longValue() == responseRows
                            && producer.path("rows").isIntegralNumber()
                            && producer.path("rows").canConvertToLong()
                            && sourceResult.path("rows").isIntegralNumber()
                            && sourceResult.path("rows").canConvertToLong()
                            && current.path("rows").longValue() == producer.path("rows").longValue()
                            && current.path("rows").longValue()
                                    == sourceResult.path("rows").longValue(),
                    "CONTAS_A_PAGAR_REQUALIFICATION_RAW_RESPONSE_ROW_BINDING");
            current.put("producerMapperQualified", producer.get("mapperQualified").booleanValue())
                    .put("historicalProducerFactRewritten", false)
                    .put("actualTerminalArrayVerified", true)
                    .put("allOriginalResponseBodiesReadToEof", true);
            current.set(
                    "producerSummaryPin", ExportValidationSupport.JSON.valueToTree(producerPin));
            current.set(
                    "producerClosurePin",
                    ExportValidationSupport.JSON.valueToTree(producerClosurePin));
            current.put("producerPhysicalClosureVerified", true);
            final var producerTestPin =
                    ContasPagarRawMapperQualifier.pin(producer.required("test"));
            producerTestPin.read(1048576, budget);
            pins.add(producerTestPin);
            current.set(
                    "producerTestPin", ExportValidationSupport.JSON.valueToTree(producerTestPin));
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
                    "CONTAS_A_PAGAR_REQUALIFICATION_INPUT_OUTPUT_COLLISION");
            pin.read(pin == catalogPin ? 65536 : 8388608, budget);
        }
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "CONTAS_A_PAGAR_SOURCE_REQUALIFICATION_V1")
                        .put("success", rejected == 0)
                        .put("profile", ContasPagarIndependentRequest.PROFILE)
                        .put("captureCount", captures.size())
                        .put("rows", rows)
                        .put("rejectedRows", rejected)
                        .put("rowTypesQualified", allTypes)
                        .put("mapperQualified", allMappers)
                        .put("currentMapperInvocations", rows)
                        .put("allRawRowsReadToEof", true)
                        .put("actualTerminalArraysVerified", true)
                        .put("pinsVerifiedBeforeAndAfter", true)
                        .put("allProducerPhysicalClosuresVerified", true)
                        .put("filterScopePolicy", ContasPagarIndependentRequest.FILTER_SCOPE_POLICY)
                        .put("createdAtFilter", "")
                        .put("createdAtScopeEnforced", false)
                        .put("historicalProducerFactsRewritten", false)
                        .put("nativePresentationUsedForMapperQualification", false)
                        .put("businessValuesPublished", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0)
                        .put("sourceRootIdentityQualified", false)
                        .put("installmentSequenceUsedAsRootIdentity", false)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("mainResumeAllowed", false);
        result.set("captures", captures);
        result.set("consumerTestPin", ExportValidationSupport.JSON.valueToTree(testPin));
        result.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(unitPin));
        result.set("catalogPin", ExportValidationSupport.JSON.valueToTree(catalogPin));
        result.set("executionInputPin", ExportValidationSupport.JSON.valueToTree(inputPin));
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(result);
        require(bytes.length <= 262144, "CONTAS_A_PAGAR_REQUALIFICATION_RESULT_BOUND");
        write(destination, bytes);
        final var diagnosticPin =
                new ExportValidationSupport.Pin(
                        destination, ExportValidationSupport.sha256(bytes), bytes.length);
        diagnosticPin.read(262144, budget);
        final var receipt =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "CONTAS_A_PAGAR_OFFLINE_REQUALIFICATION_EXECUTED_RECEIPT_V1")
                        .put("profile", ContasPagarIndependentRequest.PROFILE)
                        .put("filterScopePolicy", ContasPagarIndependentRequest.FILTER_SCOPE_POLICY)
                        .put("createdAtFilter", "")
                        .put("createdAtScopeEnforced", false)
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
        require(receiptBytes.length <= 262144, "CONTAS_A_PAGAR_REQUALIFICATION_RECEIPT_BOUND");
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

    private static void requireProducerClosure(
            final ExportValidationSupport.Pin producerPin,
            final ExportValidationSupport.Pin closurePin,
            final JsonNode producer,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                producerPin.path().getParent().equals(closurePin.path().getParent())
                        && "parent-result.json".equals(closurePin.path().getFileName().toString()),
                "CONTAS_A_PAGAR_REQUALIFICATION_PRODUCER_CLOSURE_RUN_BINDING");
        final var closure = closurePin.json(262144, budget);
        require(
                "ROOT_CONTAS_A_PAGAR_FINITE_SOURCE_CLOSURE0751_V1".equals(text(closure, "schema"))
                        && text(producer, "executingRootUUID")
                                .equals(text(closure, "executingRootUUID")),
                "CONTAS_A_PAGAR_REQUALIFICATION_PRODUCER_CLOSURE_CONTEXT");
        for (final String flag :
                Set.of("success", "originalClosedKnown", "stateRestored", "OriginalJobStarted")) {
            require(
                    closure.path(flag).isBoolean() && closure.path(flag).booleanValue(),
                    "CONTAS_A_PAGAR_REQUALIFICATION_PRODUCER_CLOSED_RESTORED");
        }
        require(
                closure.path("slotRetained").isBoolean()
                        && !closure.path("slotRetained").booleanValue(),
                "CONTAS_A_PAGAR_REQUALIFICATION_PRODUCER_SLOT_EMPTY");
        for (final String cap : Set.of("OriginalCalls", "SourceMaximum")) {
            require(
                    closure.path(cap).isIntegralNumber()
                            && closure.path(cap).canConvertToInt()
                            && closure.path(cap).intValue() == 1,
                    "CONTAS_A_PAGAR_REQUALIFICATION_PRODUCER_EXACT_CAPS");
        }
        require(
                closure.path("HTTPMaximum").isIntegralNumber()
                        && closure.path("HTTPMaximum").canConvertToInt()
                        && closure.path("HTTPMaximum").intValue() == 17
                        && closure.path("SQL").isIntegralNumber()
                        && closure.path("SQL").canConvertToInt()
                        && closure.path("SQL").intValue() == 0,
                "CONTAS_A_PAGAR_REQUALIFICATION_PRODUCER_EXACT_CAPS");
    }

    private static void fields(final JsonNode object, final Set<String> expected) {
        require(object != null && object.isObject(), "CONTAS_A_PAGAR_REQUALIFICATION_OBJECT");
        final var actual = new HashSet<String>();
        object.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected), "CONTAS_A_PAGAR_REQUALIFICATION_EXPLICIT_FIELDS");
    }

    private static void write(final Path path, final byte[] bytes) throws Exception {
        try (var file =
                FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                require(file.write(buffer) > 0, "CONTAS_A_PAGAR_REQUALIFICATION_WRITE_PROGRESS");
            }
            file.force(true);
        }
    }
}
