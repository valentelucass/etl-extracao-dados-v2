package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaGraphQlTemporalMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaTemporalReferenceStore.Qualification;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalIdentityBinding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalObservation;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlTraversalVerification;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JdbcSqlServerColetaTemporalGatewayTest {
    private static final UUID REFERENCE = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID DATA = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 9);
    private static final Instant CAPTURE = Instant.parse("2026-09-10T20:00:00.123456789Z");
    private static final CancellationToken CANCELLATION = CancellationToken.none();

    @Test
    void persistsExactNanosecondsAndIndependentRawPresenceWithoutRewriting6908() throws Exception {
        final var jdbc = new Jdbc();
        jdbc.gateway().stage(observation("\"2026-09-09T17:00:00.123456789-03:00\""), CANCELLATION);
        final var json = new ObjectMapper().readTree((String) jdbc.parameters.get(1));
        assertEquals("{call stg.usp_stage_coleta_temporal(?)}", jdbc.sql);
        assertEquals(123456789, json.get("nano").intValue());
        assertEquals(
                Instant.parse("2026-09-09T20:00:00Z").getEpochSecond(),
                json.get("epochSecond").longValue());
        assertEquals(
                "\"2026-09-09T17:00:00.123456789-03:00\"",
                json.get("statusUpdatedAtRawJson").textValue());
        assertEquals("VALUE", json.get("statusUpdatedAtPresence").textValue());
        assertEquals("STRING:17", json.get("sourceKey").textValue());
        assertEquals(CAPTURE.toString(), json.get("observedAt").textValue());
        assertEquals(20, jdbc.timeout);
        assertEquals(1, jdbc.commits);
        assertEquals(0, jdbc.rollbacks);
        assertEquals(2, jdbc.closes);
    }

    @ParameterizedTest
    @CsvSource({"null,NULL", "7,VALUE", "'\"\"',VALUE", "'\"not-an-instant\"',VALUE"})
    void storesInvalidAndNullValuesWithoutInventingAnInstant(
            final String raw, final String presence) throws Exception {
        final var jdbc = new Jdbc();
        jdbc.gateway().stage(observation(raw), CANCELLATION);
        final var json = new ObjectMapper().readTree((String) jdbc.parameters.get(1));
        assertTrue(json.get("epochSecond").isNull());
        assertTrue(json.get("nano").isNull());
        assertEquals(presence, json.get("statusUpdatedAtPresence").textValue());
    }

    @Test
    void bindsExactScopedKeysAndEvidenceThenReadsOnlySqlCounts() throws Exception {
        final var jdbc = new Jdbc();
        final var ref = observation("null");
        final var deIdentity =
                new ScopedSourceIdentity(
                        ref.identity().sourceInstance(),
                        ref.identity().tenantScope(),
                        ref.identity().entity(),
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.INTEGER, "INTEGER:17"));
        jdbc.gateway()
                .bind(
                        new ColetaTemporalIdentityBinding(
                                DATA,
                                REFERENCE,
                                deIdentity,
                                ref.identity(),
                                DATE,
                                new ImmutableFingerprint("synthetic-crosswalk-v1", "a".repeat(64))),
                        CANCELLATION);
        assertEquals("INTEGER:17", jdbc.parameters.get(5));
        assertEquals("STRING:17", jdbc.parameters.get(6));
        assertEquals("SYNTHETIC_TENANT", jdbc.parameters.get(4));
        assertEquals("synthetic-crosswalk-v1", jdbc.parameters.get(8));
        assertEquals(
                new Qualification(3, 2, 1), jdbc.gateway().qualify(DATA, REFERENCE, CANCELLATION));
        assertEquals("{call recon.usp_qualify_coleta_temporal(?,?)}", jdbc.sql);
        assertEquals(2, jdbc.commits);
    }

    @Test
    void completionCarriesLocalCountsAndRejectsOtherOperationsBeforeConnecting() {
        final var jdbc = new Jdbc();
        jdbc.gateway()
                .complete(result(GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE), CANCELLATION);
        assertEquals("{call stg.usp_complete_coleta_temporal(?,?,?)}", jdbc.sql);
        assertEquals(2, jdbc.parameters.get(2));
        assertEquals(3L, jdbc.parameters.get(3));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        jdbc.gateway()
                                .complete(
                                        result(GraphQlReadOperation.USERS_SNAPSHOT), CANCELLATION));
        assertEquals(1, jdbc.connections);
    }

    @ParameterizedTest
    @ValueSource(strings = {"execute", "commit", "prepare"})
    void sqlFailureRollsBackAndClosesWithoutSensitiveMessage(final String phase) throws Exception {
        final var jdbc = new Jdbc();
        jdbc.failureAt = phase;
        final var observation = observation("null");
        final var failure =
                assertThrows(
                        StagingPersistenceException.class,
                        () -> jdbc.gateway().stage(observation, CANCELLATION));
        assertFalse(failure.getMessage().contains("synthetic-private-driver-detail"));
        assertEquals(1, jdbc.rollbacks);
        assertTrue(jdbc.closes >= 1);
    }

    @Test
    void preservesRollbackFailureAsSuppressedAndRejectsCancellationBeforeCommit() throws Exception {
        final var jdbc = new Jdbc();
        jdbc.failureAt = "execute";
        jdbc.failRollback = true;
        final var ref = observation("null");
        final var failure =
                assertThrows(
                        StagingPersistenceException.class,
                        () -> jdbc.gateway().stage(ref, CANCELLATION));
        assertEquals(1, failure.getCause().getSuppressed().length);
        final var cancelled = new Jdbc();
        final var signal = new CancellationSignal();
        cancelled.onExecute = signal::cancel;
        assertThrows(
                ResilienceCancelledException.class, () -> cancelled.gateway().stage(ref, signal));
        assertEquals(0, cancelled.commits);
        assertEquals(1, cancelled.rollbacks);
        assertThrows(
                ResilienceCancelledException.class, () -> cancelled.gateway().stage(ref, signal));
        assertEquals(1, cancelled.connections);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "null", "negative", "equation"})
    void malformedSqlSummaryCannotCommit(final String mode) {
        final var jdbc = new Jdbc();
        jdbc.summaryMode = mode;
        assertThrows(
                RuntimeException.class,
                () -> jdbc.gateway().qualify(DATA, REFERENCE, CANCELLATION));
        assertEquals(0, jdbc.commits);
        assertEquals(1, jdbc.rollbacks);
    }

    @Test
    void rejectsForgedInstantAndContractBeforeOpeningConnection() throws Exception {
        final var jdbc = new Jdbc();
        final var ref = observation("\"2026-09-09T20:00:00Z\"");
        for (final boolean contract : new boolean[] {false, true}) {
            final var changed =
                    new ColetaTemporalObservation(
                            ref.executionId(),
                            ref.identity(),
                            ref.queryDate(),
                            contract ? "unapproved-v1" : ref.contractVersion(),
                            ref.selectionFingerprint(),
                            1,
                            1,
                            ref.observedAt(),
                            ref.status(),
                            ref.statusUpdatedAt(),
                            ref.requestDate(),
                            contract ? ref.statusAtUtc() : CAPTURE);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> jdbc.gateway().stage(changed, CANCELLATION));
        }
        assertEquals(0, jdbc.connections);
    }

    @ParameterizedTest
    @ValueSource(strings = {"7", "null", "\"done\"", "\"pending\" trailing"})
    void rejectsInconsistentRawFieldBeforePersistence(final String raw) throws Exception {
        final var jdbc = new Jdbc();
        final var ref = observation("null");
        final var forged =
                new ColetaTemporalObservation(
                        ref.executionId(),
                        ref.identity(),
                        ref.queryDate(),
                        ref.contractVersion(),
                        ref.selectionFingerprint(),
                        1,
                        1,
                        CAPTURE,
                        new ColetaTemporalObservation.Field(
                                br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence
                                        .VALUE,
                                raw,
                                "pending"),
                        ref.statusUpdatedAt(),
                        ref.requestDate(),
                        null);
        assertThrows(
                IllegalArgumentException.class, () -> jdbc.gateway().stage(forged, CANCELLATION));
        assertEquals(0, jdbc.connections);
    }

    private static ColetaTemporalObservation observation(final String timestamp) throws Exception {
        return new ColetaGraphQlTemporalMapper()
                .map(
                        REFERENCE,
                        "SYNTHETIC_SOURCE",
                        "SYNTHETIC_TENANT",
                        DATE,
                        1,
                        1,
                        CAPTURE,
                        new ObjectMapper()
                                .readTree(
                                        "{\"id\":\"17\",\"status\":\"pending\","
                                                + "\"requestDate\":\"2026-09-09\",\"statusUpdatedAt\":"
                                                + timestamp
                                                + "}"));
    }

    private static GraphQlExtractionResult result(final GraphQlReadOperation operation) {
        return new GraphQlExtractionResult(
                REFERENCE,
                operation,
                2,
                3,
                CAPTURE,
                CAPTURE,
                GraphQlTraversalVerification.LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED);
    }

    private static final class Jdbc {
        private final Map<Integer, Object> parameters = new HashMap<>();
        private int commits;
        private int rollbacks;
        private int closes;
        private int connections;
        private int timeout;
        private String sql;
        private String failureAt = "";
        private boolean failRollback;
        private String summaryMode = "valid";
        private Runnable onExecute = () -> {};

        private JdbcSqlServerColetaTemporalGateway gateway() {
            return new JdbcSqlServerColetaTemporalGateway(
                    proxy(
                            DataSource.class,
                            (object, method, args) -> {
                                if (method.getName().equals("getConnection")) {
                                    connections++;
                                    return connection();
                                }
                                throw new AssertionError(method.getName());
                            }));
        }

        private Connection connection() {
            return proxy(
                    Connection.class,
                    (object, method, args) -> {
                        switch (method.getName()) {
                            case "setAutoCommit":
                                assertEquals(false, args[0]);
                                return null;
                            case "prepareCall":
                                fail("prepare");
                                sql = (String) args[0];
                                return statement();
                            case "commit":
                                fail("commit");
                                commits++;
                                return null;
                            case "rollback":
                                rollbacks++;
                                if (failRollback) {
                                    throw new SQLException("rollback");
                                }
                                return null;
                            case "close":
                                closes++;
                                return null;
                            default:
                                throw new AssertionError(method.getName());
                        }
                    });
        }

        private CallableStatement statement() {
            return proxy(
                    CallableStatement.class,
                    (object, method, args) -> {
                        switch (method.getName()) {
                            case "setString", "setNString", "setInt", "setLong":
                                parameters.put((Integer) args[0], args[1]);
                                return null;
                            case "setQueryTimeout":
                                timeout = (Integer) args[0];
                                return null;
                            case "executeUpdate":
                                fail("execute");
                                onExecute.run();
                                return 1;
                            case "executeQuery":
                                fail("execute");
                                return resultSet();
                            case "close":
                                closes++;
                                return null;
                            default:
                                throw new AssertionError(method.getName());
                        }
                    });
        }

        private ResultSet resultSet() {
            final int[] row = {0};
            return proxy(
                    ResultSet.class,
                    (object, method, args) -> {
                        switch (method.getName()) {
                            case "next":
                                return ++row[0]
                                        <= (summaryMode.equals("missing")
                                                ? 0
                                                : summaryMode.equals("duplicate") ? 2 : 1);
                            case "getLong":
                                return switch ((String) args[0]) {
                                    case "considered_rows" ->
                                            summaryMode.equals("negative") ? -3L : 3L;
                                    case "candidate_rows" -> 2L;
                                    case "blocked_rows" -> summaryMode.equals("equation") ? 2L : 1L;
                                    default -> throw new AssertionError(args[0]);
                                };
                            case "wasNull":
                                return summaryMode.equals("null");
                            case "close":
                                closes++;
                                return null;
                            default:
                                throw new AssertionError(method.getName());
                        }
                    });
        }

        private void fail(final String phase) throws SQLException {
            if (failureAt.equals(phase)) {
                throw new SQLException("synthetic-private-driver-detail");
            }
        }
    }

    private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
