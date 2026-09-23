package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QualificationSupervisorTest {
    @Test
    void childClasspathKeepsTheRuntimeWildcardOutsideWindowsPathResolution() {
        final var jar = Path.of("sealed", "etl-dataexport-v2.jar");
        final var libraryDirectory = Path.of("sealed", "lib");

        final String classpath =
                assertDoesNotThrow(
                        () -> QualificationSupervisor.childClasspath(jar, libraryDirectory));

        assertEquals(jar + File.pathSeparator + libraryDirectory + File.separator + "*", classpath);
    }
}
