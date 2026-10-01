package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class MainLocalCliAdmissionTest {
    @Test
    void everyLocalRouteRejectsAnIncompleteCommandBeforeAStorageSession() {
        for (final String command :
                new String[] {
                    "local-data", "local-scenario", "local-sequence", "local-sweep", "local-raster"
                }) {
            final var output = new ByteArrayOutputStream();
            final var error = new ByteArrayOutputStream();
            final int code =
                    Main.run(
                            new String[] {command, "run"},
                            new PrintStream(output),
                            new PrintStream(error),
                            new Main.RuntimeDependencies(
                                    new Properties(),
                                    Map.of(),
                                    Clock.systemUTC(),
                                    new RuntimeConfigurationFactory()));
            assertNotEquals(0, code, command);
            assertFalse(output.toString().contains("ROLLBACK_CONFIRMED"), command);
            assertFalse(output.toString().contains("SQL_"), command);
        }
    }
}
