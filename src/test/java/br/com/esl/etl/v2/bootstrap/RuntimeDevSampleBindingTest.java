package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevEngineAdmissionProducer;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevEngineBankAdapter;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78Sample12AdmissionProducer;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/** Real factory/adapter linkage under missing Root only; never an authenticated or physical run. */
class RuntimeDevSampleBindingTest {
    private static RuntimeDevEngineBankBinding.Scope scope(
            final RuntimeOperationalRequest request) {
        return new RuntimeDevEngineBankBinding.Scope(
                RuntimeDevSampleExecutionTest.configuration(),
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
    }

    private static RuntimeDevEngineBankBinding.Admission held(final AtomicInteger calls) {
        return new RuntimeDevEngineBankBinding.Admission() {
            @Override
            public void verify() throws SQLException {
                calls.incrementAndGet();
                throw new RuntimeDevEngineBankBinding.Refusal(
                        RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA);
            }

            @Override
            public RuntimeDevEngineBankBinding.Session open() {
                throw new AssertionError("NO_SOURCE_SESSION_ADMITTED");
            }

            @Override
            public void close() {}
        };
    }

    @Test
    void sampleBindsConcreteSelectionProducerAndSameThreeArgumentAdapter() throws Exception {
        final var request =
                RuntimeDevSampleExecutionTest.request(
                        RuntimeDevSampleExecutionTest.configuration(), 1, 1);
        final var selected = scope(request);
        final var sourceCalls = new AtomicInteger();
        try (var source = held(sourceCalls);
                var producer =
                        RuntimeDevEngineExecution.admissionProducer(
                                selected, request, null, null, source, true);
                var admission =
                        new C78DevEngineBankAdapter(null, null, producer).prepare(selected)) {
            assertInstanceOf(C78Sample12AdmissionProducer.class, producer);
            assertEquals(
                    RuntimeDevEngineBankBinding.MissingGate.ADMISSION,
                    assertThrows(RuntimeDevEngineBankBinding.Refusal.class, admission::verify)
                            .gate());
            assertEquals(
                    RuntimeDevEngineBankBinding.MissingGate.ADMISSION,
                    assertThrows(RuntimeDevEngineBankBinding.Refusal.class, admission::open)
                            .gate());
            assertEquals(0, sourceCalls.get());
        }
    }

    @Test
    void ordinaryKeepsExistingInspectionFactoryWithOriginalThousandRowDescription()
            throws Exception {
        final var request =
                RuntimeDevSampleExecutionTest.request(
                        RuntimeDevSampleExecutionTest.configuration(), 1, 1, 1000);
        final var selected = scope(request);
        final var sourceCalls = new AtomicInteger();
        try (var source = held(sourceCalls);
                var producer =
                        RuntimeDevEngineExecution.admissionProducer(
                                selected, request, null, null, source, false);
                var admission =
                        new C78DevEngineBankAdapter(null, null, producer).prepare(selected)) {
            assertInstanceOf(C78DevEngineAdmissionProducer.class, producer);
            assertEquals(
                    RuntimeDevEngineBankBinding.MissingGate.ADMISSION,
                    assertThrows(RuntimeDevEngineBankBinding.Refusal.class, admission::verify)
                            .gate());
            assertEquals(0, sourceCalls.get());
        }
    }

    @Test
    void sampleSelectionRefusesOriginalThousandRowsBeforeRootSourceOrProvider() throws Exception {
        final var request =
                RuntimeDevSampleExecutionTest.request(
                        RuntimeDevSampleExecutionTest.configuration(), 1, 1, 1000);
        final var sourceCalls = new AtomicInteger();
        try (var source = held(sourceCalls)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            RuntimeDevEngineExecution.admissionProducer(
                                    scope(request), request, null, null, source, true));
            assertEquals(0, sourceCalls.get());
        }
    }
}
