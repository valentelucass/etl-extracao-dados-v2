package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Negative selected-SAMPLE boundaries only; no connection, source, provider or Main is used. */
class RuntimeDevSamplePreparationFinishTest {
    private enum Site {
        CHECKPOINT,
        DATA_SOURCE,
        DATA_SOURCE_NULL,
        GATEWAY
    }

    private static final class PreparationSession implements RuntimeDevEngineBankBinding.Session {
        private final Site site;
        private final Throwable primary;
        private Throwable finishFailure;
        private Throwable closeFailure;
        private int checkpoints;
        private int dataSources;
        private int gateways;
        private int finishes;
        private int closes;
        private RuntimeDevEngineBankBinding.SampleFinish received;

        private PreparationSession(final Site site, final Throwable primary) {
            this.site = site;
            this.primary = primary;
        }

        private static void fail(final Throwable failure) throws SQLException {
            if (failure instanceof SQLException sql) {
                throw sql;
            }
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure instanceof Error error) {
                throw error;
            }
        }

        @Override
        public void checkpoint() throws SQLException {
            checkpoints++;
            if (site == Site.CHECKPOINT) {
                fail(primary);
            }
        }

        @Override
        public DataSource dataSource() {
            dataSources++;
            if (site == Site.DATA_SOURCE) {
                if (primary instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw (Error) primary;
            }
            if (site == Site.DATA_SOURCE_NULL) {
                return null;
            }
            return (DataSource)
                    Proxy.newProxyInstance(
                            DataSource.class.getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            (proxy, method, args) -> {
                                throw new AssertionError("NO_DATA_SOURCE_OPERATION_ADMITTED");
                            });
        }

        @Override
        public ColetaStagingGateway sampleStagingGateway() throws SQLException {
            gateways++;
            if (site == Site.GATEWAY) {
                fail(primary);
            }
            throw new AssertionError("NO_EXECUTOR_ADMITTED");
        }

        @Override
        public RuntimeDevEngineBankBinding.SampleReadback finishSample(
                final RuntimeDevEngineBankBinding.SampleFinish finish) throws SQLException {
            finishes++;
            assertEquals(0, closes);
            received = finish;
            fail(finishFailure);
            // Offline cleanup fixture only; no SQL count or physical proof is manufactured.
            return new RuntimeDevEngineBankBinding.SampleReadback(
                    false, true, true, true, true, 0, 0, 0, 0);
        }

        @Override
        public void close() throws SQLException {
            closes++;
            fail(closeFailure);
        }
    }

    private static final class Bank implements RuntimeDevEngineBankBinding {
        private final PreparationSession session;
        private SQLException verifyFailure;
        private int opens;
        private int closes;

        private Bank(final PreparationSession session) {
            this.session = session;
        }

        @Override
        public Admission prepare(final Scope scope) {
            return new Admission() {
                @Override
                public void verify() throws SQLException {
                    if (verifyFailure != null) {
                        throw verifyFailure;
                    }
                }

                @Override
                public Session open() {
                    opens++;
                    return session;
                }

                @Override
                public void close() {
                    closes++;
                }
            };
        }
    }

    private static RuntimeDevEngineExecution.Result execute(final Bank bank, final boolean sample)
            throws Exception {
        final var configuration = RuntimeDevSampleExecutionTest.configuration();
        final var request = RuntimeDevSampleExecutionTest.request(configuration, 1, 1);
        final var scope =
                new RuntimeDevEngineBankBinding.Scope(
                        configuration,
                        RuntimeAuthorizationScope.from(
                                request.invocation, RuntimeAction.RUN, request.plan, 0),
                        C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE),
                        "a".repeat(64),
                        "b".repeat(64),
                        "c".repeat(64),
                        request.plan.fingerprint().sha256(),
                        request.template,
                        request.pageSize,
                        request.limits);
        try (var diagnostic =
                new PrintStream(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8)) {
            return RuntimeDevEngineExecution.execute(scope, request, bank, diagnostic, sample);
        }
    }

    private static JsonNode unverified(final RuntimeDevEngineExecution.Result result)
            throws Exception {
        final var receipt = new ObjectMapper().readTree(result.receipt());
        assertEquals("DEV_ENGINE_OUTCOME_NOT_VERIFIED", receipt.path("status").asText());
        assertTrue(receipt.path("openAttempted").asBoolean());
        assertFalse(receipt.path("executorInvoked").asBoolean());
        assertFalse(receipt.path("cleanupVerified").asBoolean());
        assertFalse(receipt.path("goalComplete").asBoolean());
        assertFalse(result.receipt().contains("COMPONENT_PRIVATE_MESSAGE"));
        assertFalse(receipt.has("sampleVerifiedPhysicalRows"));
        return receipt;
    }

    private static void finishedOnce(final Bank bank) {
        assertEquals(1, bank.opens);
        assertEquals(1, bank.closes);
        assertEquals(1, bank.session.finishes);
        assertEquals(1, bank.session.closes);
        assertTrue(bank.session.received.expectation().isEmpty());
        assertEquals(
                RuntimeDevEngineBankBinding.SampleOutcome.FAILED, bank.session.received.outcome());
    }

    @Test
    void initialCheckpointFailureFinishesExactlyOnceBeforeIndependentClose() throws Exception {
        final var session =
                new PreparationSession(
                        Site.CHECKPOINT, new SQLException("COMPONENT_PRIVATE_MESSAGE"));
        final var bank = new Bank(session);
        final var receipt = unverified(execute(bank, true));
        finishedOnce(bank);
        assertEquals("java.sql.SQLException", receipt.path("failureType").asText());
        assertEquals(0, session.dataSources);
        assertEquals(0, session.gateways);
    }

    @Test
    void nullDataSourceBeforeGatewayStillFinishesExactlyOnce() throws Exception {
        final var session = new PreparationSession(Site.DATA_SOURCE_NULL, null);
        final var bank = new Bank(session);
        final var receipt = unverified(execute(bank, true));
        finishedOnce(bank);
        assertEquals("java.lang.NullPointerException", receipt.path("failureType").asText());
        assertEquals(1, session.checkpoints);
        assertEquals(1, session.dataSources);
        assertEquals(0, session.gateways);
    }

    @Test
    void dataSourceFailurePreservesPrimaryAndSuppressesOwnerFailureWithoutSensitiveMessage()
            throws Exception {
        final var primary = new IllegalStateException("COMPONENT_PRIVATE_MESSAGE");
        final var cleanup = new SQLException("COMPONENT_PRIVATE_MESSAGE_OWNER");
        final var session = new PreparationSession(Site.DATA_SOURCE, primary);
        session.finishFailure = cleanup;
        final var bank = new Bank(session);
        final var receipt = unverified(execute(bank, true));
        finishedOnce(bank);
        assertSame(cleanup, primary.getSuppressed()[0]);
        assertEquals("java.lang.IllegalStateException", receipt.path("failureType").asText());
        assertEquals(1, receipt.path("cleanupSuppressedCount").asInt());
        assertEquals(0, session.gateways);
    }

    @Test
    void gatewayRefusalAndFailedFinishAreNotRetriedOrPublishedAsVerified() throws Exception {
        final var primary =
                new RuntimeDevEngineBankBinding.Refusal(
                        RuntimeDevEngineBankBinding.MissingGate.PHYSICAL_CONTRACT);
        final var cleanup = new SQLException("COMPONENT_PRIVATE_MESSAGE_OWNER");
        final var session = new PreparationSession(Site.GATEWAY, primary);
        session.finishFailure = cleanup;
        final var bank = new Bank(session);
        final var receipt = unverified(execute(bank, true));
        finishedOnce(bank);
        assertSame(cleanup, primary.getSuppressed()[0]);
        assertEquals(1, session.gateways);
        assertEquals(1, receipt.path("cleanupSuppressedCount").asInt());
        assertFalse(receipt.path("failureV2Frame").asText().equals("V2_FRAME_NOT_OBSERVED"));
    }

    @Test
    void preparationErrorRemainsPrimaryWithFinishAndCloseErrorsSuppressed() {
        final var primary = new AssertionError("COMPONENT_PRIVATE_MESSAGE");
        final var cleanup = new LinkageError("COMPONENT_PRIVATE_MESSAGE_OWNER");
        final var closing = new AssertionError("COMPONENT_PRIVATE_MESSAGE_CLOSE");
        final var session = new PreparationSession(Site.CHECKPOINT, primary);
        session.finishFailure = cleanup;
        session.closeFailure = closing;
        final var bank = new Bank(session);
        final var caught = assertThrows(AssertionError.class, () -> execute(bank, true));
        assertSame(primary, caught);
        assertEquals(2, caught.getSuppressed().length);
        assertSame(cleanup, caught.getSuppressed()[0]);
        assertSame(closing, caught.getSuppressed()[1]);
        finishedOnce(bank);
    }

    @Test
    void refusalBeforeOpenNeverCreatesOrFinishesSession() throws Exception {
        final var session = new PreparationSession(Site.CHECKPOINT, null);
        final var bank = new Bank(session);
        bank.verifyFailure =
                new RuntimeDevEngineBankBinding.Refusal(
                        RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA);
        final var result = execute(bank, true);
        final var receipt = new ObjectMapper().readTree(result.receipt());
        assertEquals("DEV_ENGINE_GATE_REFUSED_ZERO_EFFECTS", receipt.path("status").asText());
        assertEquals(0, bank.opens);
        assertEquals(0, session.finishes);
        assertEquals(0, session.closes);
        assertEquals(1, bank.closes);
    }

    @Test
    void ordinaryPreparationFailureKeepsHistoricalCloseWithoutSampleFinish() throws Exception {
        final var session =
                new PreparationSession(
                        Site.CHECKPOINT, new SQLException("COMPONENT_PRIVATE_MESSAGE"));
        final var bank = new Bank(session);
        unverified(execute(bank, false));
        assertEquals(1, bank.opens);
        assertEquals(1, bank.closes);
        assertEquals(1, session.closes);
        assertEquals(0, session.finishes);
    }
}
