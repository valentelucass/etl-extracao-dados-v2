package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import java.io.File;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualificationSupervisorTest {
    @TempDir Path folder;

    @Test
    void childClasspathKeepsTheRuntimeWildcardOutsideWindowsPathResolution() {
        final var jar = Path.of("sealed", "etl-dataexport-v2.jar");
        final var libraryDirectory = Path.of("sealed", "lib");

        final String classpath =
                assertDoesNotThrow(
                        () -> QualificationSupervisor.childClasspath(jar, libraryDirectory));

        assertEquals(jar + File.pathSeparator + libraryDirectory + File.separator + "*", classpath);
    }

    @Test
    void barrierMustBindTheExpectedNoncePointAndOwnedProcess() throws Exception {
        final var path = folder.resolve("barrier.json");
        final var nonce = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Files.writeString(
                path,
                "{\"nonce\":\""
                        + nonce
                        + "\",\"point\":\"BEFORE_SQL\",\"pid\":42,\"observedAt\":\"2037-08-11T12:00:00Z\"}");
        assertDoesNotThrow(
                () ->
                        QualificationSupervisor.verifyBarrier(
                                path, QualificationCampaign.Barrier.BEFORE_SQL, nonce, 42));
        assertEquals(
                "QUAL_BARRIER_BINDING",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        QualificationSupervisor.verifyBarrier(
                                                path,
                                                QualificationCampaign.Barrier.DURING_CAPTURE,
                                                nonce,
                                                42))
                        .getMessage());
        for (final long pid : new long[] {41, 43}) {
            assertEquals(
                    "QUAL_BARRIER_BINDING",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            QualificationSupervisor.verifyBarrier(
                                                    path,
                                                    QualificationCampaign.Barrier.BEFORE_SQL,
                                                    nonce,
                                                    pid))
                            .getMessage());
        }
        assertEquals(
                "QUAL_BARRIER_BINDING",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        QualificationSupervisor.verifyBarrier(
                                                path,
                                                QualificationCampaign.Barrier.BEFORE_SQL,
                                                UUID.fromString(
                                                        "00000000-0000-4000-8000-000000000002"),
                                                42))
                        .getMessage());
        assertEquals(
                "QUAL_BARRIER_BINDING",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        QualificationSupervisor.verifyBarrier(
                                                path,
                                                QualificationCampaign.Barrier.NONE,
                                                nonce,
                                                42))
                        .getMessage());
    }

    @Test
    void childLogRejectsMalformedUtf8ReplacementCharacterAndExcessLength() throws Exception {
        final var path = folder.resolve("stdout.log");
        Files.writeString(path, "started\nfinished\n", StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> QualificationSupervisor.verifyLog(path));
        Files.write(path, new byte[] {(byte) 0xc3, (byte) 0x28});
        assertThrows(CharacterCodingException.class, () -> QualificationSupervisor.verifyLog(path));
        Files.writeString(path, "replacement \ufffd", StandardCharsets.UTF_8);
        assertEquals(
                "QUAL_PROCESS_LOG_ENCODING",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> QualificationSupervisor.verifyLog(path))
                        .getMessage());
        Files.write(path, new byte[1048577]);
        assertEquals(
                "QUAL_PROCESS_LOG_LIMIT",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> QualificationSupervisor.verifyLog(path))
                        .getMessage());
    }
}
