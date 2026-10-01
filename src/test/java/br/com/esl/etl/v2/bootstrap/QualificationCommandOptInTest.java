package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class QualificationCommandOptInTest {
    @Test
    void commandsThatCanStartOrRecoverSqlRequireBothGatesBeforePackageAccess() throws Exception {
        final String enabled = System.getProperty("shadow.local.integration.enabled");
        final String profile = System.getProperty("shadow.local.integration.profile.active");
        try {
            for (final String command : new String[] {"run", "resume", "worker"}) {
                for (final boolean[] gates :
                        new boolean[][] {{false, false}, {true, false}, {false, true}}) {
                    System.setProperty(
                            "shadow.local.integration.enabled", Boolean.toString(gates[0]));
                    System.setProperty(
                            "shadow.local.integration.profile.active", Boolean.toString(gates[1]));
                    assertEquals(
                            "QUAL_SQL_OPT_IN_REQUIRED",
                            assertThrows(
                                            IllegalArgumentException.class,
                                            () ->
                                                    QualificationLaboratoryMain.execute(
                                                            new String[] {
                                                                command, "--manifest-sha=synthetic"
                                                            }))
                                    .getMessage());
                }
                System.setProperty("shadow.local.integration.enabled", "true");
                System.setProperty("shadow.local.integration.profile.active", "true");
                assertEquals(
                        "QUAL_PACKAGE_PIN",
                        assertThrows(
                                        IllegalArgumentException.class,
                                        () ->
                                                QualificationLaboratoryMain.execute(
                                                        new String[] {
                                                            command, "--manifest-sha=synthetic"
                                                        }))
                                .getMessage());
            }
        } finally {
            restore("shadow.local.integration.enabled", enabled);
            restore("shadow.local.integration.profile.active", profile);
        }
    }

    private static void restore(final String key, final String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
