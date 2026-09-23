package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExpansionLaboratoryMainTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "--roots=0",
                "--roots=257",
                "--roots=-1",
                "--roots=01",
                "--page-size=17",
                "--days=4",
                "--path=payload.json",
                "--url=https://example.invalid",
                "--source=real",
                "--database=ETL_SISTEMA",
                "--environment=prod",
                "--graphql=true",
                "--permit=fabricated",
                "--mode=SWEEP",
                "--limit=1",
                "--projection=CAP",
                "--after=1",
                "--roots=999999999999"
            })
    void flagsAreRefusedBeforeOpeningSession(final String flag) {
        final var output = new ByteArrayOutputStream();
        assertEquals(
                RuntimeExitCategory.CONFIG_AUTH,
                ExpansionLaboratoryMain.run(
                        new String[] {"scenario", "--synthetic-expansion-lab", flag},
                        new PrintStream(output)));
        assertEquals(
                "EXP_LAB_CONFIG_REJECTED" + System.lineSeparator(),
                output.toString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void requiresOptInSingleValuedFlagsAndBoundedQueryScope() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExpansionLaboratoryMain.Options.parse(new String[] {"scenario"}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExpansionLaboratoryMain.Options.parse(
                                new String[] {"query", "--synthetic-expansion-lab"}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExpansionLaboratoryMain.Options.parse(
                                new String[] {
                                    "scenario",
                                    "--synthetic-expansion-lab",
                                    "--roots=1",
                                    "--roots=2"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExpansionLaboratoryMain.Options.parse(
                                new String[] {
                                    "query",
                                    "--synthetic-expansion-lab",
                                    "--projection=REVENUE",
                                    "--limit=101"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExpansionLaboratoryMain.Options.parse(
                                new String[] {
                                    "scenario",
                                    "--synthetic-expansion-lab",
                                    "--days=3",
                                    "--roots=100"
                                }));
        assertEquals(
                1,
                ExpansionLaboratoryMain.Options.parse(
                                new String[] {
                                    "query",
                                    "--synthetic-expansion-lab",
                                    "--projection=REVENUE",
                                    "--limit=1",
                                    "--after=0"
                                })
                        .limit());
    }
}
