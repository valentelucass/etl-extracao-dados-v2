package br.com.esl.etl.v2.contratos.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CotacoesSourceRunnerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");
    @TempDir Path temporary;

    @Test
    void crossesAcquisitionParserMapperAndIndependentOracleWithoutClaimingCompleteness()
            throws Exception {
        final var fixture = new Fixture();
        final var input = fixture.input();
        final var nanos = new AtomicLong();
        final List<String> queries = new ArrayList<>();
        final var runner =
                runner(
                        (uri, timeout) -> {
                            assertEquals(30, timeout);
                            queries.add(
                                    uri.getRawQuery() == null
                                            ? "INFO"
                                            : URLDecoder.decode(
                                                    uri.getRawQuery(), StandardCharsets.UTF_8));
                            return response(
                                    200,
                                    queries.size() == 1
                                            ? fixture.metadata.toString()
                                            : fixture.envelope);
                        },
                        nanos);
        final Path directory = temporary.resolve("sample");
        final var report = runner.run(input, directory);
        assertEquals("BOUNDED_SAMPLE_MATCH", report.path("status").textValue());
        assertEquals(2, report.path("requestsReserved").intValue());
        assertEquals(2_000_000_000L, nanos.get());
        assertTrue(queries.get(1).contains("search[quotes][requested_at]=2026-07-15 - 2026-07-15"));
        assertTrue(queries.get(1).contains("page=1&per=3&order_by=sequence_code asc"));
        assertFalse(report.path("coverageOrSnapshotProven").booleanValue());
        assertEquals(
                "NOT_EXECUTED",
                report.path("comparisons").get(0).path("providerEvidence").textValue());
        assertEquals(report.toString() + "\n", Files.readString(directory.resolve("receipt.json")));
        assertThrows(FileAlreadyExistsException.class, () -> runner.run(input, directory));
        fixture.config.put("executionId", "different-execution");
        assertThrows(
                FileAlreadyExistsException.class, () -> runner.run(fixture.input(), directory));
        assertEquals(2, queries.size());
    }

    @Test
    void stopsAtMetadataDivergenceBeforeAnyDataRequest() throws Exception {
        final var report =
                runResponses(new Fixture(), response(200, "{\"fields\":[],\"filters\":[]}"));
        assertEquals("METADATA_DIVERGED", report.path("reason").textValue());
        assertEquals(1, report.path("requestsReserved").intValue());
    }

    @Test
    void stopsOn429RedirectAndServerErrorWithoutRetry() throws Exception {
        for (final int status : List.of(429, 302, 500)) {
            final var fixture = new Fixture();
            final var report = runResponses(fixture, response(status, "PRIVATE_RESPONSE_SENTINEL"));
            assertEquals("HTTP_NON_2XX", report.path("reason").textValue());
            assertEquals(1, report.path("requestsReserved").intValue());
            assertFalse(report.toString().contains("PRIVATE_RESPONSE_SENTINEL"));
        }
    }

    @Test
    void reservesBeforeTransportAndKeepsUnknownOutcomeWithNoRetry() throws Exception {
        final var fixture = new Fixture();
        final Path directory = temporary.resolve("unknown");
        final var runner =
                runner(
                        (uri, timeout) -> {
                            assertTrue(Files.exists(directory.resolve("request-1-reserved.json")));
                            throw new IOException("PRIVATE_RESPONSE_SENTINEL");
                        },
                        new AtomicLong());
        final var report = runner.run(fixture.input(), directory);
        assertEquals("UNKNOWN_REQUEST_RESULT_NO_RETRY", report.path("reason").textValue());
        assertFalse(Files.exists(directory.resolve("request-1-observed.json")));
        assertFalse(report.toString().contains("PRIVATE_RESPONSE_SENTINEL"));
        assertThrows(
                FileAlreadyExistsException.class, () -> runner.run(fixture.input(), directory));
    }

    @Test
    void unexpectedQuarantineStopsBeforeAnotherPlannedPage() throws Exception {
        final var fixture = new Fixture();
        fixture.oracle.withArray("pages").add(fixture.expected.deepCopy());
        final var report =
                runResponses(
                        fixture,
                        response(200, fixture.metadata.toString()),
                        response(
                                200,
                                "{\"data\":[{\"sequence_code\":0,\"requested_at\":\"2026-07-15\"}]}"));
        assertEquals("MAPPING_DIVERGED_OR_INCOMPLETE", report.path("reason").textValue());
        assertEquals(2, report.path("requestsReserved").intValue());
        assertEquals("DIVERGED", report.path("comparisons").get(0).path("status").textValue());
    }

    @Test
    void enforcesResponseAndPhysicalRowCapsWithoutUsingPerAsCompletenessProof() throws Exception {
        final var fixture = new Fixture();
        final var bytes =
                runResponses(fixture, new CotacoesSourceRunner.Response(200, new byte[65_537]));
        assertEquals("BYTE_LIMIT", bytes.path("reason").textValue());
        final var rows =
                runResponses(
                        fixture,
                        response(200, fixture.metadata.toString()),
                        response(200, "{\"data\":[{},{},{},{}]}"));
        assertEquals("PHYSICAL_ROW_LIMIT", rows.path("reason").textValue());
    }

    @Test
    void malformedUtf8JsonAndErrorEnvelopesCannotPass() throws Exception {
        final var fixture = new Fixture();
        for (final var bad :
                List.of(
                        new byte[] {(byte) 0xc3, 0x28},
                        bytes("{"),
                        bytes("{\"error\":\"PRIVATE_SENTINEL\",\"data\":[]}"))) {
            final var report =
                    runResponses(
                            fixture,
                            response(200, fixture.metadata.toString()),
                            new CotacoesSourceRunner.Response(200, bad));
            assertEquals("INCOMPLETE", report.path("status").textValue());
            assertFalse(report.toString().contains("PRIVATE_SENTINEL"));
        }
    }

    @Test
    void elapsedBudgetAndAuthorizationExpiryStopBeforeAnotherCall() throws Exception {
        final var fixture = new Fixture();
        final var nanos = new AtomicLong();
        final var runner =
                runner(
                        (uri, timeout) -> {
                            nanos.set(120_000_000_000L);
                            return response(200, fixture.metadata.toString());
                        },
                        nanos);
        final var report = runner.run(fixture.input(), temporary.resolve("elapsed"));
        assertEquals("ELAPSED_LIMIT", report.path("reason").textValue());
        assertEquals(1, report.path("requestsReserved").intValue());
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.input().requireValidAt(NOW.plusSeconds(60)));
    }

    @Test
    void missingBindingsSentinelScopesAndNonAdoptedPlansFailBeforeTransport() throws Exception {
        for (final String key :
                List.of(
                        "logicalHost",
                        "sourceInstance",
                        "tenantScope",
                        "temporalGuaranteeReference",
                        "authorizationReference",
                        "credentialAttestationReference",
                        "representativenessReference")) {
            final var fixture = new Fixture();
            fixture.config.putNull(key);
            assertThrows(IllegalArgumentException.class, fixture::input);
        }
        for (final String value : List.of("GLOBAL", "DEFAULT", "SINGLETON")) {
            final var fixture = new Fixture();
            fixture.config.put("tenantScope", value);
            assertThrows(IllegalArgumentException.class, fixture::input);
        }
        final var fixture = new Fixture();
        fixture.config.put("budgetAdopted", false);
        assertThrows(IllegalArgumentException.class, fixture::input);
    }

    @Test
    void rejectsUnknownConfigFieldsTemplateMethodAndUnsafeHosts() throws Exception {
        for (final String host :
                List.of(
                        "http://example.invalid",
                        "https://user@example.invalid",
                        "https://example.invalid/?private=sentinel",
                        "https://example.invalid/#secret",
                        "https://example.invalid/path")) {
            final var fixture = new Fixture();
            fixture.config.put("baseUri", host);
            assertThrows(IllegalArgumentException.class, fixture::input);
        }
        for (final String key : List.of("retry", "template", "method")) {
            final var fixture = new Fixture();
            fixture.config.put(key, "UNAUTHORIZED");
            assertThrows(IllegalArgumentException.class, fixture::input);
        }
    }

    @Test
    void rejectsUnboundOracleMissingFieldCoverageEmptySampleAndExcessivePlan() throws Exception {
        final var fixture = new Fixture();
        fixture.config.put("oracleSha256", "0".repeat(64));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CotacoesSourceInput.validate(
                                fixture.config,
                                bytes(fixture.oracle.toString()),
                                fixture.decision,
                                NOW));
        ((ObjectNode) fixture.oracle.path("pages").get(0).get(0)).remove("/sequence_code/presence");
        assertThrows(IllegalArgumentException.class, fixture::input);
        fixture.oracle.withArray("pages").removeAll();
        assertThrows(IllegalArgumentException.class, fixture::input);
        for (int page = 0; page < 4; page++) {
            fixture.oracle.withArray("pages").add(fixture.expected);
        }
        assertThrows(IllegalArgumentException.class, fixture::input);
    }

    @Test
    void boundedPrivateInputReaderUsesOriginalOracleArtifactAndDecisionBinding() throws Exception {
        final var fixture = new Fixture();
        final Path oracle = temporary.resolve("oracle.json");
        Files.writeString(oracle, fixture.oracle.toString());
        fixture.config.put("oracleFile", oracle.toString());
        fixture.input();
        final Path config = temporary.resolve("input.json");
        Files.writeString(config, fixture.config.toString());
        assertEquals(
                fixture.input().oracleHash(), CotacoesSourceInput.read(config, NOW).oracleHash());
        Files.write(oracle, new byte[65_537]);
        assertThrows(IOException.class, () -> CotacoesSourceInput.read(config, NOW));
    }

    private ObjectNode runResponses(
            final Fixture fixture, final CotacoesSourceRunner.Response... responses)
            throws Exception {
        final var queue = new ArrayList<>(List.of(responses));
        final var runner =
                runner(
                        (uri, timeout) -> {
                            assertFalse(queue.isEmpty(), "Unexpected extra request");
                            return queue.remove(0);
                        },
                        new AtomicLong());
        return runner.run(
                fixture.input(), temporary.resolve("sample-" + java.util.UUID.randomUUID()));
    }

    private static CotacoesSourceRunner runner(
            final CotacoesSourceRunner.Transport transport, final AtomicLong nanos) {
        return new CotacoesSourceRunner(
                transport,
                Clock.fixed(NOW, ZoneOffset.UTC),
                nanos::get,
                milliseconds -> nanos.addAndGet(milliseconds * 1_000_000),
                true);
    }

    private static byte[] bytes(final String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static CotacoesSourceRunner.Response response(final int status, final String body) {
        return new CotacoesSourceRunner.Response(status, bytes(body));
    }

    private static final class Fixture {
        private final ObjectNode config = JSON.createObjectNode();
        private final ObjectNode oracle = JSON.createObjectNode();
        private final JsonNode expected;
        private final String envelope;
        private final JsonNode metadata =
                JSON.readTree(
                        "{\"fields\":[{\"name\":\"sequence_code\"}],\"filters\":[{\"name\":\"quotes.requested_at\"}]}");
        private final byte[] decision = Files.readAllBytes(Path.of(CotacoesSourceInput.DECISION));

        private Fixture() throws Exception {
            final JsonNode base;
            try (var stream =
                    getClass()
                            .getResourceAsStream(
                                    "/contracts/bloco57/cotacoes.current-v1.synthetic.json")) {
                base = JSON.readTree(stream).path("cases").get(0);
            }
            // Literal expectations already anchored in V2-027; never derive them from mapper
            // output.
            expected = base.path("expected").deepCopy();
            envelope = base.path("envelope").textValue();
            oracle.put("decisionSha256", CotacoesSourceInput.sha256(decision));
            oracle.set("metadata", metadata);
            oracle.putArray("pages").add(expected.deepCopy());
            config.put("version", 1).put("template", 6906).put("method", "GET_WITH_QUERY");
            config.put("baseUri", "https://example.invalid");
            for (final String key :
                    List.of(
                            "logicalHost",
                            "sourceInstance",
                            "tenantScope",
                            "authorizationReference",
                            "temporalGuaranteeReference",
                            "representativenessReference",
                            "credentialAttestationReference",
                            "executionId")) {
                config.put(key, "synthetic-" + key);
            }
            config.put("windowStart", "2026-07-15T03:00:00Z")
                    .put("windowEndExclusive", "2026-07-16T03:00:00Z");
            config.put("civilDateFrom", "2026-07-15").put("civilDateThrough", "2026-07-15");
            config.put("timezone", "America/Sao_Paulo");
            config.put("notBefore", NOW.minusSeconds(1).toString())
                    .put("notAfter", NOW.plusSeconds(60).toString());
            config.put("budgetAdopted", true)
                    .put("sourceExecutionEnabled", true)
                    .put("oracleFile", "private-oracle.json");
        }

        private CotacoesSourceInput input() {
            config.put("oracleSha256", CotacoesSourceInput.sha256(bytes(oracle.toString())));
            return CotacoesSourceInput.validate(config, bytes(oracle.toString()), decision, NOW);
        }
    }
}
