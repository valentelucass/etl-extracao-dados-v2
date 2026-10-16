package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DevLocalMainConfigurationTest {
    @TempDir Path temporary;

    @Test
    void safeSampleRoundTripsAndRejectsPhysicalConfigurationFields() throws Exception {
        final var sample = DevLocalMainConfiguration.sample();
        assertEquals(sample, parse(sample.document().toString()));
        assertThrows(
                IllegalArgumentException.class,
                () -> parse(sample.document().put("jdbcUrl", "SYNTHETIC_CANARY").toString()));
        assertThrows(
                IllegalArgumentException.class,
                () -> parse(sample.document().put("synthetic", false).toString()));
    }

    @Test
    void duplicateTrailingAndNonIntegralConfigurationRefused() {
        final var sample = DevLocalMainConfiguration.sample().document().toString();
        assertThrows(
                java.io.IOException.class,
                () ->
                        parse(
                                sample.replace(
                                        "\"synthetic\":true",
                                        "\"synthetic\":true,\"synthetic\":true")));
        assertThrows(IllegalArgumentException.class, () -> parse(sample + " {}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> parse(sample.replace("\"scale\":2", "\"scale\":2.0")));
    }

    @Test
    void boundedSerialConfigurationAcceptsOnlyDeclaredFourScales() {
        for (final int scale : new int[] {2, 4, 8, 16}) {
            assertEquals(scale, new DevLocalMainConfiguration(scale, 4, 1, 65536, 30).scale());
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new DevLocalMainConfiguration(3, 4, 1, 65536, 30));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DevLocalMainConfiguration(2, 4, 2, 65536, 30));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DevLocalMainConfiguration(2, 4, 1, 65536, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> DevLocalMainConfiguration.parse(new byte[4097]));
    }

    @Test
    void cliRequiresOptInAndRejectsAmbiguousPathsFlagsAndIdentities() {
        final var valid =
                new String[] {
                    "run", "--synthetic", "--workspace", "target/example", "--execution", "sample01"
                };
        assertEquals("sample01", DevLocalMainArguments.parse(valid).executionId());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DevLocalMainArguments.parse(
                                new String[] {
                                    "run",
                                    "--workspace",
                                    "target/example",
                                    "--execution",
                                    "sample01"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DevLocalMainArguments.parse(
                                new String[] {
                                    "run", "--synthetic", "--workspace", "//SYNTHETIC/remote"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DevLocalMainArguments.parse(
                                new String[] {
                                    "run",
                                    "--synthetic",
                                    "--workspace",
                                    "target/example",
                                    "--execution",
                                    "../other"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DevLocalMainArguments.parse(
                                new String[] {
                                    "run",
                                    "--synthetic",
                                    "--synthetic",
                                    "--workspace",
                                    "target/example"
                                }));
    }

    private static DevLocalMainConfiguration parse(final String value) throws Exception {
        return DevLocalMainConfiguration.parse(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void scenarioRequiresExplicitSyntheticBuiltInCaseAndPrefix() {
        final var valid =
                new String[] {
                    "scenario",
                    "--synthetic",
                    "--workspace",
                    "target/example",
                    "--execution",
                    "scenario01",
                    "--case",
                    "ABSENCE_REACTIVATION"
                };
        assertEquals("ABSENCE_REACTIVATION", DevLocalMainArguments.parse(valid).scenarioCase());
        final var unknown = valid.clone();
        unknown[7] = "EXTERNAL_CASE";
        assertThrows(IllegalArgumentException.class, () -> DevLocalMainArguments.parse(unknown));
        final var wrongCommand = valid.clone();
        wrongCommand[0] = "run";
        assertThrows(
                IllegalArgumentException.class, () -> DevLocalMainArguments.parse(wrongCommand));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DevLocalMainArguments.parse(
                                new String[] {
                                    "scenario",
                                    "--synthetic",
                                    "--workspace",
                                    "target/example",
                                    "--case",
                                    "ABSENCE_REACTIVATION"
                                }));
    }

    @Test
    void suppliedConfigurationReadsOnlyFiniteExplicitJsonFiles() throws Exception {
        final var safe = temporary.resolve("safe.json");
        Files.writeString(safe, DevLocalMainConfiguration.sample().document().toString());
        assertEquals(DevLocalMainConfiguration.sample(), DevLocalMainConfiguration.read(safe));
        final var dotenv = temporary.resolve(".env");
        Files.writeString(dotenv, "SYNTHETIC_REJECTED_INPUT_CANARY");
        assertThrows(IllegalArgumentException.class, () -> DevLocalMainConfiguration.read(dotenv));
        final var excessive = temporary.resolve("excessive.json");
        Files.write(excessive, new byte[4097]);
        assertThrows(
                IllegalArgumentException.class, () -> DevLocalMainConfiguration.read(excessive));
        assertThrows(
                IllegalArgumentException.class, () -> DevLocalMainConfiguration.read(temporary));
    }
}
