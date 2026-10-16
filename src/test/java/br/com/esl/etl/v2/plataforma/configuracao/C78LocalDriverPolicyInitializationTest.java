package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.ToolProvider;

class C78LocalDriverPolicyInitializationTest {
    @TempDir Path privateDirectory;

    @Test
    void wrongJarCannotInitializeStaticCodeBeforePin() throws Exception {
        final Path source = privateDirectory.resolve("SQLServerDriver.java");
        Files.writeString(
                source,
                """
package com.microsoft.sqlserver.jdbc;
public final class SQLServerDriver implements java.sql.Driver {
  static { System.setProperty("c78.fixture.unqualified.driver.initialized", "TRUE"); }
  public SQLServerDriver() { System.setProperty("c78.fixture.constructor.executed", "TRUE"); }
  public java.sql.Connection connect(String u, java.util.Properties p) { throw new AssertionError("NO_NETWORK"); }
  public boolean acceptsURL(String u) { return false; }
  public java.sql.DriverPropertyInfo[] getPropertyInfo(String u, java.util.Properties p) { return new java.sql.DriverPropertyInfo[0]; }
  public int getMajorVersion() { System.setProperty("c78.fixture.method.executed", "TRUE"); return 12; }
  public int getMinorVersion() { return 8; }
  public boolean jdbcCompliant() { return false; }
  public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getLogger("fixture"); }
}
""",
                StandardCharsets.UTF_8);
        final var compilerOutput = new ByteArrayOutputStream();
        assertEquals(
                0,
                ToolProvider.getSystemJavaCompiler()
                        .run(
                                null,
                                compilerOutput,
                                compilerOutput,
                                "--release",
                                "17",
                                "-Xlint:all",
                                "-Werror",
                                "-d",
                                privateDirectory.toString(),
                                source.toString()));
        final var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Bundle-Version", "12.8.2");
        final Path jar = privateDirectory.resolve("unqualified-driver.jar");
        try (var archive = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            archive.putNextEntry(
                    new JarEntry("com/microsoft/sqlserver/jdbc/SQLServerDriver.class"));
            archive.write(
                    Files.readAllBytes(
                            privateDirectory.resolve(
                                    "com/microsoft/sqlserver/jdbc/SQLServerDriver.class")));
            archive.closeEntry();
        }
        probe(jar, "reject");
    }

    @Test
    void originalQualifiedDriverInitializesOnlyAfterPin() throws Exception {
        final var driver = C78LocalDriverPolicy.load();
        final Path jar =
                Path.of(
                        driver.getClass()
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        probe(jar, "accept");
    }

    private static void probe(final Path jar, final String mode) throws Exception {
        final Path classes =
                Path.of(
                        C78LocalDriverPolicy.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        final Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        final var process =
                new ProcessBuilder(
                                java.toString(),
                                "-cp",
                                classes + File.pathSeparator + jar,
                                Probe.class.getName(),
                                mode)
                        .start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "OWN_FIXTURE_DEADLINE");
            assertEquals(
                    0,
                    process.exitValue(),
                    new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                            .contains("C78_DRIVER_GUARD_PASS"));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "OWN_FIXTURE_CLOSE_UNKNOWN");
            }
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
        }
    }

    /** A separate private JVM prevents an already initialized real driver masking the RED. */
    public static final class Probe {
        private Probe() {}

        public static void main(final String[] args) {
            if ("accept".equals(args[0])) {
                C78LocalDriverPolicy.load();
            } else {
                try {
                    C78LocalDriverPolicy.load();
                    throw new AssertionError("UNQUALIFIED_DRIVER_ACCEPTED");
                } catch (final IllegalStateException expected) {
                    // Refusal must precede every unqualified driver effect.
                }
                for (final String marker :
                        new String[] {
                            "c78.fixture.unqualified.driver.initialized",
                            "c78.fixture.constructor.executed",
                            "c78.fixture.method.executed"
                        }) {
                    if (System.getProperty(marker) != null) {
                        throw new AssertionError("UNQUALIFIED_DRIVER_CODE_BEFORE_PIN");
                    }
                }
            }
            System.out.println("C78_DRIVER_GUARD_PASS");
        }
    }
}
