package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationControlIT {
    @Test
    @Timeout(30)
    void openHandleCeilingRefusesOverflowAndReusesReleasedCapacity() throws Exception {
        assertEquals(64, ColetaTemporalLaboratorySession.MAXIMUM_OPEN_STATEMENTS);
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment();
                var connection = session.getConnection()) {
            session.controlStatements(30, 100);
            final var handles = new java.util.ArrayList<java.sql.Statement>();
            try {
                for (int index = 0; index < 64; index++) {
                    handles.add(connection.createStatement());
                }
                assertEquals(64, session.openControlledStatements());
                final var refused = assertThrows(SQLException.class, connection::createStatement);
                assertEquals("COL_LAB_OPEN_STATEMENT_LIMIT", refused.getMessage());
                assertEquals(64, session.openControlledStatements());
                handles.get(0).close();
                try (var replacement = connection.prepareStatement("SELECT 1");
                        var row = replacement.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(1, row.getInt(1));
                    assertEquals(64, session.openControlledStatements());
                }
            } finally {
                for (final var statement : handles) {
                    statement.close();
                }
            }
            assertEquals(0, session.openControlledStatements());
            session.rollback();
        }
    }

    @Test
    @Timeout(30)
    void separateSqlSessionsConsumeTheSameCaseBudgetBeforePreparingAnyExcess() throws Exception {
        final var budget =
                new br.com.esl.etl.v2.plataforma.persistencia.coletas.LaboratoryJdbcBudget(2);
        try (var first = ColetaTemporalLaboratorySession.openFromEnvironment();
                var second = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            first.controlStatements(60, budget);
            second.controlStatements(60, budget);
            for (final var session : java.util.List.of(first, second)) {
                try (var connection = session.getConnection();
                        var sql = connection.prepareStatement("SELECT @@SPID");
                        var row = sql.executeQuery()) {
                    assertTrue(row.next());
                }
            }
            try (var connection = second.getConnection()) {
                final var refused =
                        assertThrows(SQLException.class, () -> connection.prepareCall("SELECT 1"));
                assertEquals("COL_LAB_JDBC_CALL_LIMIT", refused.getMessage());
            }
            assertEquals(2, budget.used());
            first.rollback();
            second.rollback();
            assertEquals(0, first.openControlledStatements() + second.openControlledStatements());
        }
    }

    @Test
    @Timeout(30)
    void declaredJdbcCapRefusesBeforeTheThirdStatementAndKeepsRollbackAndCloseAvailable()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(60, 2);
            for (int index = 0; index < 2; index++) {
                try (var connection = session.getConnection();
                        var sql = connection.prepareStatement("SELECT 1");
                        var row = sql.executeQuery()) {
                    assertTrue(row.next());
                }
            }
            try (var connection = session.getConnection()) {
                final var refused =
                        assertThrows(
                                SQLException.class, () -> connection.prepareStatement("SELECT 1"));
                assertEquals("COL_LAB_JDBC_CALL_LIMIT", refused.getMessage());
                session.rollback();
            }
            assertEquals(0, session.openControlledStatements());
        }
    }

    @Test
    @Timeout(30)
    void ownedCaptureBarrierConsumesAnAtomicCancelAndCancelsTheRealDriverStatement()
            throws Exception {
        final var directory =
                Files.createDirectories(
                        Path.of("target", "qualification-control-" + UUID.randomUUID()));
        final var nonce = UUID.randomUUID();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment();
                var control =
                        new QualificationCaseControl(
                                directory,
                                nonce,
                                QualificationCampaign.Barrier.DURING_CAPTURE,
                                20)) {
            session.controlStatements(60);
            control.attach(session);
            final var signal = Executors.newSingleThreadExecutor();
            try {
                final var delivered =
                        signal.submit(
                                () -> {
                                    final long deadline =
                                            System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                                    while (!Files.exists(directory.resolve("barrier.json"))
                                            && System.nanoTime() < deadline) {
                                        Thread.sleep(20);
                                    }
                                    final var barrier =
                                            QualificationJson.read(
                                                    directory.resolve("barrier.json"), 1024);
                                    assertEquals(nonce.toString(), barrier.path("nonce").asText());
                                    Thread.sleep(200);
                                    QualificationControlFiles.atomic(
                                            QualificationControlFiles.member(
                                                    directory, "cancel.json"),
                                            JsonNodeFactory.instance
                                                    .objectNode()
                                                    .put("nonce", nonce.toString())
                                                    .put("reason", "CONTROLLED_BARRIER"));
                                    return true;
                                });
                final long started = System.nanoTime();
                assertThrows(
                        ResilienceCancelledException.class,
                        () -> control.barrier(QualificationCampaign.Barrier.DURING_CAPTURE));
                assertTrue(delivered.get(5, TimeUnit.SECONDS));
                assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(5));
                assertEquals("CONTROLLED_BARRIER", control.reason());
                assertEquals(0, session.openControlledStatements());
                session.rollback();
            } finally {
                signal.shutdownNow();
                assertTrue(signal.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }
}
