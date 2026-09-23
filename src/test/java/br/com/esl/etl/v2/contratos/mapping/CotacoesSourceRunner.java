package br.com.esl.etl.v2.contratos.mapping;

import br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationReportSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.function.LongSupplier;

/** One serial, single-use sample. Network transport is separate from the mapper consumer. */
final class CotacoesSourceRunner {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Transport transport;
    private final Clock clock;
    private final LongSupplier nanos;
    private final Sleeper sleeper;
    private final boolean synthetic;

    CotacoesSourceRunner(
            final Transport transport,
            final Clock clock,
            final LongSupplier nanos,
            final Sleeper sleeper,
            final boolean synthetic) {
        this.transport = transport;
        this.clock = clock;
        this.nanos = nanos;
        this.sleeper = sleeper;
        this.synthetic = synthetic;
    }

    ObjectNode run(final CotacoesSourceInput input, final Path destination) throws IOException {
        input.requireValidAt(clock.instant());
        final Path directory = destination.toAbsolutePath().normalize();
        for (Path current = directory; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                final var attributes =
                        Files.readAttributes(
                                current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                CotacoesSourceInput.require(
                        !attributes.isSymbolicLink() && !attributes.isOther(), "REPARSE_REJECTED");
            }
        }
        // An existing directory blocks another budget even if executionId or the input changes.
        Files.createDirectory(directory);
        final ObjectNode receipt = JSON.createObjectNode();
        receipt.put("evidence", synthetic ? "SYNTHETIC_TRANSPORT" : "CONTROLLED_SOURCE_SAMPLE");
        receipt.put("template", 6906);
        receipt.put("oracleArtifactSha256", input.oracleHash());
        receipt.put("status", "INCOMPLETE");
        receipt.put("coverageOrSnapshotProven", false);
        receipt.put("canonicalAcceptance", false);
        final var comparisons = receipt.putArray("comparisons");
        final var budget = new Budget(input, directory);
        write(directory.resolve("reservation.json"), receipt);
        try {
            final var metadata = CotacoesSourceInput.json(budget.get(input.metadataUri()));
            if (!input.metadataMatches(metadata)) {
                throw new Stop("METADATA_DIVERGED");
            }
            receipt.put("metadata", "MATCH");
            for (int page = 1; page <= input.pages(); page++) {
                final int expectedPage = page;
                final byte[] bytes = budget.get(input.dataUri(page));
                final var envelope = CotacoesSourceInput.json(bytes);
                if (!envelope.isObject()
                        || !envelope.path("data").isArray()
                        || envelope.has("error")
                        || envelope.has("errors")) {
                    throw new Stop("INVALID_DATA_ENVELOPE");
                }
                // Provider per semantics are unproven: use a stricter physical-row cap here.
                if (envelope.path("data").size() > 3) {
                    throw new Stop("PHYSICAL_ROW_LIMIT");
                }
                final var result =
                        MapperCharacterization.compare(
                                MapperProjection.Entity.COTACOES,
                                1,
                                ignored -> new ByteArrayInputStream(bytes),
                                ignored -> input.expected(expectedPage));
                final var report = result.sanitized();
                report.put(
                        "evidence",
                        synthetic
                                ? "SYNTHETIC_LOCAL_PARSER_MAPPER"
                                : "SOURCE_SAMPLE_PARSER_MAPPER");
                report.put("providerEvidence", synthetic ? "NOT_EXECUTED" : "BOUNDED_OBSERVATION");
                report.put("samplePage", page);
                comparisons.add(report);
                if (!result.matches()) {
                    throw new Stop("MAPPING_DIVERGED_OR_INCOMPLETE");
                }
            }
            budget.checkTime();
            receipt.put("status", "BOUNDED_SAMPLE_MATCH");
            receipt.put("reason", "PLANNED_SAMPLE_ONLY_NOT_COMPLETE_SET");
        } catch (final Stop stop) {
            receipt.put("reason", stop.reason);
        } catch (final IllegalArgumentException error) {
            receipt.put("reason", "INVALID_JSON_INPUT_OR_STRUCTURE");
        } catch (final IOException error) {
            receipt.put("reason", "LOCAL_IO_FAILURE_RECONCILE_RESERVATIONS");
        } finally {
            receipt.put("requestsReserved", budget.requests);
            receipt.put("bytesObserved", budget.bytes);
            receipt.put(
                    "elapsedMilliseconds",
                    Math.max(0, (nanos.getAsLong() - budget.start) / 1_000_000));
            write(directory.resolve("receipt.json"), receipt);
        }
        return receipt;
    }

    private final class Budget {
        private final CotacoesSourceInput input;
        private final Path directory;
        private final long start = nanos.getAsLong();
        private long completed;
        private int requests;
        private int bytes;

        private Budget(final CotacoesSourceInput input, final Path directory) {
            this.input = input;
            this.directory = directory;
        }

        private void checkTime() throws Stop {
            if (nanos.getAsLong() - start >= 120_000_000_000L) {
                throw new Stop("ELAPSED_LIMIT");
            }
            try {
                input.requireValidAt(clock.instant());
            } catch (final IllegalArgumentException error) {
                throw new Stop("AUTHORIZATION_EXPIRED");
            }
        }

        private byte[] get(final URI uri) throws IOException, Stop {
            checkTime();
            if (requests >= 4) {
                throw new Stop("REQUEST_LIMIT");
            }
            if (requests > 0) {
                final long remaining = 2_000_000_000L - (nanos.getAsLong() - completed);
                if (remaining > 0) {
                    try {
                        sleeper.sleep((remaining + 999_999) / 1_000_000);
                    } catch (final InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new Stop("INTERRUPTED");
                    }
                }
            }
            checkTime();
            final int timeout =
                    (int)
                            Math.min(
                                    30,
                                    (120_000_000_000L - (nanos.getAsLong() - start))
                                            / 1_000_000_000L);
            if (timeout < 1) {
                throw new Stop("ELAPSED_LIMIT");
            }
            requests++;
            final var reserved = JSON.createObjectNode();
            reserved.put("requestOrdinal", requests);
            reserved.put("state", "RESERVED_OUTCOME_UNKNOWN");
            write(directory.resolve("request-" + requests + "-reserved.json"), reserved);
            final Response response;
            try {
                response = transport.get(uri, timeout);
            } catch (final IOException | RuntimeException error) {
                throw new Stop("UNKNOWN_REQUEST_RESULT_NO_RETRY");
            }
            completed = nanos.getAsLong();
            final var observation = JSON.createObjectNode();
            observation.put("httpStatus", response.status);
            observation.put("bytes", response.body.length);
            observation.put("state", "OBSERVED");
            write(directory.resolve("request-" + requests + "-observed.json"), observation);
            checkTime();
            if (response.status < 200 || response.status > 299) {
                throw new Stop("HTTP_NON_2XX");
            }
            if (response.body.length > CotacoesSourceInput.RESPONSE_BYTES
                    || response.body.length > 262_144 - bytes) {
                throw new Stop("BYTE_LIMIT");
            }
            bytes += response.body.length;
            return response.body;
        }
    }

    private static void write(final Path path, final ObjectNode value) throws IOException {
        CharacterizationReportSanitizer.requireSanitized(value);
        Files.writeString(
                path,
                value.toString() + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }

    @FunctionalInterface
    interface Transport {
        Response get(URI uri, int timeoutSeconds) throws IOException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }

    static final class Response {
        private final int status;
        private final byte[] body;

        Response(final int status, final byte[] body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public String toString() {
            return "COTACOES_RESPONSE_REDACTED";
        }
    }

    private static final class Stop extends Exception {
        private static final long serialVersionUID = 1L;
        private final String reason;

        private Stop(final String reason) {
            this.reason = reason;
        }
    }
}
