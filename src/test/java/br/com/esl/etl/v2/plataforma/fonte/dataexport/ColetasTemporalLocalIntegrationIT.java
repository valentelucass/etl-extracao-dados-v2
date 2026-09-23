package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ColetasTemporalPhysicalFixture.data;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ColetasTemporalPhysicalFixture.graphPage;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ColetasTemporalPhysicalFixture.reference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.LocalColetasTemporalRuntime;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ExtrairColetasDataExport;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.PersistirReferenciasTemporaisColetas;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcColetaTemporalLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaTemporalGateway;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Physical opt-in: no DDL, Flyway, source network or commit. Every test restores aggregate counts.
 */
class ColetasTemporalLocalIntegrationIT {
    private static final String STAMP = "2036-01-20T10:00:00.123456789Z";
    private static final String EQUIVALENT = "2036-01-20T07:00:00.123456789-03:00";
    private static final String NEXT_NANO = "2036-01-20T10:00:00.123456790Z";

    @ParameterizedTest
    @ValueSource(
            strings = {
                "COMPLEMENT",
                "NATIVE",
                "NATIVE_OFFSET",
                "REFERENCE_EQUIVALENT",
                "EXPANSION",
                "NATIVE_NULL",
                "NATIVE_INVALID",
                "DONE",
                "FINISHED",
                "CANCELED"
            })
    void integratesContractedSourcesAuditExactDecisionTypedConsumptionAndReplay(final String mode)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(session);
            try {
                final String status =
                        switch (mode) {
                            case "DONE" -> "done";
                            case "FINISHED" -> "finished";
                            case "CANCELED" -> "canceled";
                            default -> "pending";
                        };
                final String nativeRaw =
                        switch (mode) {
                            case "NATIVE", "NATIVE_OFFSET" -> STAMP;
                            case "NATIVE_NULL" -> "null";
                            case "NATIVE_INVALID" -> "invalid";
                            default -> null;
                        };
                final String row = data(status, nativeRaw);
                final String page =
                        mode.equals("EXPANSION")
                                ? "["
                                        + row
                                        + ","
                                        + row.replace(
                                                "\"synthetic_fixture\":true",
                                                "\"synthetic_fixture\":true,\"pck_prn_name\":\"synthetic-detail\"")
                                        + "]"
                                : "[" + row + "]";
                final String graph =
                        mode.equals("REFERENCE_EQUIVALENT")
                                ? graphPage(
                                        false,
                                        reference(status, STAMP),
                                        reference(status, EQUIVALENT))
                                : graphPage(
                                        false,
                                        reference(
                                                status,
                                                mode.equals("NATIVE_OFFSET") ? EQUIVALENT : STAMP));
                final var fixture =
                        new ColetasTemporalPhysicalFixture(
                                session,
                                List.of(page, "[]"),
                                List.of(graph),
                                CancellationToken.none());
                final var result = fixture.run();
                assertEquals(1, result.inserted());
                assertEquals(1, result.considered());
                assertFalse(result.replay());
                assertEquals(
                        "123456789",
                        scalar(session, "SELECT nano FROM core.coleta_temporal_laboratory"));
                assertEquals(
                        Long.toString(Instant.parse(STAMP).getEpochSecond()),
                        scalar(
                                session,
                                "SELECT epoch_second FROM core.coleta_temporal_laboratory"));
                assertEquals(
                        status,
                        scalar(session, "SELECT status_code FROM core.coleta_temporal_laboratory"));
                assertEquals(
                        "COMPLETED", scalar(session, "SELECT status FROM ctl.execution_audit"));
                assertEquals(
                        "0",
                        scalar(
                                session,
                                "SELECT CONVERT(int,MAX(CONVERT(int,promotion_authorized))) FROM recon.vw_coleta_temporal_decision_v2"));
                final var replay =
                        new JdbcColetaTemporalLaboratory(session, true)
                                .consume(
                                        fixture.binding.executionId(),
                                        fixture.graph.guard().executionContext().executionId(),
                                        CancellationToken.none());
                assertTrue(replay.replay());
                assertEquals(1, replay.noop());
                assertThrows(ContractDriftException.class, fixture.graph.guard()::complete);
                assertTrue(session.suppressedCommits() >= 4);
                assertTrue(session.releasedConnections() >= 8);
            } finally {
                session.rollback();
            }
            assertEquals(before, counts(session));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "REFERENCE_ABSENT",
                "REFERENCE_NULL",
                "REFERENCE_INVALID",
                "REFERENCE_NANO_CONFLICT",
                "NATIVE_NANO_CONFLICT",
                "STATUS_MISMATCH",
                "WINDOW_MISMATCH",
                "ROOT_CONFLICT",
                "BINDING_ABSENT",
                "IDENTITY_MISMATCH"
            })
    void refusesDiscriminatingConflictsWithoutPromoting(final String mode) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(session);
            try {
                String nativeRaw = mode.equals("NATIVE_NANO_CONFLICT") ? STAMP : null;
                String graphRaw =
                        switch (mode) {
                            case "REFERENCE_ABSENT" -> null;
                            case "REFERENCE_NULL" -> "null";
                            case "REFERENCE_INVALID" -> "invalid";
                            case "NATIVE_NANO_CONFLICT" -> NEXT_NANO;
                            default -> STAMP;
                        };
                String graphNode =
                        reference(mode.equals("STATUS_MISMATCH") ? "done" : "pending", graphRaw);
                if (mode.equals("WINDOW_MISMATCH")) {
                    graphNode = graphNode.replace("2036-01-20\"", "2036-01-19\"");
                }
                final String graph =
                        mode.equals("REFERENCE_NANO_CONFLICT")
                                ? graphPage(false, graphNode, reference("pending", NEXT_NANO))
                                : graphPage(false, graphNode);
                final String page =
                        mode.equals("ROOT_CONFLICT")
                                ? "[" + data("pending", null) + "," + data("done", null) + "]"
                                : "[" + data("pending", nativeRaw) + "]";
                final var fixture =
                        new ColetasTemporalPhysicalFixture(
                                session,
                                List.of(page, "[]"),
                                List.of(graph),
                                CancellationToken.none());
                final var temporal = capture(fixture);
                if (!mode.equals("BINDING_ABSENT")) {
                    temporal.bind(
                            fixture.identityBinding(
                                    "INTEGER:17",
                                    mode.equals("IDENTITY_MISMATCH")
                                            ? "STRING:another-synthetic"
                                            : "STRING:synthetic-17"),
                            CancellationToken.none());
                }
                final var result =
                        temporal.qualify(
                                fixture.binding.executionId(),
                                fixture.graph.guard().executionContext().executionId(),
                                CancellationToken.none());
                assertEquals(0, result.candidateRows());
                assertTrue(result.blockedRows() > 0);
                assertThrows(
                        RuntimeException.class,
                        () ->
                                new JdbcColetaTemporalLaboratory(session, true)
                                        .consume(
                                                fixture.binding.executionId(),
                                                fixture.graph
                                                        .guard()
                                                        .executionContext()
                                                        .executionId(),
                                                CancellationToken.none()));
            } finally {
                session.rollback();
            }
            assertEquals(before, counts(session));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"done", "finished", "canceled"})
    void preservesTerminalPrecedenceInverseOrderAndRejectsExactTie(final String terminal)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(session);
            try {
                fixture(session, "pending", NEXT_NANO).run();
                assertEquals(1, fixture(session, terminal, STAMP).run().updated());
                assertEquals(1, fixture(session, "pending", "2036-01-20T10:00:01Z").run().noop());
                assertEquals(
                        terminal,
                        scalar(session, "SELECT status_code FROM core.coleta_temporal_laboratory"));
                final var tied = fixture(session, "pending", STAMP);
                final var error = assertThrows(RuntimeException.class, tied::run);
                assertEquals(51428, sqlError(error));
            } finally {
                session.rollback();
            }
            assertEquals(before, counts(session));
        }
    }

    @Test
    void ordersNonterminalStatesUsingNanosecondsAndRejectsStaleUpdate() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(session);
            try {
                fixture(session, "pending", STAMP).run();
                assertEquals(1, fixture(session, "treatment", NEXT_NANO).run().updated());
                assertEquals(1, fixture(session, "pending", STAMP).run().noop());
                assertEquals(
                        "treatment",
                        scalar(session, "SELECT status_code FROM core.coleta_temporal_laboratory"));
                assertEquals(
                        "123456790",
                        scalar(session, "SELECT nano FROM core.coleta_temporal_laboratory"));
            } finally {
                session.rollback();
            }
            assertEquals(before, counts(session));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DATA_INTERRUPTED", "REFERENCE_INTERRUPTED", "CANCELLED", "DISABLED"})
    void incompleteAndCancelledCaptureRollBackWithoutResidue(final String mode)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(session);
            try {
                final var signal = new CancellationSignal();
                final var fixture =
                        new ColetasTemporalPhysicalFixture(
                                session,
                                mode.equals("DATA_INTERRUPTED")
                                        ? List.of("[" + data("pending", null) + "]")
                                        : List.of("[" + data("pending", null) + "]", "[]"),
                                List.of(
                                        graphPage(
                                                mode.equals("REFERENCE_INTERRUPTED"),
                                                reference("pending", STAMP))),
                                signal);
                if (mode.equals("CANCELLED")) {
                    signal.cancel();
                }
                if (mode.equals("DISABLED")) {
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    new LocalColetasTemporalRuntime(
                                                    session,
                                                    fixture.streamer,
                                                    fixture.graph.streamer(),
                                                    ColetasTemporalPhysicalFixture.CLOCK,
                                                    false)
                                            .execute(fixture.input(), List.of(), signal));
                } else {
                    assertThrows(RuntimeException.class, fixture::run);
                }
            } finally {
                session.rollback();
            }
            assertEquals(before, counts(session));
        }
    }

    @Test
    void twoSessionsExerciseActualStagingQualificationAndConsumerThenFullFlowAfterRollback()
            throws Exception {
        try (var first = ColetaTemporalLaboratorySession.openFromEnvironment();
                var second = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(first);
            final var executor = Executors.newSingleThreadExecutor();
            try {
                final var fixture = fixture(first, "pending", STAMP);
                fixture.run();
                final String observation =
                        scalar(
                                first,
                                "SELECT observation_json FROM stg.coleta_temporal_observation");
                for (final String operation : List.of("stage", "qualify", "consume")) {
                    final var future =
                            executor.submit(
                                    () -> {
                                        try (var connection = second.getConnection();
                                                var statement =
                                                        connection.prepareCall(
                                                                switch (operation) {
                                                                    case "stage" ->
                                                                            "{call stg.usp_stage_coleta_temporal(?)}";
                                                                    case "qualify" ->
                                                                            "{call recon.usp_qualify_coleta_temporal_v2(?,?)}";
                                                                    default ->
                                                                            "{call core.usp_consume_coleta_temporal_laboratory(?,?,?)}";
                                                                })) {
                                            try (var begin = connection.createStatement()) {
                                                begin.execute(
                                                        "IF @@TRANCOUNT=0 BEGIN TRANSACTION;");
                                            }
                                            statement.setQueryTimeout(8);
                                            statement.setNString(
                                                    1,
                                                    operation.equals("stage")
                                                            ? observation
                                                            : fixture.binding
                                                                    .executionId()
                                                                    .toString());
                                            if (!operation.equals("stage")) {
                                                statement.setString(
                                                        2,
                                                        fixture.graph
                                                                .guard()
                                                                .executionContext()
                                                                .executionId()
                                                                .toString());
                                            }
                                            if (operation.equals("consume")) {
                                                statement.setBoolean(3, true);
                                            }
                                            statement.execute();
                                            return 0;
                                        } catch (final SQLException failure) {
                                            return failure.getErrorCode();
                                        } finally {
                                            second.rollback();
                                        }
                                    });
                    final int expected =
                            operation.equals("stage")
                                    ? 53008
                                    : operation.equals("qualify") ? 1222 : 53111;
                    assertEquals(expected, future.get(12, TimeUnit.SECONDS));
                }
                first.rollback();
                assertEquals(1, fixture(second, "done", NEXT_NANO).run().inserted());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
                first.rollback();
                second.rollback();
            }
            assertEquals(before, counts(first));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"IDENTICAL", "DIVERGENT", "AFTER_SEAL", "CONTRACT", "TENANT", "AMBIGUOUS"})
    void exercisesPhysicalReferenceRetrySealingContractAndBindingGuards(final String mode)
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(session);
            try {
                final var fixture = fixture(session, "pending", STAMP);
                final var temporal = capture(fixture);
                final var binding = fixture.identityBinding("INTEGER:17", "STRING:synthetic-17");
                temporal.bind(binding, CancellationToken.none());
                final var raw =
                        (com.fasterxml.jackson.databind.node.ObjectNode)
                                new ObjectMapper()
                                        .readTree(
                                                scalar(
                                                        session,
                                                        "SELECT observation_json FROM stg.coleta_temporal_observation"));
                if (mode.equals("DIVERGENT")) {
                    raw.put("observedAt", "2036-01-22T12:00:00Z");
                }
                if (mode.equals("AFTER_SEAL")) {
                    raw.put("page", 2);
                }
                if (mode.equals("CONTRACT")) {
                    raw.put("contractVersion", "unapproved");
                }
                final Runnable call =
                        () -> {
                            if (mode.equals("TENANT") || mode.equals("AMBIGUOUS")) {
                                final String sql =
                                        "EXEC stg.usp_bind_coleta_temporal ?,?,?,?,?,?,?,?,?";
                                execute(
                                        session,
                                        sql,
                                        fixture.binding.executionId().toString(),
                                        fixture.graph
                                                .guard()
                                                .executionContext()
                                                .executionId()
                                                .toString(),
                                        LocalColetasTemporalRuntime.SOURCE,
                                        mode.equals("TENANT")
                                                ? "OTHER_SYNTHETIC"
                                                : LocalColetasTemporalRuntime.TENANT,
                                        "INTEGER:17",
                                        "STRING:other-synthetic",
                                        ColetasTemporalPhysicalFixture.DATE.toString(),
                                        ColetasTemporalPhysicalFixture.EVIDENCE.version(),
                                        ColetasTemporalPhysicalFixture.EVIDENCE.sha256());
                            } else {
                                execute(
                                        session,
                                        "EXEC stg.usp_stage_coleta_temporal ?",
                                        raw.toString());
                            }
                        };
                if (mode.equals("IDENTICAL")) {
                    call.run();
                    temporal.bind(binding, CancellationToken.none());
                    assertEquals(
                            "1",
                            scalar(
                                    session,
                                    "SELECT COUNT_BIG(*) FROM stg.coleta_temporal_observation"));
                } else {
                    final int expected =
                            switch (mode) {
                                case "DIVERGENT" -> 53002;
                                case "AFTER_SEAL" -> 53003;
                                case "CONTRACT" -> 53000;
                                case "TENANT" -> 53006;
                                default -> 53007;
                            };
                    assertEquals(
                            expected, sqlError(assertThrows(RuntimeException.class, call::run)));
                }
            } finally {
                session.rollback();
            }
            assertEquals(before, counts(session));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void typedAndExactStagingRetryIsAtomicAndRejectsDivergence(final boolean divergent)
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(session);
            try {
                final var fixture = fixture(session, "pending", STAMP);
                final var staging = new JdbcSqlServerColetaStagingGateway(session, true);
                new ExtrairColetasDataExport(
                                fixture.streamer, new ColetaDataExportRecordMapper(), staging)
                        .execute(
                                fixture.guard,
                                ColetasTemporalPhysicalFixture.request(),
                                fixture.input().dataLimits(),
                                CancellationToken.none());
                final var row =
                        new ColetaDataExportRecordMapper()
                                .map(
                                        1,
                                        new ObjectMapper()
                                                .readTree(
                                                        data(
                                                                divergent ? "done" : "pending",
                                                                null)));
                final var batch =
                        new ColetaStageBatch(
                                fixture.binding.executionId(),
                                1,
                                List.of(row),
                                ColetasTemporalPhysicalFixture.CLOCK.instant());
                if (divergent) {
                    assertEquals(
                            51403,
                            sqlError(
                                    assertThrows(
                                            RuntimeException.class, () -> staging.stage(batch))));
                } else {
                    staging.stage(batch);
                    assertEquals(
                            "1", scalar(session, "SELECT COUNT_BIG(*) FROM stg.coleta_exact_time"));
                }
            } finally {
                session.rollback();
            }
            assertEquals(before, counts(session));
        }
    }

    @Test
    void cancellationAfterPhysicalTypedStagingRollsBackAuditAndAllRows() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = counts(session);
            final var signal = new CancellationSignal();
            final var staged = new AtomicInteger();
            try {
                final var fixture =
                        new ColetasTemporalPhysicalFixture(
                                session,
                                List.of("[" + data("pending", null) + "]", "[]"),
                                List.of(graphPage(false, reference("pending", STAMP))),
                                signal);
                fixture.beforeDataFetch =
                        page -> {
                            if (page == 2) {
                                try {
                                    staged.set(
                                            Integer.parseInt(
                                                    scalar(
                                                            session,
                                                            "SELECT COUNT_BIG(*) FROM stg.coleta_exact_time")));
                                } catch (final SQLException failure) {
                                    throw new IllegalStateException(failure);
                                }
                                signal.cancel();
                            }
                        };
                assertThrows(ResilienceCancelledException.class, fixture::run);
                assertEquals(1, staged.get());
                assertEquals(before, counts(session));
            } finally {
                session.rollback();
            }
            assertEquals(before, counts(session));
        }
    }

    private static void execute(
            final ColetaTemporalLaboratorySession session,
            final String sql,
            final Object... parameters) {
        try (var connection = session.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(10);
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.execute();
        } catch (final SQLException failure) {
            throw new IllegalStateException("SYNTHETIC_SQL_REJECTED", failure);
        }
    }

    private static JdbcSqlServerColetaTemporalGateway capture(
            final ColetasTemporalPhysicalFixture fixture) {
        final var temporal = new JdbcSqlServerColetaTemporalGateway(fixture.session, true);
        final var dataResult =
                new ExtrairColetasDataExport(
                                fixture.streamer,
                                new ColetaDataExportRecordMapper(),
                                new JdbcSqlServerColetaStagingGateway(fixture.session, true))
                        .execute(
                                fixture.guard,
                                ColetasTemporalPhysicalFixture.request(),
                                fixture.input().dataLimits(),
                                fixture.cancellation);
        new JdbcColetaTemporalLaboratory(fixture.session, true)
                .completeDataExport(dataResult.executionId(), fixture.cancellation);
        new PersistirReferenciasTemporaisColetas(
                        fixture.graph.streamer(), temporal, ColetasTemporalPhysicalFixture.CLOCK)
                .execute(
                        fixture.graph.guard().executionContext(),
                        LocalColetasTemporalRuntime.SOURCE,
                        LocalColetasTemporalRuntime.TENANT,
                        ColetasTemporalPhysicalFixture.DATE,
                        fixture.input().referenceLimits(),
                        fixture.cancellation);
        return temporal;
    }

    private static ColetasTemporalPhysicalFixture fixture(
            final ColetaTemporalLaboratorySession session, final String status, final String stamp)
            throws SQLException {
        return new ColetasTemporalPhysicalFixture(
                session,
                List.of("[" + data(status, null) + "]", "[]"),
                List.of(graphPage(false, reference(status, stamp))),
                CancellationToken.none());
    }

    private static int sqlError(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getErrorCode();
            }
        }
        return 0;
    }

    private static String counts(final ColetaTemporalLaboratorySession session)
            throws SQLException {
        return scalar(
                session,
                """
                SELECT CONCAT((SELECT COUNT_BIG(*) FROM ctl.execution_attempt),':',
                    (SELECT COUNT_BIG(*) FROM ctl.execution_audit),':',(SELECT COUNT_BIG(*) FROM ctl.page_audit),':',
                    (SELECT COUNT_BIG(*) FROM stg.execution_record),':',(SELECT COUNT_BIG(*) FROM stg.coleta_record),':',
                    (SELECT COUNT_BIG(*) FROM stg.coleta_exact_time),':',(SELECT COUNT_BIG(*) FROM stg.coleta_temporal_run),':',
                    (SELECT COUNT_BIG(*) FROM stg.coleta_temporal_observation),':',
                    (SELECT COUNT_BIG(*) FROM stg.coleta_temporal_binding),':',
                    (SELECT COUNT_BIG(*) FROM core.coleta_temporal_laboratory),':',
                    (SELECT COUNT_BIG(*) FROM recon.coleta_temporal_laboratory_application),':',
                    (SELECT COUNT_BIG(*) FROM core.coleta))
                """);
    }

    private static String scalar(final ColetaTemporalLaboratorySession session, final String sql)
            throws SQLException {
        try (var connection = session.getConnection();
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (var rows = statement.executeQuery(sql)) {
                assertTrue(rows.next());
                final String result = rows.getString(1);
                assertFalse(rows.next());
                return result;
            }
        }
    }
}
