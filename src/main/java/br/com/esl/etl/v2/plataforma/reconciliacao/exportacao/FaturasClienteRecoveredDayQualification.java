package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Offline retained-data proof only; it never converts a failed Source into a qualified capture. */
final class FaturasClienteRecoveredDayQualification {
    static final String INPUT_SCHEMA =
            "ROOT_FATURAS_CLIENTE_RAW_REQUALIFICATION_WITH_RECOVERED_DAY_INPUT_V1";

    private FaturasClienteRecoveredDayQualification() {}

    static ObjectNode qualify(
            final JsonNode entry,
            final ExportValidationSupport.Pin consumerTestPin,
            final ExportValidationSupport.Pin consumerUnitPin,
            final ExportValidationSupport.Pin catalogPin,
            final List<ExportValidationSupport.Pin> held,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        fields(
                entry,
                Set.of(
                        "captureKind",
                        "producerSummaryPin",
                        "captureReceiptPin",
                        "producerClosurePin",
                        "dataCompletenessReceiptPin",
                        "dataCompletenessClosurePin",
                        "requestPin",
                        "sourcePin"));
        require(
                "RECOVERED_RETAINED_DAY_V1".equals(text(entry, "captureKind"))
                        && entry.required("producerSummaryPin").isNull(),
                "FAT_RECOVERED_EXPLICIT_KIND_NO_FAKE_SUMMARY");
        final var capturePin = pin(entry, "captureReceiptPin", held);
        final var failedClosurePin = pin(entry, "producerClosurePin", held);
        final var proofPin = pin(entry, "dataCompletenessReceiptPin", held);
        final var proofClosurePin = pin(entry, "dataCompletenessClosurePin", held);
        final var requestPin = pin(entry, "requestPin", held);
        final var sourcePin = pin(entry, "sourcePin", held);
        final var capture = capturePin.json(262144, budget);
        final var failedClosure = failedClosurePin.json(262144, budget);
        final String actor = text(failedClosure, "executingRootUUID");
        require(UUID.fromString(actor).toString().equals(actor), "FAT_RECOVERED_ROOT_ACTOR");
        failedClosure(capturePin, failedClosurePin, failedClosure, actor);
        final var proof = proofPin.json(262144, budget);
        require(
                "FATURAS_CLIENTE_RETAINED_DAY_DATA_COMPLETENESS_SOURCE0_V1"
                                .equals(text(proof, "schema"))
                        && actor.equals(text(proof, "executingRootUUID")),
                "FAT_RECOVERED_COMPLETENESS_SCHEMA_ACTOR");
        require(
                "RECOVERED_RETAINED_DAY_V1".equals(text(proof, "captureKind"))
                        && proof.path("producerSummaryPin").isNull()
                        && FaturasClienteIndependentRequest.PROFILE.equals(text(proof, "profile"))
                        && "FATURAS_POR_CLIENTE".equals(text(proof, "entity"))
                        && "INDEPENDENT_RETAINED_DATA_INTEGRITY_ONLY_V1"
                                .equals(text(proof, "counterScope")),
                "FAT_RECOVERED_PROOF_CONTEXT");
        exact(proof, "reportId", 4924);
        sourceZeroClosure(proofPin, proofClosurePin, actor, budget);
        flags(
                proof,
                Set.of(
                        "success",
                        "dataCompletenessQualified",
                        "pinsVerifiedBeforeAndAfter",
                        "allOriginalResponseBodiesReadToEof",
                        "allOriginalResponseRecordBytesMatchRetainedRaw",
                        "actualTerminalArrayVerified"),
                Set.of(
                        "sourceCaptureQualified",
                        "producerDeadlineQualified",
                        "currentMapperQualificationPerformed",
                        "historicalProducerFactsRewritten",
                        "contentAccepted",
                        "entityQualified",
                        "mainResumeAllowed",
                        "partialRecordsSelected",
                        "bytesRenamedOrRewritten",
                        "deduplicationPerformed"));
        exact(proof, "Source", 0);
        exact(proof, "HTTP", 0);
        exact(proof, "SQL", 0);
        exact(proof, "currentMapperInvocations", 0);
        same(proof, "producerCaptureReceiptPin", capturePin);
        same(proof, "producerClosurePin", failedClosurePin);
        same(proof, "requestPin", requestPin);
        same(proof, "sourcePin", sourcePin);
        final var inputPin = pin(proof, "inputPin", held);
        final var proofInput = inputPin.json(262144, budget);
        fields(
                proofInput,
                Set.of("schema", "audit59InputPin", "audit59ReceiptPin", "audit59ClosurePin"));
        require(
                "ROOT_FATURAS_CLIENTE_RETAINED_DAY_DATA_COMPLETENESS69_INPUT_V1"
                        .equals(text(proofInput, "schema")),
                "FAT_RECOVERED_PROOF_INPUT_SCHEMA");
        final var auditInputPin = pin(proof, "audit59InputPin", held);
        final var auditPin = pin(proof, "audit59ReceiptPin", held);
        final var auditClosurePin = pin(proof, "audit59ClosurePin", held);
        same(proofInput, "audit59InputPin", auditInputPin);
        same(proofInput, "audit59ReceiptPin", auditPin);
        same(proofInput, "audit59ClosurePin", auditClosurePin);
        sourceZeroClosure(auditPin, auditClosurePin, actor, budget);
        final var audit = auditPin.json(262144, budget);
        require(
                "ROOT_FATURAS_CLIENTE_TERMINAL_FAILED_WIRE59_PHYSICAL_OBSERVATION_V1"
                        .equals(text(audit, "schema")),
                "FAT_RECOVERED_ACTUAL_PHYSICAL_AUDIT");
        flags(
                audit,
                Set.of(
                        "success",
                        "physicalOutcomeKnown",
                        "allStartedOperationsClosedKnown",
                        "originalResponsePinSetEqualsPhysicalBodies",
                        "completeWireDirectoryEnumeration",
                        "noUnmatchedWireDirectory",
                        "sharedUnknownLatchAbsent",
                        "shared429LatchAbsent",
                        "actualTerminalEmptyArrayVerified",
                        "allOriginalResponseBodiesReadToEof",
                        "allRetainedOriginalBytesReadToEof",
                        "retainedOriginalBytesMatchEveryResponseRecord",
                        "infoFields52Filters18Verified",
                        "serviceAtVerifiedForEveryRetainedRow",
                        "pinsVerifiedBeforeAndAfter",
                        "captureDeadlinePartialRemainsRejected",
                        "lastResponseWithinDeclaredSourceBudget"),
                Set.of(
                        "historicalProducerFactsRewritten",
                        "historicalProducerCaptureComplete",
                        "historicalProducerPayloadPromoted",
                        "historicalProducerWireOutcomeUnknown",
                        "sourceCaptureQualified",
                        "producerDeadlineQualified"));
        same(audit, "inputPin", auditInputPin);
        same(audit, "producerCaptureReceiptPin", capturePin);
        same(audit, "producerClosurePin", failedClosurePin);
        same(audit, "requestPin", requestPin);
        same(audit, "retainedSourcePin", sourcePin);
        final var auditInput = auditInputPin.json(262144, budget);
        require(
                "ROOT_FATURAS_CLIENTE_TERMINAL_FAILED_WIRE59_INPUT_V1"
                        .equals(text(auditInput, "schema")),
                "FAT_RECOVERED_AUDIT_INPUT_SCHEMA");
        same(auditInput, "captureReceiptPin", capturePin);
        same(auditInput, "closurePin", failedClosurePin);
        final var workerPin = pin(proof, "producerWorkerPin", held);
        final var sourceResultPin = pin(proof, "sourceResultPin", held);
        final var producerTestPin = pin(proof, "producerTestPin", held);
        final var producerUnitPin = pin(proof, "producerCodeUnitPin", held);
        same(audit, "producerWorkerPin", workerPin);
        same(audit, "sourceResultPin", sourceResultPin);
        same(auditInput, "workerPin", workerPin);
        same(auditInput, "producerTestPin", producerTestPin);
        final var executionPin = pin(auditInput, "executionInputPin", held);
        same(proof, "executionInputPin", executionPin);
        final var execution = executionPin.json(262144, budget);
        same(capture, "executionInputPin", executionPin);
        same(execution, "requestPin", requestPin);
        same(capture, "requestPin", requestPin);
        same(capture, "codeUnitPin", producerUnitPin);
        same(execution, "codeUnitPin", producerUnitPin);
        require(
                "ROOT_FATURAS_CLIENTE_SOURCE_EXECUTION_INPUT_V1".equals(text(execution, "schema"))
                        && sourceResultPin.path().toString().equals(text(execution, "resultPath")),
                "FAT_RECOVERED_EXECUTED_SOURCE_RESULT_PATH");
        final var contextPin = pin(execution, "physicalContextPin", held);
        final var context = contextPin.json(262144, budget);
        require(
                actor.equals(text(context, "executingRootUUID")),
                "FAT_RECOVERED_EXECUTED_CONTEXT_ACTOR");
        final var privateRoot = Path.of(text(context, "privateRoot"));
        require(
                privateRoot.isAbsolute()
                        && privateRoot.normalize().equals(privateRoot)
                        && sourcePin.path().startsWith(privateRoot)
                        && sourceResultPin.path().startsWith(privateRoot),
                "FAT_RECOVERED_ACTUAL_PRIVATE_ROOT");
        final var configurationPin = pin(proof, "producerConfigurationPin", held);
        final var driverPin = pin(proof, "producerDriverPin", held);
        same(auditInput, "configurationPin", configurationPin);
        same(auditInput, "driverPin", driverPin);
        final var configuration = configurationPin.json(2097152, budget);
        final var driver = driverPin.json(262144, budget);
        same(configuration, "executionInput", executionPin);
        same(configuration, "physicalContext", contextPin);
        same(configuration, "request", requestPin);
        same(configuration, "test", producerTestPin);
        same(driver, "testPin", producerTestPin);
        same(driver, "codeUnitPin", producerUnitPin);
        flags(driver, Set.of(), Set.of("success"));
        require(
                capture.equals(sourceResultPin.json(262144, budget)),
                "FAT_RECOVERED_PUBLIC_PRIVATE_CAPTURE_FACTS_IDENTICAL");
        final var request = requestPin.json(262144, budget);
        FaturasClienteIndependentRequest.validate(request);
        require(
                text(request, "periodStart").equals(text(request, "periodEnd"))
                        && text(proof, "periodStart").equals(text(request, "periodStart"))
                        && text(proof, "periodEnd").equals(text(request, "periodEnd"))
                        && text(proof, "day").equals(text(request, "periodStart"))
                        && text(capture, "attemptId").equals(text(request, "attemptId"))
                        && text(capture, "requestSha256")
                                .equals(
                                        ExportValidationSupport.sha256(
                                                ExportValidationSupport.canonicalBytes(request))),
                "FAT_RECOVERED_EXACT_ORIGINAL_ONE_DAY_REQUEST");
        require(
                FaturasClienteIndependentRequest.PROFILE.equals(text(capture, "profile"))
                        && "FATURAS_CLIENTE_INTERVAL_CAPTURE_V1".equals(text(capture, "schema"))
                        && "PARTIAL_FATURAS_CLIENTE_CAPTURE_DEADLINE_PARTIAL"
                                .equals(text(capture, "status"))
                        && FaturasClienteObservedWire52.SHA256.equals(
                                text(capture, "rowTypesContractSha256")),
                "FAT_RECOVERED_ONLY_KNOWN_TERMINAL_DEADLINE_PARTIAL");
        flags(
                capture,
                Set.of(
                        "terminalEmptyPageObserved",
                        "rawRecordLexemesPreserved",
                        "serviceAtScopeEnforced",
                        "serviceAtVerifiedForRetainedRows",
                        "serviceAtOriginalPreserved"),
                Set.of(
                        "captureComplete",
                        "payloadPromoted",
                        "wireOutcomeUnknown",
                        "emptyInterval",
                        "contentQualified",
                        "entityQualified",
                        "deduplicationPerformed",
                        "mapperQualified",
                        "rowTypesQualified",
                        "updatedAtScopeEnforced"));
        require(
                FaturasClienteIndependentRequest.FILTER_SCOPE_POLICY.equals(
                                text(capture, "filterScopePolicy"))
                        && capture.path("updatedAtFilter").isTextual()
                        && capture.get("updatedAtFilter").textValue().isEmpty()
                        && "SOURCE_OFFSET_CIVIL_DATE_V1"
                                .equals(text(capture, "serviceAtScopePolicy"))
                        && "ACTUAL_EMPTY_DATA_ARRAY_ONLY_V1"
                                .equals(text(capture, "terminalPolicy")),
                "FAT_RECOVERED_SOURCE_SCOPE_NOT_RELAXED");
        final var began = Instant.parse(text(capture, "startedAt"));
        final var finished = Instant.parse(text(capture, "finishedAt"));
        require(
                finished.isAfter(began.plusSeconds(210)),
                "FAT_RECOVERED_DEADLINE_FAILURE_REMAINS_REAL");
        same(capture, "partialDataPin", sourcePin);
        require(
                !capture.hasNonNull("dataPin")
                        && sourcePin.path().toString().equals(text(capture, "retainedPayloadPath"))
                        && sourcePin.bytes() == number(capture, "payloadBytes")
                        && sourcePin.sha256().equals(text(capture, "payloadSha256")),
                "FAT_RECOVERED_ORIGINAL_PARTIAL_NO_RENAME");
        final var responses = capture.required("responses");
        require(
                responses.isArray() && responses.size() > 1 && responses.size() <= 17,
                "FAT_RECOVERED_ORIGINAL_RESPONSE_BOUND");
        final int http = responses.size();
        for (final String key :
                Set.of("HTTP", "httpAdmitted", "httpRequestsReserved", "responsesObserved")) {
            exact(capture, key, http);
        }
        exact(capture, "pages", http - 1);
        exact(audit, "physicalHttpStarted", http);
        exact(audit, "physicalHttpCompleted", http);
        exact(audit, "declinedBeforeCurlStart", 0);
        exact(audit, "wireOperationDirectoryCount", http);
        Instant prior = began;
        for (final var response : responses) {
            flags(response, Set.of("rawBodyBytesVerified"), Set.of());
            final var completed = Instant.parse(text(response, "completedAt"));
            require(
                    !completed.isBefore(prior) && !completed.isAfter(finished),
                    "FAT_RECOVERED_LITERAL_RESPONSE_CLOCK_ORDER");
            prior = completed;
        }
        require(
                !prior.isAfter(began.plusSeconds(210)),
                "FAT_RECOVERED_LAST_RESPONSE_STILL_WITHIN_SOURCE_WINDOW");
        historicalFlags(proof, capture);
        require(
                text(proof, "historicalProducerStatus").equals(text(capture, "status"))
                        && text(proof, "historicalParentFailureCode")
                                .equals(text(failedClosure.required("failure"), "message")),
                "FAT_RECOVERED_FAILED_STATUS_AND_PARENT_CODE_PRESERVED");
        final var worker = workerPin.json(262144, budget);
        require(
                workerPin.path().getParent().equals(capturePin.path().getParent())
                        && actor.equals(text(worker, "executingRootUUID")),
                "FAT_RECOVERED_WORKER_ACTOR_RUN");
        flags(worker, Set.of("allChildrenClosedKnown"), Set.of());
        require(
                worker.path("processes").isArray() && worker.get("processes").size() == 1,
                "FAT_RECOVERED_ONE_CLOSED_FAILED_SOURCE_CHILD");
        final var child = worker.get("processes").get(0);
        flags(child, Set.of("closedKnown", "stdinClosed", "pipesEof"), Set.of());
        exact(child, "exit", 3);
        final var producerTest = producerTestPin.json(1048576, budget);
        flags(
                producerTest,
                Set.of("success", "syntheticTestsPassed", "originalClosedKnown", "stateRestored"),
                Set.of());
        require(
                "TEST".equals(text(producerTest, "mode")),
                "FAT_RECOVERED_PRODUCER_CLOSED_FULL_TEST");
        same(producerTest, "unit", producerUnitPin);
        final var producerUnit = producerUnitPin.json(8388608, budget);
        require(
                number(producerTest, "syntheticTestCount") > 0
                        && number(producerTest, "syntheticTestCount")
                                == number(producerUnit.required("counts"), "methods"),
                "FAT_RECOVERED_PRODUCER_FULL_UNIT_LEDGER");
        final long originalRows =
                FaturasClienteRawMapperQualifier.verifyOriginalResponses(
                        responses, sourcePin, held, budget);
        final var current = FaturasClienteRawMapperQualifier.qualify(requestPin, sourcePin, budget);
        require(
                originalRows == number(capture, "rows")
                        && originalRows == number(proof, "rows")
                        && originalRows == number(audit, "independentlyCountedRows")
                        && originalRows == number(current, "rows")
                        && sourcePin.bytes() == number(proof, "retainedBytes"),
                "FAT_RECOVERED_RECOUNT_ALL_ORIGINAL_OCCURRENCES");
        current.put("captureKind", "RECOVERED_RETAINED_DAY_V1")
                .put("producerFailed", true)
                .put("sourceCaptureQualified", false)
                .put("producerDeadlineQualified", false)
                .put("producerCaptureComplete", false)
                .put("producerPayloadPromoted", false)
                .put("producerMapperQualified", false)
                .put("producerRowTypesQualified", false)
                .put("sourceAcquisitionVerified", false)
                .put("historicalDataCompletenessVerified", true)
                .put("producerPhysicalClosureVerified", true)
                .put("allOriginalResponseRecordBytesMatchRetainedRaw", true)
                .put("actualTerminalArrayVerified", true)
                .put("allOriginalResponseBodiesReadToEof", true)
                .put("historicalProducerFactRewritten", false);
        current.putNull("producerSummaryPin");
        set(current, "captureReceiptPin", capturePin);
        set(current, "producerClosurePin", failedClosurePin);
        set(current, "dataCompletenessReceiptPin", proofPin);
        set(current, "dataCompletenessClosurePin", proofClosurePin);
        set(current, "producerTestPin", producerTestPin);
        set(current, "producerCodeUnitPin", producerUnitPin);
        set(current, "consumerTestPin", consumerTestPin);
        set(current, "codeUnitPin", consumerUnitPin);
        set(current, "catalogPin", catalogPin);
        set(current, "sourceResultPin", sourceResultPin);
        return current;
    }

    private static void historicalFlags(final JsonNode proof, final JsonNode capture) {
        for (final String name :
                Set.of(
                        "CaptureComplete",
                        "PayloadPromoted",
                        "WireOutcomeUnknown",
                        "RowTypesQualified",
                        "MapperQualified")) {
            final String captureName = Character.toLowerCase(name.charAt(0)) + name.substring(1);
            require(
                    proof.path("historicalProducer" + name).isBoolean()
                            && proof.get("historicalProducer" + name).booleanValue()
                                    == capture.get(captureName).booleanValue(),
                    "FAT_RECOVERED_LITERAL_HISTORICAL_FLAGS");
        }
    }

    private static void failedClosure(
            final ExportValidationSupport.Pin capture,
            final ExportValidationSupport.Pin closurePin,
            final JsonNode closure,
            final String actor) {
        require(
                capture.path().getParent().equals(closurePin.path().getParent())
                        && "parent-result.json".equals(closurePin.path().getFileName().toString())
                        && "ROOT_FATURAS_CLIENTE_FINITE_SOURCE_CLOSURE0753_V1"
                                .equals(text(closure, "schema"))
                        && actor.equals(text(closure, "executingRootUUID")),
                "FAT_RECOVERED_FAILED_CLOSURE_RUN");
        flags(
                closure,
                Set.of("originalClosedKnown", "stateRestored", "OriginalJobStarted"),
                Set.of("success", "slotRetained"));
        exact(closure, "OriginalCalls", 1);
        exact(closure, "SourceMaximum", 1);
        exact(closure, "HTTPMaximum", 17);
        exact(closure, "SQL", 0);
    }

    private static void sourceZeroClosure(
            final ExportValidationSupport.Pin receipt,
            final ExportValidationSupport.Pin closurePin,
            final String actor,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                receipt.path().getParent().equals(closurePin.path().getParent())
                        && "result.json".equals(receipt.path().getFileName().toString())
                        && "parent-result.json".equals(closurePin.path().getFileName().toString()),
                "FAT_RECOVERED_SOURCE0_CLOSURE_RUN");
        final var closure = closurePin.json(262144, budget);
        require(
                "ROOT_FRESH_PAIR_COMPARISON_CLOSURE0664_V1".equals(text(closure, "schema"))
                        && actor.equals(text(closure, "executingRootUUID")),
                "FAT_RECOVERED_SOURCE0_CLOSURE_SCHEMA_ACTOR");
        flags(
                closure,
                Set.of("success", "originalClosedKnown", "stateRestored", "OriginalJobStarted"),
                Set.of("slotRetained"));
        exact(closure, "OriginalCalls", 1);
        exact(closure, "Source", 0);
        exact(closure, "HTTP", 0);
        exact(closure, "SQL", 0);
    }

    private static ExportValidationSupport.Pin pin(
            final JsonNode node, final String name, final List<ExportValidationSupport.Pin> held) {
        final var pin = FaturasClienteRawMapperQualifier.pin(node.required(name));
        held.add(pin);
        return pin;
    }

    private static void same(
            final JsonNode node, final String name, final ExportValidationSupport.Pin pin) {
        require(
                pin.equals(FaturasClienteRawMapperQualifier.pin(node.required(name))),
                "FAT_RECOVERED_EXACT_PROVENANCE_PIN");
    }

    private static long number(final JsonNode node, final String name) {
        require(
                node.path(name).isIntegralNumber() && node.get(name).canConvertToLong(),
                "FAT_RECOVERED_TYPED_INTEGER");
        return node.get(name).longValue();
    }

    private static void exact(final JsonNode node, final String name, final long expected) {
        require(number(node, name) == expected, "FAT_RECOVERED_EXACT_PHYSICAL_COUNT");
    }

    private static void flags(final JsonNode node, final Set<String> yes, final Set<String> no) {
        for (final var name : yes) {
            require(
                    node.path(name).isBoolean() && node.get(name).booleanValue(),
                    "FAT_RECOVERED_TYPED_TRUE_FACT");
        }
        for (final var name : no) {
            require(
                    node.path(name).isBoolean() && !node.get(name).booleanValue(),
                    "FAT_RECOVERED_TYPED_FALSE_FACT");
        }
    }

    private static void fields(final JsonNode value, final Set<String> wanted) {
        require(value.isObject(), "FAT_RECOVERED_OBJECT");
        final var names = new HashSet<String>();
        value.fieldNames().forEachRemaining(names::add);
        require(names.equals(wanted), "FAT_RECOVERED_EXACT_ENTRY_FIELDS");
    }

    private static void set(
            final ObjectNode node, final String name, final ExportValidationSupport.Pin pin) {
        node.set(name, ExportValidationSupport.JSON.valueToTree(pin));
    }
}
