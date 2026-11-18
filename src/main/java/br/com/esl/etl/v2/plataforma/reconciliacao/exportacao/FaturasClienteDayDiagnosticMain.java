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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Source0 daily diagnostic; only the bounded receipt is published on standard output. */
public final class FaturasClienteDayDiagnosticMain {
    private static final int DIAGNOSTIC_BOUND = 2097152;

    private FaturasClienteDayDiagnosticMain() {}

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
            err.println("FATURAS_CLIENTE_DAY_DIAGNOSTIC_USAGE");
            return 2;
        }
        try {
            final var clock = Clock.systemUTC();
            final var started = clock.instant();
            final var budget = new ExportValidationSupport.Budget(Duration.ofSeconds(80));
            final Path path = Path.of(args[2]).toAbsolutePath().normalize();
            final var pin =
                    new ExportValidationSupport.Pin(
                            path, args[4].toLowerCase(java.util.Locale.ROOT), Files.size(path));
            final var receipt =
                    execute(
                            pin.json(262144, budget),
                            pin,
                            Path.of(args[6]),
                            started,
                            clock,
                            budget);
            out.println(ExportValidationSupport.JSON.writeValueAsString(receipt));
            return receipt.path("dayContentEqualUnderCandidatePresentation").booleanValue() ? 0 : 3;
        } catch (final Exception refused) {
            final String code = refused.getMessage();
            err.println(
                    code != null && code.matches("[A-Z][A-Z0-9_]{1,159}")
                            ? code
                            : "FATURAS_CLIENTE_DAY_REFUSED_PRIVATE_DETAILS_WITHHELD");
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
        fields(
                input,
                Set.of(
                        "schema",
                        "privateRoot",
                        "requestPin",
                        "sourcePins",
                        "workbookPin",
                        "catalogPin",
                        "day",
                        "emptyDayProofPin",
                        "emptyDayProofClosurePin",
                        "consumerTestPin",
                        "codeUnitPin"));
        require(
                "FATURAS_CLIENTE_OFFLINE_DAY_DIAGNOSTIC_INPUT_V1".equals(text(input, "schema")),
                "FAT_DAY_DIAGNOSTIC_INPUT_SCHEMA");
        require(
                Arrays.equals(
                        ExportValidationSupport.canonicalBytes(input),
                        ExportValidationSupport.canonicalBytes(manifest.json(262144, budget))),
                "FAT_DAY_DIAGNOSTIC_INPUT_BINDING");
        final var request = exactPin(input.required("requestPin"));
        final var workbook = exactPin(input.required("workbookPin"));
        final var catalog = exactPin(input.required("catalogPin"));
        final var test = exactPin(input.required("consumerTestPin"));
        final var unit = exactPin(input.required("codeUnitPin"));
        final var sourceValues = input.required("sourcePins");
        final var sources = FaturasClienteDayPartitionAdapter.sources(sourceValues);
        final var pins = new ArrayList<>(List.of(manifest, request, workbook, catalog, test, unit));
        pins.addAll(sources);
        final var fullTest = test.json(1048576, budget);
        flags(
                fullTest,
                Set.of("success", "syntheticTestsPassed", "originalClosedKnown", "stateRestored"),
                true);
        require(unit.equals(exactPin(fullTest.required("unit"))), "FAT_DAY_CURRENT_CONSUMER_UNIT");
        unit.read(8388608, budget);
        final String day = text(input, "day");
        final boolean empty = sources.isEmpty();
        if (empty) {
            final var proof = exactPin(input.required("emptyDayProofPin"));
            final var closure = exactPin(input.required("emptyDayProofClosurePin"));
            pins.add(proof);
            pins.add(closure);
            requireEmptyProof(proof, closure, test, unit, day, budget);
        } else {
            require(
                    input.required("emptyDayProofPin").isNull()
                            && input.required("emptyDayProofClosurePin").isNull(),
                    "FAT_DAY_NONEMPTY_FORBIDS_EMPTY_PROOF");
        }
        final Path root = Path.of(text(input, "privateRoot"));
        require(
                root.isAbsolute()
                        && root.normalize().equals(root)
                        && Files.isDirectory(root)
                        && destination != null
                        && destination.isAbsolute()
                        && destination.normalize().equals(destination),
                "FAT_DAY_PRIVATE_ROOT_AND_OUTPUT");
        final Path receiptPath =
                destination.resolveSibling(destination.getFileName() + ".receipt.json");
        require(
                destination.startsWith(root)
                        && !destination.equals(root)
                        && Files.isDirectory(destination.getParent())
                        && !Files.exists(destination)
                        && !Files.exists(receiptPath),
                "FAT_DAY_FRESH_PRIVATE_OUTPUT");
        for (Path part = destination; part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "FAT_DAY_OUTPUT_LINK_REFUSED");
        }
        require(
                destination.getParent().toRealPath().startsWith(root.toRealPath()),
                "FAT_DAY_REAL_PRIVATE_ROOT");
        final var paths = new HashSet<Path>();
        for (final var pin : pins) {
            require(
                    paths.add(pin.path())
                            && !pin.path().equals(destination)
                            && !pin.path().equals(receiptPath),
                    "FAT_DAY_DISTINCT_INPUTS_OUTPUTS");
        }
        final var diagnostic =
                FaturasClienteDayPartitionAdapter.compare(
                        request.json(262144, budget),
                        sourceValues,
                        workbook,
                        catalog,
                        day,
                        empty,
                        budget);
        final String execution = UUID.randomUUID().toString();
        diagnostic.put("executionId", execution);
        for (final String name :
                List.of(
                        "requestPin",
                        "catalogPin",
                        "consumerTestPin",
                        "codeUnitPin",
                        "emptyDayProofPin",
                        "emptyDayProofClosurePin")) {
            diagnostic.set(name, input.required(name).deepCopy());
        }
        diagnostic.set("manifestPin", ExportValidationSupport.JSON.valueToTree(manifest));
        for (final var pin : pins) {
            pin.read(8388608, budget);
        }
        final byte[] diagnosticBytes = ExportValidationSupport.JSON.writeValueAsBytes(diagnostic);
        require(diagnosticBytes.length <= DIAGNOSTIC_BOUND, "FAT_DAY_DIAGNOSTIC_SIZE");
        writeFresh(destination, diagnosticBytes);
        final var diagnosticPin =
                new ExportValidationSupport.Pin(
                        destination,
                        ExportValidationSupport.sha256(diagnosticBytes),
                        diagnosticBytes.length);
        diagnosticPin.read(DIAGNOSTIC_BOUND, budget);
        final var receipt =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "FATURAS_CLIENTE_OFFLINE_DAY_DIAGNOSTIC_EXECUTED_RECEIPT_V1")
                        .put("profile", FaturasClienteIndependentRequest.PROFILE)
                        .put("executionId", execution)
                        .put("startedAt", started.toString())
                        .put("finishedAt", clock.instant().toString())
                        .put("pinsVerifiedBeforeAndAfter", true)
                        .put("periodComparisonComplete", false)
                        .put("periodAccepted", false)
                        .put("contentEqualUnderCandidatePresentation", false)
                        .put("catalogBindingPoliciesQualified", false)
                        .put("trustedSourceCompleteness", false)
                        .put("presentationPoliciesQualified", false)
                        .put("diagnosticOnly", true)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("productionAuthorized", false)
                        .put("mainResumeAllowed", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0);
        for (final String name :
                List.of(
                        "attemptId",
                        "periodStart",
                        "periodEnd",
                        "day",
                        "weekdayIndex",
                        "partitionPolicy",
                        "partitionCount",
                        "dayComparisonComplete",
                        "dayContentEqualUnderCandidatePresentation",
                        "status",
                        "sourceDayRows",
                        "nativeWholeWeekRows",
                        "selectedNativeDayRows",
                        "columns",
                        "totalColumns",
                        "catalogNature",
                        "emptyDaySourceProofVerified",
                        "nativeWholeWeekDayCounts",
                        "totalNumericCounts")) {
            receipt.set(name, diagnostic.required(name).deepCopy());
        }
        for (final String name :
                List.of(
                        "requestPin",
                        "catalogPin",
                        "consumerTestPin",
                        "codeUnitPin",
                        "sourcePins",
                        "workbookPin",
                        "emptyDayProofPin",
                        "emptyDayProofClosurePin")) {
            receipt.set(name, input.required(name).deepCopy());
        }
        receipt.set("manifestPin", ExportValidationSupport.JSON.valueToTree(manifest));
        receipt.set("diagnosticPin", ExportValidationSupport.JSON.valueToTree(diagnosticPin));
        for (final var pin : pins) {
            pin.read(8388608, budget);
        }
        final byte[] receiptBytes = ExportValidationSupport.JSON.writeValueAsBytes(receipt);
        require(receiptBytes.length <= 262144, "FAT_DAY_RECEIPT_SIZE");
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

    private static void requireEmptyProof(
            final ExportValidationSupport.Pin proofPin,
            final ExportValidationSupport.Pin closurePin,
            final ExportValidationSupport.Pin test,
            final ExportValidationSupport.Pin unit,
            final String day,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                proofPin.path().getParent().equals(closurePin.path().getParent())
                        && "parent-result.json".equals(closurePin.path().getFileName().toString()),
                "FAT_DAY_EMPTY_PROOF_CLOSURE_SAME_RUN");
        final var proof = proofPin.json(262144, budget);
        require(
                "FATURAS_CLIENTE_EMPTY_DAY_SOURCE0_QUALIFICATION_V1".equals(text(proof, "schema"))
                        && FaturasClienteIndependentRequest.PROFILE.equals(text(proof, "profile"))
                        && "FATURAS_POR_CLIENTE".equals(text(proof, "entity"))
                        && exactInt(proof.path("reportId"), 4924)
                        && day.equals(text(proof, "periodStart"))
                        && day.equals(text(proof, "periodEnd"))
                        && test.equals(exactPin(proof.required("consumerTestPin")))
                        && unit.equals(exactPin(proof.required("codeUnitPin"))),
                "FAT_DAY_EMPTY_PROOF_CONTEXT");
        flags(
                proof,
                Set.of(
                        "success",
                        "pinsVerifiedBeforeAndAfter",
                        "emptySourceDayQualified",
                        "actualTerminalEmptyArrayVerified",
                        "allOriginalResponseBodiesReadToEof",
                        "originalEmptyPayloadReadToEof",
                        "producerFlagsPreserved"),
                true);
        flags(
                proof,
                Set.of("historicalProducerFactsRewritten", "contentAccepted", "entityQualified"),
                false);
        for (final String name :
                Set.of(
                        "sourceRows",
                        "retainedBytes",
                        "currentMapperInvocations",
                        "rowTypesEvaluatedOnRows",
                        "Source",
                        "HTTP",
                        "SQL")) {
            require(exactInt(proof.path(name), 0), "FAT_DAY_EMPTY_PROOF_ZERO_ACTUAL_COUNTERS");
        }
        final var closure = closurePin.json(262144, budget);
        require(
                "ROOT_FRESH_PAIR_COMPARISON_CLOSURE0664_V1".equals(text(closure, "schema"))
                        && exactInt(closure.path("OriginalCalls"), 1),
                "FAT_DAY_EMPTY_PROOF_CLOSURE_SCHEMA");
        flags(
                closure,
                Set.of("success", "originalClosedKnown", "stateRestored", "OriginalJobStarted"),
                true);
        flags(closure, Set.of("slotRetained"), false);
        for (final String name : Set.of("Source", "HTTP", "SQL")) {
            require(exactInt(closure.path(name), 0), "FAT_DAY_EMPTY_PROOF_SOURCE0_CLOSURE");
        }
    }

    private static boolean exactInt(final JsonNode value, final int number) {
        return value.isIntegralNumber() && value.canConvertToInt() && value.intValue() == number;
    }

    private static void flags(
            final JsonNode object, final Set<String> names, final boolean expected) {
        for (final String name : names) {
            require(
                    object.path(name).isBoolean() && object.get(name).booleanValue() == expected,
                    "FAT_DAY_REQUIRED_LITERAL_FLAG");
        }
    }

    private static void fields(final JsonNode object, final Set<String> expected) {
        require(object != null && object.isObject(), "FAT_DAY_EXACT_INPUT_OBJECT");
        final var names = new HashSet<String>();
        object.fieldNames().forEachRemaining(names::add);
        require(names.equals(expected), "FAT_DAY_EXACT_INPUT_FIELDS");
    }

    private static ExportValidationSupport.Pin exactPin(final JsonNode value) {
        fields(value, Set.of("path", "sha256", "bytes"));
        return FaturasClienteRawMapperQualifier.pin(value);
    }

    private static void writeFresh(final Path path, final byte[] bytes) throws Exception {
        try (var channel =
                FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                require(channel.write(buffer) > 0, "FAT_DAY_WRITE_PROGRESS");
            }
            channel.force(true);
        }
    }
}
