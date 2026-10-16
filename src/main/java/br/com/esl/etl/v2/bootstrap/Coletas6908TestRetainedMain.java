package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.EnvironmentSecretProvider;
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.LongSupplier;

/** Separate opt-in retained test entrypoint; Main and rollback SAMPLE remain unchanged. */
public final class Coletas6908TestRetainedMain {
    private Coletas6908TestRetainedMain() {}

    public static void main(final String[] arguments) {
        System.exit(
                run(
                        arguments,
                        System.getenv(),
                        System.getProperties(),
                        Clock.systemUTC(),
                        System.out));
    }

    static int run(
            final String[] arguments,
            final Map<String, String> environment,
            final Properties properties,
            final Clock clock,
            final PrintStream output) {
        final var receipt = new LinkedHashMap<String, Object>();
        int exit = 1;
        receipt.put("profile", "TEST_RETAINED_LOCAL_SQL_PIN_V1");
        receipt.put("status", "STOP_NO_RETRY");
        receipt.put("windowComplete", false);
        receipt.put("corePromotion", false);
        receipt.put("callsAttempted", 0);
        receipt.put("independentPostCommitReadback", false);
        try {
            if (arguments.length != 3
                    || !"--request".equals(arguments[1])
                    || !Set.of("--preflight", "--run-reserved").contains(arguments[0])) {
                throw new IllegalArgumentException("TEST_CLI_INVALID");
            }
            requireOptions(properties);
            final var request = Coletas6908TestRetainedRequest.read(Path.of(arguments[2]));
            final var snapshot = Map.copyOf(environment);
            final var configuration = Coletas6908TestRetainedSource.configuration(snapshot);
            final var provider = new EnvironmentSecretProvider(snapshot);
            provider.require(SecretKey.DATA_EXPORT_TOKEN);
            provider.require(SecretKey.SHADOW_SAMPLE_SA_PASSWORD);
            Coletas6908TestRetainedSource.binding(request, configuration);
            if (!Coletas6908TestRetainedRequest.DAY.isBefore(
                    java.time.LocalDate.now(
                            clock.withZone(configuration.settings().sourceZone())))) {
                throw new IllegalArgumentException("TEST_DAY_NOT_CLOSED");
            }
            if ("--preflight".equals(arguments[0])) {
                receipt.put("status", "TEST_RETAINED_PREFLIGHT_ONLY");
                receipt.put("sqlExecutions", 0);
                receipt.put("callsAttempted", 0);
                receipt.put("sourceConfigurationBound", true);
                exit = 0;
            } else {
                if (!"1".equals(snapshot.get("V2_TEST_RETAINED_RESERVED"))) {
                    throw new IllegalArgumentException("TEST_RESERVATION_REQUIRED");
                }
                final var deadline =
                        new Deadline(
                                snapshot.get("V2_TEST_RETAINED_DEADLINE_UTC"),
                                clock,
                                System::nanoTime);
                receipt.putAll(
                        Coletas6908TestRetainedRunner.execute(
                                request, configuration, provider, clock, deadline));
                exit =
                        "CAPTURE_COMMITTED_PARTIAL_AWAITING_INDEPENDENT_READBACK"
                                                .equals(receipt.get("status"))
                                        && Boolean.TRUE.equals(receipt.get("committed"))
                                        && Boolean.TRUE.equals(receipt.get("closeObserved"))
                                        && !Boolean.TRUE.equals(receipt.get("commitUncertain"))
                                ? 0
                                : 1;
            }
        } catch (final Exception failure) {
            receipt.put("failureType", failure.getClass().getSimpleName());
            receipt.put("failureCode", failureCode(failure));
            describeFailure(receipt, failure);
        }
        receipt.put("exitCode", exit);
        receipt.put("completedUtc", clock.instant().toString());
        try {
            output.println(new ObjectMapper().writeValueAsString(receipt));
        } catch (final com.fasterxml.jackson.core.JsonProcessingException failure) {
            output.println("{\"status\":\"SANITIZED_RECEIPT_FAILURE\",\"exitCode\":1}");
            return 1;
        }
        return exit;
    }

    static void requireOptions(final Properties values) {
        if (!"true".equals(values.getProperty("test.retained.local.enabled"))
                || !"true".equals(values.getProperty("test.retained.sql-pin.enabled"))) {
            throw new IllegalArgumentException("TEST_RETAINED_OPT_IN_REQUIRED");
        }
        if (!"1".equals(values.getProperty("jdk.httpclient.redirects.retrylimit"))
                || !"true".equals(values.getProperty("jdk.httpclient.disableRetryConnect"))
                || !"false".equals(values.getProperty("jdk.httpclient.enableAllMethodRetry"))) {
            throw new IllegalArgumentException("TEST_JVM_RETRY_SETTINGS_REQUIRED");
        }
        for (final String key :
                List.of(
                        "javax.net.ssl.trustStore",
                        "javax.net.ssl.trustStoreType",
                        "javax.net.ssl.trustStorePassword",
                        "javax.net.ssl.trustStoreProvider",
                        "javax.net.ssl.keyStore",
                        "javax.net.ssl.keyStorePassword",
                        "jdk.internal.httpclient.disableHostnameVerification")) {
            if (values.containsKey(key)) {
                throw new IllegalArgumentException("TEST_GLOBAL_TLS_OVERRIDE_REFUSED");
            }
        }
    }

    static String failureCode(final Throwable failure) {
        final Set<String> codes =
                Set.of(
                        "TEST_REQUEST_SCHEMA",
                        "TEST_REQUEST_STRING_REQUIRED",
                        "TEST_REQUEST_IDENTITIES",
                        "TEST_REQUEST_SCOPE_OR_LIMIT",
                        "TEST_REQUEST_LIMIT",
                        "TEST_SOURCE_HTTPS_REQUIRED",
                        "TEST_SOURCE_INFO_REFUSED",
                        "TEST_SOURCE_ROW_BOUND",
                        "TEST_SOURCE_OBSERVATION_REQUIRED",
                        "TEST_SOURCE_MAPPER_QUARANTINE",
                        "TEST_SOURCE_ATTEMPT_COUNT",
                        "TEST_HTTP_BUDGET",
                        "TEST_HTTP_TEMPLATE",
                        "TEST_HTTP_ORDER",
                        "TEST_HTTP_OPERATION",
                        "TEST_CAPTURE_NOT_RETAINABLE",
                        "TEST_CLI_INVALID",
                        "TEST_DAY_NOT_CLOSED",
                        "TEST_RESERVATION_REQUIRED",
                        "TEST_RETAINED_OPT_IN_REQUIRED",
                        "TEST_JVM_RETRY_SETTINGS_REQUIRED",
                        "TEST_GLOBAL_TLS_OVERRIDE_REFUSED",
                        "TEST_DEADLINE_INVALID",
                        "TEST_DEADLINE_EXPIRED");
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof Coletas6908TestRetainedSource.MappingFailure) {
                return "TEST_SOURCE_MAPPER_QUARANTINE";
            }
            if (current
                    instanceof
                    br.com.esl.etl.v2.plataforma.persistencia.controle.TestRetainedSqlSession
                                            .Failure
                                    bank) {
                return bank.code();
            }
            if ((current.getClass() == IllegalArgumentException.class
                            || current.getClass() == IllegalStateException.class)
                    && current.getMessage() != null
                    && codes.contains(current.getMessage())) {
                return current.getMessage();
            }
            if (current
                    instanceof br.com.esl.etl.v2.plataforma.contrato.ContractDriftException drift) {
                return "CONTRACT_" + drift.reason().name();
            }
        }
        return "SOURCE_OR_SQL_REFUSED";
    }

    static void describeFailure(final Map<String, Object> receipt, final Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof Coletas6908TestRetainedSource.SourceFailure source) {
                receipt.put("failureStage", source.stage);
            }
            if (current instanceof Coletas6908TestRetainedSource.MappingFailure mapping) {
                receipt.put("mapperFailureCode", mapping.reason);
            }
            if (current
                            instanceof
                            br.com.esl.etl.v2.plataforma.fonte.dataexport
                                                    .DataExportUnavailableException
                                            unavailable
                    && unavailable.httpStatus().isPresent()) {
                receipt.put("failureHttpStatus", unavailable.httpStatus().getAsInt());
            }
            if (current
                    instanceof
                    br.com.esl.etl.v2.plataforma.persistencia.controle.TestRetainedSaJdbcConnector
                                            .ConnectionFailure
                                    connection) {
                receipt.put("pinReason", connection.pinReason());
                receipt.put("tlsCauseObserved", connection.tlsCauseObserved());
            }
        }
    }

    static final class Deadline implements Runnable {
        private final Instant end;
        private final Clock clock;
        private final LongSupplier ticker;
        private final long started;
        private final long budget;

        Deadline(final String utc, final Clock clock, final LongSupplier ticker) {
            this.clock = clock;
            this.ticker = ticker;
            if (utc == null
                    || !utc.matches(
                            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z")) {
                throw new IllegalArgumentException("TEST_DEADLINE_INVALID");
            }
            end = Instant.parse(utc);
            final Duration remaining = Duration.between(clock.instant(), end);
            if (remaining.isNegative()
                    || remaining.isZero()
                    || remaining.compareTo(Duration.ofSeconds(60)) > 0) {
                throw new IllegalArgumentException("TEST_DEADLINE_INVALID");
            }
            budget = remaining.toNanos();
            started = ticker.getAsLong();
        }

        @Override
        public void run() {
            if (!clock.instant().isBefore(end) || ticker.getAsLong() - started >= budget) {
                throw new IllegalStateException("TEST_DEADLINE_EXPIRED");
            }
        }
    }
}
