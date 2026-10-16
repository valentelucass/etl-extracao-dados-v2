package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevEngineBankAdapter;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78Sample12AdmissionProducer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Concrete Bank factory, typed Source reader and adapter negatives; no Root unit or effects. */
class RuntimeDevCurrentSourceFactoryTest {
    @Test
    void actualCurrentFactoryBindsOneProducerReaderAndRefusesRootBeforeSourceOrOpen()
            throws Exception {
        final var config = RuntimeDevSampleExecutionTest.configuration();
        final var request = RuntimeDevSampleExecutionTest.request(config, 1, 1);
        final var scope =
                new RuntimeDevEngineBankBinding.Scope(
                        config,
                        RuntimeAuthorizationScope.from(
                                request.invocation, RuntimeAction.RUN, request.plan),
                        C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE),
                        "a".repeat(64),
                        "b".repeat(64),
                        "c".repeat(64),
                        request.plan.fingerprint().sha256(),
                        request.template,
                        request.pageSize,
                        request.limits);
        final var output = new ByteArrayOutputStream();
        try (var diagnostic = new PrintStream(output, false, StandardCharsets.UTF_8)) {
            final var composed =
                    RuntimeDevEngineExecution.currentSourceProducer(
                            scope,
                            request,
                            Path.of(
                                    "C:/Users/suporte/Documents/Armazenamento-Projetos/"
                                        + "etl-extracao-dados-v2/execucao-esl-shadow-20261008-164952/"
                                        + "hermesc54d6a25880d"),
                            null,
                            null,
                            diagnostic);
            assertTrue(composed.producer() instanceof C78Sample12AdmissionProducer);
            final var adapter = new C78DevEngineBankAdapter(null, null, composed.producer());
            try (var reader = composed.reader();
                    var admission = adapter.prepare(scope)) {
                final var refusal =
                        assertThrows(RuntimeDevEngineBankBinding.Refusal.class, admission::verify);
                assertEquals(RuntimeDevEngineBankBinding.MissingGate.ADMISSION, refusal.gate());
                assertThrows(RuntimeDevEngineBankBinding.Refusal.class, admission::open);
                assertThrows(RuntimeDevEngineBankBinding.Refusal.class, reader::verify);
                assertThrows(RuntimeDevEngineBankBinding.Refusal.class, reader::verify);
            }
        }
        assertEquals(0, output.size());
    }
}
