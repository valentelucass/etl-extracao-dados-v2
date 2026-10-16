package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Real Main children exercise the first pure gate, without authorizing any workload. */
class MainOperationalStartupDiagnosticProcessTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    Path directory;

    @Test
    void officialDisabledTargetExplainsItsGateBeforeReadingTheRequest() throws Exception {
        final Path configuration = directory.resolve("official.properties");
        Files.copy(Path.of("config/application.example.properties"), configuration);
        final Path request = directory.resolve("request-not-created.json");
        final var result = child(configuration, request);
        assertEquals(2, result.exit());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("WINDOWS_WORKLOAD_TARGET_REQUIRED"));
        assertFalse(Files.exists(request));
        final String classes = Files.readString(directory.resolve("classload.log"));
        assertTrue(classes.contains("br.com.esl.etl.v2.bootstrap.Main source:"));
        assertTrue(classes.contains("RuntimeCompositionRoot source:"));
        assertTrue(classes.contains("BoundedRuntimeDataSource source:"));
        // Verification can load request/authority types without reading or invoking them.
        assertFalse(classes.contains("AdministeredSqlConnection source:"));
        assertFalse(classes.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver source:"));
    }

    @Test
    void unreviewedConfigurationFailureDoesNotEchoTheInputPath() throws Exception {
        final String privateControl = UUID.randomUUID().toString();
        final Path configuration = directory.resolve(privateControl + ".properties");
        final var result = child(configuration, directory.resolve("request-not-created.json"));
        assertEquals(2, result.exit());
        assertEquals("", result.stdout());
        assertEquals("Configuração ou pedido operacional inválido.", result.stderr().strip());
        assertFalse(result.stderr().contains(privateControl));
        assertFalse(Files.exists(configuration));
    }

    @Test
    void unverifiedTlsIsRefusedBeforeReadingTheRequest() throws Exception {
        final Path configuration = directory.resolve("unverified-tls.properties");
        final String official = Files.readString(Path.of("config/application.example.properties"));
        Files.writeString(
                configuration,
                official.replace(
                        "shadow.audit.enabled=false",
                        "shadow.audit.enabled=true\nshadow.target-kind=LOCAL_EPHEMERAL\n"
                                + "shadow.jdbc-url=jdbc:sqlserver://localhost;"
                                + "databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;"
                                + "encrypt=true;trustServerCertificate=true"));
        final Path request = directory.resolve("request-not-created.json");
        final var result = child(configuration, request);
        assertEquals(2, result.exit());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("VERIFIED_LOCAL_WORKLOAD_TLS_REQUIRED"));
        assertFalse(Files.exists(request));
        assertFalse(
                Files.readString(directory.resolve("classload.log"))
                        .contains("com.microsoft.sqlserver.jdbc.SQLServerDriver source:"));
    }

    private Result child(final Path configuration, final Path request) throws Exception {
        final String javaName =
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        final var arguments = new ArrayList<String>();
        arguments.add(Path.of(System.getProperty("java.home"), "bin", javaName).toString());
        arguments.addAll(
                List.of(
                        "-Xmx256m",
                        "-Dfile.encoding=UTF-8",
                        "-Xlog:class+load=info:file=classload.log",
                        "-cp",
                        System.getProperty(
                                "surefire.test.class.path", System.getProperty("java.class.path")),
                        Main.class.getName(),
                        "run",
                        "--config",
                        configuration.toAbsolutePath().toString(),
                        "--request",
                        request.toAbsolutePath().toString()));
        final var builder = new ProcessBuilder(arguments).directory(directory.toFile());
        builder.environment()
                .keySet()
                .removeIf(
                        key ->
                                key.toUpperCase(java.util.Locale.ROOT).startsWith("V2_")
                                        || List.of(
                                                        "JAVA_TOOL_OPTIONS",
                                                        "JDK_JAVA_OPTIONS",
                                                        "_JAVA_OPTIONS",
                                                        "CLASSPATH")
                                                .contains(key.toUpperCase(java.util.Locale.ROOT)));
        final var readers = Executors.newFixedThreadPool(2);
        final var process = builder.start();
        try {
            process.getOutputStream().close();
            final var stdout = readers.submit(capture(process.getInputStream()));
            final var stderr = readers.submit(capture(process.getErrorStream()));
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "OWN_MAIN_CHILD_DEADLINE");
            return new Result(
                    process.exitValue(),
                    stdout.get(3, TimeUnit.SECONDS),
                    stderr.get(3, TimeUnit.SECONDS));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            assertTrue(process.waitFor(3, TimeUnit.SECONDS), "OWN_MAIN_CHILD_CLOSE");
            process.getInputStream().close();
            process.getErrorStream().close();
            readers.shutdownNow();
            assertTrue(readers.awaitTermination(3, TimeUnit.SECONDS), "OWN_MAIN_READERS_CLOSE");
            assertFalse(process.isAlive());
        }
    }

    private static Callable<String> capture(final InputStream stream) {
        return () -> {
            try (stream;
                    var bytes = new ByteArrayOutputStream()) {
                final byte[] buffer = new byte[4096];
                int count;
                boolean limited = false;
                while ((count = stream.read(buffer)) != -1) {
                    final int remaining = Math.max(0, 65_536 - bytes.size());
                    bytes.write(buffer, 0, Math.min(remaining, count));
                    limited |= count > remaining;
                }
                assertFalse(limited, "OWN_MAIN_OUTPUT_LIMIT");
                return bytes.toString(StandardCharsets.UTF_8);
            }
        };
    }

    private record Result(int exit, String stdout, String stderr) {}
}
