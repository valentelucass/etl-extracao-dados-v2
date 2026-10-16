package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayBundle;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlan;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Actual current consumer/wrappers over JDBC/source fixtures; never physical admission/delivery.
 */
class C78Sample12ConsumerCounterTest {
    @Test
    void finishErrorsKeepTheSameOwnedHandleEligibleForIndependentCleanup() throws Exception {
        for (String fault : new String[] {"rollback", "readback", "close", "observation"}) {
            var selected = C78Sample12OwnerTest.selection();
            var fixture = new C78DevEngineBankAdapterTest.Fixture(selected.scope());
            fixture.dispatch.withObject("limits").put("rows", 12).put("statements", 128);
            fixture.reference(
                    "reservation",
                    C78DevEngineBankAdapterTest.JSON
                            .createObjectNode()
                            .put("dispatchId", fixture.dispatch.path("dispatchId").textValue())
                            .set("limits", fixture.dispatch.path("limits").deepCopy()));
            fixture.save();
            var model =
                    new Model(
                            1,
                            selected,
                            new RuntimeBootstrapTestFixture(Instant.now())
                                    .dataSource()
                                    .getConnection());
            try (var controls = fixture.control()) {
                Field pid = C78DevEngineDispatch.class.getDeclaredField("provedSqlPID");
                pid.setAccessible(true);
                pid.setLong(controls, 77);
                var boundary =
                        new RuntimeDevEngineBankBinding.Admission() {
                            public void verify() {}

                            public RuntimeDevEngineBankBinding.Session open() {
                                throw new AssertionError("TEST_NOT_AUTHORITY");
                            }

                            public void close() {}
                        };
                var session =
                        new C78DevEngineSession(model.connection(), controls, boundary, selected);
                session.establishProtection();
                Error fatal = new AssertionError("COMPONENT_FATAL");
                switch (fault) {
                    case "rollback" -> model.rollbackError = fatal;
                    case "readback" -> model.readbackError = fatal;
                    case "close" -> model.closeError = fatal;
                    case "observation" -> model.isClosedError = fatal;
                    default -> throw new AssertionError();
                }
                var finish = C78Sample12OwnerTest.finish(selected, 1);
                assertSame(
                        fatal,
                        assertThrows(AssertionError.class, () -> session.finishSample(finish)));
                assertEquals(1, model.closeAttempts);
                assertFalse(session.active());
                if (fault.equals("close") || fault.equals("observation")) {
                    assertFalse(session.physicalClosed());
                    model.closeError = null;
                    model.isClosedError = null;
                    session.close();
                    assertEquals(2, model.closeAttempts);
                    assertTrue(session.physicalClosed());
                } else {
                    assertTrue(session.physicalClosed());
                    session.close();
                    assertEquals(1, model.closeAttempts);
                }
            }
        }
    }

    @Test
    void replacementTransactionEvenWithAll29LocksRefusesAfterTheActualCall() throws Exception {
        var selected = C78Sample12OwnerTest.selection();
        var fixture = new C78DevEngineBankAdapterTest.Fixture(selected.scope());
        fixture.dispatch.withObject("limits").put("rows", 12).put("statements", 128);
        fixture.reference(
                "reservation",
                C78DevEngineBankAdapterTest.JSON
                        .createObjectNode()
                        .put("dispatchId", fixture.dispatch.path("dispatchId").textValue())
                        .set("limits", fixture.dispatch.path("limits").deepCopy()));
        fixture.save();
        var jdbc = new RuntimeBootstrapTestFixture(Instant.now()).dataSource().getConnection();
        var model = new Model(1, selected, jdbc);
        try (var controls = fixture.control()) {
            Field pid = C78DevEngineDispatch.class.getDeclaredField("provedSqlPID");
            pid.setAccessible(true);
            pid.setLong(controls, 77);
            var boundary =
                    new RuntimeDevEngineBankBinding.Admission() {
                        public void verify() {}

                        public RuntimeDevEngineBankBinding.Session open() {
                            throw new AssertionError("TEST_NOT_AUTHORITY");
                        }

                        public void close() {}
                    };
            var session = new C78DevEngineSession(model.connection(), controls, boundary, selected);
            session.establishProtection();
            model.transactionIdAfterCall = 56;
            var gateway = session.sampleStagingGateway();
            var row =
                    new br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper()
                            .map(
                                    1,
                                    C78DevEngineBankAdapterTest.JSON
                                            .createObjectNode()
                                            .put("id", 1));
            var batch =
                    new br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch(
                            selected.item().request().executionId(),
                            1,
                            java.util.List.of(row),
                            Instant.now());
            assertThrows(
                    br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException
                            .class,
                    () -> gateway.stage(batch));
            assertEquals(1, model.stageCalls);
            assertThrows(RuntimeDevEngineBankBinding.Refusal.class, session::checkpoint);
            var finish =
                    new RuntimeDevEngineBankBinding.SampleFinish(
                            java.util.Optional.empty(),
                            br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory.SOURCE_DQ,
                            RuntimeDevEngineBankBinding.SampleOutcome.FAILED);
            assertTrue(session.finishSample(finish).matches(finish));
            assertTrue(model.closed);
        }
    }

    @Test
    void actualConsumerOneAndTwelveWithTwoAdditionalTypedHeartbeatsStayInside128()
            throws Exception {
        for (int rows : new int[] {1, 12}) {
            final Class<?> fixtureType =
                    Class.forName("br.com.esl.etl.v2.bootstrap.RuntimeDevSampleExecutionTest");
            final var configuration =
                    (RuntimeConfiguration) call(fixtureType, "configuration", new Class<?>[0]);
            final Object request =
                    call(
                            fixtureType,
                            "request",
                            new Class<?>[] {RuntimeConfiguration.class, int.class, int.class},
                            configuration,
                            1,
                            1);
            final var plan = (RuntimeExecutionPlan) field(request, "plan");
            final var invocation = (java.util.UUID) field(request, "invocation");
            final var scope =
                    new RuntimeDevEngineBankBinding.Scope(
                            configuration,
                            RuntimeAuthorizationScope.from(invocation, RuntimeAction.RUN, plan),
                            C78DevEngineBankAdapterTest.scope().target(),
                            "a".repeat(64),
                            "a".repeat(64),
                            "a".repeat(64),
                            "a".repeat(64),
                            DataExportTemplate.COLETAS,
                            1,
                            new DataExportExtractionLimits(1, 12, 1));
            final var selection = C78Sample12Selection.from(scope, plan);
            final var controlFixture = new C78DevEngineBankAdapterTest.Fixture(scope);
            controlFixture.dispatch.withObject("limits").put("rows", 12).put("statements", 128);
            controlFixture.reference(
                    "reservation",
                    C78DevEngineBankAdapterTest.JSON
                            .createObjectNode()
                            .put(
                                    "dispatchId",
                                    controlFixture.dispatch.path("dispatchId").textValue())
                            .set("limits", controlFixture.dispatch.path("limits").deepCopy()));
            controlFixture.save();
            final var bootstrap =
                    new RuntimeBootstrapTestFixture(Instant.parse("2036-03-21T12:00:00Z"));
            final var owner = new Model(rows, selection, bootstrap.dataSource().getConnection());
            try (var controls = controlFixture.control()) {
                Field pid = C78DevEngineDispatch.class.getDeclaredField("provedSqlPID");
                pid.setAccessible(true);
                pid.setLong(controls, 77);
                var boundary =
                        new RuntimeDevEngineBankBinding.Admission() {
                            public void verify() {}

                            public RuntimeDevEngineBankBinding.Session open() {
                                throw new AssertionError("TEST_NOT_AUTHORITY");
                            }

                            public void close() {}
                        };
                final var session =
                        new C78DevEngineSession(owner.connection(), controls, boundary, selection);
                session.establishProtection();
                Object original =
                        call(
                                fixtureType,
                                "oneEntity",
                                new Class<?>[] {
                                    RuntimeBootstrapTestFixture.class, boolean.class, int.class
                                },
                                bootstrap,
                                false,
                                rows);
                Class<?> sourceType =
                        Class.forName(
                                "br.com.esl.etl.v2.bootstrap.RuntimeOperationalExecution$SourceGateways");
                Object source =
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {sourceType},
                                (p, m, a) -> {
                                    m.setAccessible(true);
                                    final var bundle =
                                            (DataExportHttpGatewayBundle) m.invoke(original, a);
                                    final var cp =
                                            new JdbcSqlServerControlPlane(session.dataSource());
                                    final var now = configuration.clock().instant();
                                    return RuntimeBootstrapTestFixture.boundGateways(
                                            page -> {
                                                cp.heartbeat(
                                                        scope.authorization().executionId(),
                                                        now.plusSeconds(40),
                                                        Duration.ofSeconds(60));
                                                return bundle.dataGateway().fetch(page);
                                            },
                                            template -> {
                                                cp.heartbeat(
                                                        scope.authorization().executionId(),
                                                        now.plusSeconds(20),
                                                        Duration.ofSeconds(60));
                                                return bundle.templateInfoGateway()
                                                        .fetchInfo(template);
                                            },
                                            (br.com.esl.etl.v2.plataforma.fonte.dataexport
                                                            .DataExportContractObservationConfiguration)
                                                    a[3]);
                                });
                Class<?> consumer =
                        Class.forName("br.com.esl.etl.v2.bootstrap.RuntimeDevSampleExecution");
                final var result =
                        call(
                                consumer,
                                "execute",
                                new Class<?>[] {
                                    boolean.class,
                                    RuntimeConfiguration.class,
                                    request.getClass(),
                                    javax.sql.DataSource.class,
                                    sourceType,
                                    br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway
                                            .class,
                                    Runnable.class
                                },
                                true,
                                configuration,
                                request,
                                session.dataSource(),
                                source,
                                session.sampleStagingGateway(),
                                (Runnable)
                                        () -> {
                                            try {
                                                session.checkpoint();
                                            } catch (SQLException failure) {
                                                throw new IllegalStateException(failure);
                                            }
                                        });
                var finish =
                        (RuntimeDevEngineBankBinding.SampleFinish)
                                call(result.getClass(), "finish", new Class<?>[0], result);
                assertTrue(finish.expectation().isPresent());
                assertEquals(rows, owner.stageCalls);
                assertTrue(owner.perCallBindCounts.stream().allMatch(value -> value == 23));
                var readback = session.finishSample(finish);
                assertTrue(readback.matches(finish));
                assertEquals(2, owner.rollbacks);
                assertTrue(owner.closed);
                assertEquals(controls.statementsUsed(), owner.sql.size() + owner.calls);
                assertEquals(40 + owner.calls * 2, controls.statementsUsed());
                assertTrue(controls.statementsUsed() <= 128);
                assertTrue(
                        owner.callNames.contains("ctl.usp_runtime_status")
                                || owner.callNames.contains("ctl.usp_runtime_recovery"));
                assertEquals(
                        3,
                        owner.callNames.stream()
                                .filter(
                                        name ->
                                                name.equals(
                                                        "ctl.usp_control_plane_heartbeat_lease"))
                                .count());
                assertEquals(owner.calls, owner.originalReadbacks - 4);
                assertThrows(RuntimeDevEngineBankBinding.Refusal.class, session::checkpoint);
                System.out.println(
                        "{\"scope\":\"ACTUAL_CONSUMER_COMPONENT_COUNTER_ONLY\",\"N\":"
                                + rows
                                + ",\"Hadditional\":2,\"statements\":"
                                + controls.statementsUsed()
                                + ",\"calls\":"
                                + owner.calls
                                + ",\"physicalSQL\":0,\"SourceHTTP\":0}");
            }
        }
    }

    private static Object call(Class<?> owner, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method m = owner.getDeclaredMethod(name, types);
        m.setAccessible(true);
        Object target = null;
        if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
            target = args[0];
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        try {
            return m.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw failure;
        }
    }

    private static Object field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(owner);
    }

    static final class Model extends C78Sample12OwnerTest.Fixture {
        final C78Sample12Selection actualSelection;
        final Connection underlying;
        final ArrayList<String> callNames = new ArrayList<>();
        final ArrayList<Integer> perCallBindCounts = new ArrayList<>();
        int calls, stageCalls, originalReadbacks;
        long transactionId = 55;
        long transactionIdAfterCall;

        Model(int rows, C78Sample12Selection selection, Connection underlying) {
            super(rows);
            actualSelection = selection;
            this.underlying = underlying;
            transaction = 0;
        }

        @Override
        Connection connection() {
            final Connection delegate = super.connection();
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (p, m, a) -> {
                                return switch (m.getName()) {
                                    case "createStatement" ->
                                            Proxy.newProxyInstance(
                                                    getClass().getClassLoader(),
                                                    new Class<?>[] {java.sql.Statement.class},
                                                    (s, method, args) -> {
                                                        if (method.getName()
                                                                .equals("setQueryTimeout")) {
                                                            timeouts.add((Integer) args[0]);
                                                            return null;
                                                        }
                                                        if (method.getName()
                                                                .equals("executeQuery")) {
                                                            String text = (String) args[0];
                                                            sql.add(text);
                                                            if (text.contains("SERVERPROPERTY")) {
                                                                return result(
                                                                        new Object[] {
                                                                            1,
                                                                            "16.0.1000.6",
                                                                            1,
                                                                            "SQL",
                                                                            77L,
                                                                            84L,
                                                                            0
                                                                        });
                                                            }
                                                            if (text.contains(
                                                                    "TABLOCKX,HOLDLOCK")) {
                                                                return result(new Object[] {1});
                                                            }
                                                            if (text.contains(
                                                                    "COUNT(DISTINCT"
                                                                        + " resource_associated_entity_id)")) {
                                                                originalReadbacks++;
                                                                return result(
                                                                        new Object[] {
                                                                            1, 1, 29, transactionId
                                                                        });
                                                            }
                                                            return result(
                                                                    new Object[] {
                                                                        transaction, transaction
                                                                    });
                                                        }
                                                        return primitive(method.getReturnType());
                                                    });
                                    case "prepareStatement" -> ownerPrepared((String) a[0]);
                                    case "setAutoCommit" -> {
                                        assertEquals(false, a[0]);
                                        transaction = 1;
                                        yield null;
                                    }
                                    case "getAutoCommit" -> false;
                                    case "prepareCall" -> callable((String) a[0]);
                                    case "rollback", "close", "isClosed" -> {
                                        try {
                                            yield m.invoke(delegate, a);
                                        } catch (
                                                java.lang.reflect.InvocationTargetException
                                                        failure) {
                                            throw failure.getCause();
                                        }
                                    }
                                    default -> primitive(m.getReturnType());
                                };
                            });
        }

        Object ownerPrepared(String sql) {
            Object original = super.statement(sql);
            return Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {java.sql.PreparedStatement.class},
                    (p, m, a) -> {
                        if (m.getName().equals("executeQuery")
                                && sql.contains("SELECT p.environment_name")) {
                            this.sql.add(sql);
                            if (readbackError != null) {
                                throw readbackError;
                            }
                            var item = actualSelection.item();
                            var part = item.partition();
                            return result(
                                    new Object[] {
                                        part.environment(),
                                        part.sourceInstance(),
                                        part.tenantScope(),
                                        "coletas",
                                        "BACKFILL",
                                        java.sql.Timestamp.from(part.partitionStart()),
                                        java.sql.Timestamp.from(part.partitionEndExclusive()),
                                        item.definition().contract().version(),
                                        item.definition().contract().sha256(),
                                        item.definition().configuration().version(),
                                        item.definition().configuration().sha256(),
                                        actualSelection.plan().cycleId().toString(),
                                        actualSelection.plan().fingerprint().version(),
                                        actualSelection.plan().fingerprint().sha256(),
                                        item.request().idempotencyKey(),
                                        item.request().windowStrategy().name(),
                                        "FAILED",
                                        1L,
                                        1L,
                                        0L,
                                        0L,
                                        0L,
                                        0L,
                                        0L,
                                        0L,
                                        0L,
                                        1L,
                                        1L
                                    });
                        }
                        try {
                            return m.invoke(original, a);
                        } catch (java.lang.reflect.InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }

        CallableStatement callable(String sql) throws SQLException {
            String name = sql.substring(6, sql.indexOf('('));
            final CallableStatement delegate =
                    name.equals("stg.usp_stage_coleta_record") ? null : underlying.prepareCall(sql);
            Set<Integer> binds = new HashSet<>();
            return (CallableStatement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {CallableStatement.class},
                            (p, m, a) -> {
                                if (m.getName().startsWith("set")
                                        && a != null
                                        && a.length > 1
                                        && a[0] instanceof Integer index) {
                                    binds.add(index);
                                }
                                if (m.getName().startsWith("execute")) {
                                    calls++;
                                    callNames.add(name);
                                    if (delegate == null) {
                                        stageCalls++;
                                        perCallBindCounts.add(binds.size());
                                        if (transactionIdAfterCall != 0) {
                                            transactionId = transactionIdAfterCall;
                                        }
                                        return 1;
                                    }
                                }
                                if (delegate == null) {
                                    return primitive(m.getReturnType());
                                }
                                try {
                                    return m.invoke(delegate, a);
                                } catch (java.lang.reflect.InvocationTargetException failure) {
                                    throw failure.getCause();
                                }
                            });
        }
    }
}
