package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ExtrairColetasDataExport;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageDisposition;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded stdin replay only. No source client, SQL, promotion, payload output or real-data fixture.
 */
public final class ColetasSourceReplay {
    private static final int MAXIMUM_BYTES = 65_536;
    private static final Map<String, String> COMMON =
            Map.of(
                    "status",
                    "status",
                    "request_date",
                    "requestDate",
                    "service_date",
                    "serviceDate",
                    "finish_date",
                    "finishDate",
                    "cancellation_reason",
                    "cancellationReason");

    private ColetasSourceReplay() {}

    public static void main(final String[] args) {
        try {
            System.out.println(new ObjectMapper().writeValueAsString(replay(System.in)));
        } catch (final Exception failure) {
            // Parser/contract exception messages can contain source values; never serialize them.
            System.out.println(
                    "{\"accepted\":false,\"reason\":\"REPLAY_INPUT_OR_CONTRACT_REJECTED\"}");
            System.exit(2);
        }
    }

    static Map<String, Object> replay(final InputStream input) throws IOException {
        final var wire = new DataInputStream(input);
        if (wire.readInt() != 6201) {
            throw new IOException("REPLAY_PROTOCOL");
        }
        final String metadata = frame(wire);
        final int count = wire.readInt();
        if (count < 1 || count > 2) {
            throw new IOException("REPLAY_PAGE_COUNT");
        }
        final var pages = new ArrayList<String>();
        final var originals = new ArrayList<JsonNode>();
        final var pageKeys = new HashSet<String>();
        int repeatedAcrossPages = 0;
        boolean terminal = false;
        for (int i = 0; i < count; i++) {
            if (terminal) {
                throw new IOException("PAGE_AFTER_TERMINAL");
            }
            final String text = frame(wire);
            final var root = DataExportStrictJsonParser.readTree(text, 4096);
            if (!root.isArray() || originals.size() + root.size() > 1000) {
                throw new IOException("REPLAY_ROW_BOUND");
            }
            final Set<String> thisPage = new HashSet<>();
            for (final var row : root) {
                if (!row.isObject() || !row.path("id").isIntegralNumber()) {
                    throw new IOException("REPLAY_ID");
                }
                thisPage.add(row.path("id").asText());
                originals.add(row);
            }
            if (thisPage.size() > 2) {
                throw new IOException("REPLAY_PER");
            }
            for (final String key : thisPage) {
                if (!pageKeys.add(key)) {
                    repeatedAcrossPages++;
                }
            }
            terminal = root.isEmpty();
            pages.add(text);
        }
        final int graphCount = wire.readInt();
        if (graphCount < 0 || graphCount > 2) {
            throw new IOException("REPLAY_GRAPH_COUNT");
        }
        final var graphRows = new ArrayList<JsonNode>();
        final Set<String> graphIds = new HashSet<>();
        final Set<String> cursors = new HashSet<>();
        boolean graphTerminal = false;
        for (int i = 0; i < graphCount; i++) {
            if (graphTerminal) {
                throw new IOException("GRAPH_PAGE_AFTER_TERMINAL");
            }
            final var graph = DataExportStrictJsonParser.readTree(frame(wire), 4096);
            final var connection = graph.path("data").path("pick");
            final var edges = connection.path("edges");
            final var info = connection.path("pageInfo");
            if (graph.has("errors")
                    || graph.has("error")
                    || !edges.isArray()
                    || edges.size() > 100
                    || !info.path("hasNextPage").isBoolean()
                    || !info.has("endCursor")) {
                throw new IOException("GRAPH_STRUCTURE");
            }
            for (final var edge : edges) {
                final var node = edge.path("node");
                final String key = scalarIdentity(node.path("id"));
                if (!node.isObject() || key == null || !graphIds.add(key)) {
                    throw new IOException("GRAPH_ID");
                }
                if (!node.has("sequenceCode") || !node.has("statusUpdatedAt")) {
                    throw new IOException("GRAPH_SELECTION");
                }
                for (final String field : COMMON.values()) {
                    if (!node.has(field)) {
                        throw new IOException("GRAPH_SELECTION");
                    }
                }
                graphRows.add(node);
            }
            graphTerminal = !info.path("hasNextPage").booleanValue();
            final var cursor = info.path("endCursor");
            if (!cursor.isNull() && !cursor.isTextual()) {
                throw new IOException("GRAPH_CURSOR_TYPE");
            }
            if (!graphTerminal
                    && (edges.isEmpty()
                            || !cursor.isTextual()
                            || cursor.asText().isBlank()
                            || !cursors.add(cursor.asText()))) {
                throw new IOException("GRAPH_CURSOR");
            }
        }
        if (wire.read() != -1) {
            throw new IOException("REPLAY_TRAILING_INPUT");
        }

        final var release = DataExportColetasContractCatalog.release();
        final var policy = ContractTestSupport.policy(release);
        final var fingerprint = new ImmutableFingerprint("b62-memory-capture", "b".repeat(64));
        final var limits = new ContractObservationLimits(16, 256, 4096);
        final var binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(), release, policy, fingerprint, limits);
        final var guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        ignored -> {});
        final var configuration =
                DataExportContractObservationConfiguration.forRelease(
                        DataExportTemplate.COLETAS,
                        release,
                        limits,
                        guard.responsePathBoundary(),
                        fingerprint);
        final var parsedInfo =
                new DataExportTemplateInfoParser()
                        .parse(DataExportStrictJsonParser.readTree(metadata, 4096));
        final var adapter = new DataExportContractAdapter(limits, guard.responsePathBoundary());
        final var gateways =
                DataExportContractGate.enforce(
                        DataExportHttpGatewayBundle.contractBound(
                                request -> {
                                    if (request.page() > pages.size()) {
                                        throw new CaptureLimit();
                                    }
                                    final var root =
                                            DataExportStrictJsonParserAccess.parse(
                                                    pages.get(request.page() - 1));
                                    final var normalized =
                                            new DataExportResponseNormalizer().normalize(root);
                                    DataExportPageEntityLimitValidator.validate(
                                            request, normalized);
                                    return normalized.withObservation(
                                            adapter.response(
                                                    root,
                                                    configuration.expectedResponseForm(),
                                                    configuration.approvedKeyPath()),
                                            limits,
                                            configuration.responsePathBoundary());
                                },
                                template ->
                                        new DataExportTemplateInfo(
                                                template,
                                                200,
                                                Optional.empty(),
                                                true,
                                                parsedInfo.fields(),
                                                parsedInfo.filters()),
                                configuration),
                        DataExportTemplate.COLETAS,
                        guard);
        gateways.templateInfoGateway().fetchInfo(DataExportTemplate.COLETAS);
        final var staged = new ArrayList<ColetaStageRecord>();
        boolean captureLimit = false;
        try {
            new ExtrairColetasDataExport(
                            new DataExportPageStreamer(
                                    gateways.dataGateway(),
                                    DataExportExtractionAudit.noop(),
                                    Clock.systemUTC()),
                            new ColetaDataExportRecordMapper(),
                            batch -> {
                                for (int i = 0; i < batch.size(); i++) {
                                    staged.add(batch.recordAt(i));
                                }
                            })
                    .execute(
                            guard,
                            new DataExportPageRequest(
                                    DataExportTemplate.COLETAS,
                                    new BusinessDateRange(
                                            LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 9)),
                                    Optional.empty(),
                                    1,
                                    2,
                                    DataExportTemplate.COLETAS.defaultOrderBy()),
                            new DataExportExtractionLimits(pages.size() + 1, 1000, 100),
                            CancellationToken.none());
        } catch (final CaptureLimit expected) {
            captureLimit = true;
        }
        if (captureLimit == terminal || staged.size() != originals.size()) {
            throw new IOException("REPLAY_TRAVERSAL");
        }
        int quarantine = 0;
        int preservationDifferences = 0;
        int unknownStatus = 0;
        int unavailableFreshness = 0;
        for (int i = 0; i < staged.size(); i++) {
            final var record = staged.get(i);
            if (record.disposition() != ColetaStageDisposition.VALID) {
                quarantine++;
                continue;
            }
            if (!originals
                    .get(i)
                    .equals(DataExportStrictJsonParser.readTree(record.payloadJson(), 4096))) {
                preservationDifferences++;
            }
            if (record.status().code() == null) {
                unknownStatus++;
            }
            if (record.freshnessAtUtc() == null) {
                unavailableFreshness++;
            }
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", quarantine == 0 && preservationDifferences == 0);
        result.put("layer", "SOURCE_BODY_REPLAY_IN_MEMORY_STAGING");
        result.put("contractVersion", release.contractVersion());
        result.put("contractFingerprint", release.contractFingerprint().sha256());
        result.put("dataPages", pages.size());
        result.put("physicalRows", originals.size());
        result.put("distinctRoots", pageKeys.size());
        result.put("repeatedRootsAcrossPages", repeatedAcrossPages);
        result.put("stagedRows", staged.size());
        result.put("quarantinedRows", quarantine);
        result.put("preservationDifferences", preservationDifferences);
        result.put("unknownStatusRows", unknownStatus);
        result.put("unavailableFreshnessRows", unavailableFreshness);
        result.put("dataTerminalObserved", terminal);
        result.put("captureLimitReached", captureLimit);
        result.put("graphPages", graphCount);
        result.put("graphRows", graphRows.size());
        result.put("graphTerminalObserved", graphCount > 0 && graphTerminal);
        result.put("comparison", compare(originals, graphRows));
        result.put("snapshotProven", false);
        result.put("representativeParityAccepted", false);
        result.put("operationalBindingValidated", false);
        result.put("sqlExecuted", false);
        return result;
    }

    private static Map<String, Object> compare(
            final List<JsonNode> rows, final List<JsonNode> nodes) throws IOException {
        final var byAlias = new HashMap<String, JsonNode>();
        for (final var node : nodes) {
            final String alias = scalarIdentity(node.path("sequenceCode"));
            if (alias == null || byAlias.putIfAbsent(alias, node) != null) {
                throw new IOException("GRAPH_ALIAS_AMBIGUOUS");
            }
        }
        int matched = 0;
        int missing = 0;
        int identityDifferences = 0;
        final Map<String, Integer> differences = new java.util.TreeMap<>();
        for (final String field : COMMON.keySet()) {
            differences.put(field, 0);
        }
        differences.put("status_updated_at", 0);
        for (final var row : rows) {
            final var node = byAlias.get(scalarIdentity(row.path("sequence_code")));
            if (node == null) {
                missing++;
                continue;
            }
            matched++;
            if (!java.util.Objects.equals(
                    scalarIdentity(row.path("id")), scalarIdentity(node.path("id")))) {
                identityDifferences++;
            }
            for (final var pair : COMMON.entrySet()) {
                if (!row.path(pair.getKey()).equals(node.path(pair.getValue()))) {
                    differences.merge(pair.getKey(), 1, Integer::sum);
                }
            }
            if (!row.path("status_updated_at").equals(node.path("statusUpdatedAt"))) {
                differences.merge("status_updated_at", 1, Integer::sum);
            }
        }
        return Map.of(
                "matchedPhysicalRows",
                matched,
                "rowsOutsideGraphSample",
                missing,
                "canonicalIdDifferences",
                identityDifferences,
                "fieldDifferences",
                differences,
                "globalSetEqualityProven",
                false);
    }

    private static String scalarIdentity(final JsonNode node) {
        if (!node.isTextual() && !node.isIntegralNumber()) {
            return null;
        }
        return node.asText().isBlank() ? null : node.asText();
    }

    private static String frame(final DataInputStream input) throws IOException {
        final int length = input.readInt();
        if (length < 1 || length > MAXIMUM_BYTES) {
            throw new IOException("REPLAY_BYTE_BOUND");
        }
        final byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("REPLAY_TRUNCATED_FRAME");
        }
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static final class CaptureLimit extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class DataExportStrictJsonParserAccess {
        private static JsonNode parse(final String text) {
            try {
                return DataExportStrictJsonParser.readTree(text, 4096);
            } catch (final IOException failure) {
                throw new IllegalArgumentException("REPLAY_JSON_REJECTED");
            }
        }
    }
}
