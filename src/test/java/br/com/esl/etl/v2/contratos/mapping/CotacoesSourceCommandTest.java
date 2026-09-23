package br.com.esl.etl.v2.contratos.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Explicit test-classpath command; ordinary verify cannot select source acquisition. */
@EnabledIfSystemProperty(named = "bloco57.cotacoes.command", matches = "preflight|execute")
class CotacoesSourceCommandTest {
    @Test
    void command() {
        String outcome = "INPUT_REJECTED";
        try {
            final var clock = Clock.systemUTC();
            final var input =
                    CotacoesSourceInput.read(
                            Path.of(System.getProperty("bloco57.cotacoes.input", "")),
                            clock.instant());
            if ("preflight".equals(System.getProperty("bloco57.cotacoes.command"))) {
                outcome = "SUCCESS";
            } else {
                final var transport =
                        new CotacoesCurlTransport(System.getenv("B57_COT_BEARER_TOKEN"));
                final var runner =
                        new CotacoesSourceRunner(
                                transport, clock, System::nanoTime, Thread::sleep, false);
                final var receipt = runner.run(input, Path.of("target", "bloco57-source-future"));
                outcome =
                        "BOUNDED_SAMPLE_MATCH".equals(receipt.path("status").textValue())
                                ? "SUCCESS"
                                : "SOURCE_SAMPLE_INCOMPLETE_SEE_RECEIPT";
            }
        } catch (final Exception error) {
            // Maven/JUnit output must contain only this fixed code, never a private file or cause.
            outcome = "INPUT_OR_EXECUTION_REJECTED_SEE_EXISTING_RESERVATION";
        }
        assertEquals("SUCCESS", outcome);
    }
}
