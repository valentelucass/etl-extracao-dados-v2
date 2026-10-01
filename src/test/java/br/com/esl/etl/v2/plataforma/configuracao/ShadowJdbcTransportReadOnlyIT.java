package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Prova o transporte local antes de qualquer schema, dentro do perfil Maven opt-in. */
class ShadowJdbcTransportReadOnlyIT {

    @Test
    void acceptsOnlyAsciiSpellingOfTheLocalMachineName() {
        assertTrue(isExpectedLocalMachineName("Lucas"));
        assertTrue(isExpectedLocalMachineName("LUCAS"));
        assertFalse(isExpectedLocalMachineName("LUCA\u017F"));
        assertFalse(isExpectedLocalMachineName("LUC\u0391S"));
        assertFalse(isExpectedLocalMachineName(null));
    }

    @Test
    void connectsOnlyToEmptyShadowOverLoopbackTcpWithWindowsAuthentication()
            throws IOException, SQLException {
        Assumptions.assumeTrue(Boolean.getBoolean("shadow.local.integration.enabled"));
        Assumptions.assumeTrue(Boolean.getBoolean("shadow.local.integration.profile.active"));
        final String url = System.getenv("V2_SHADOW_JDBC_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        final String validatedUrl =
                QualificationConfiguration.read(
                                Path.of(
                                        "src/main/resources/qualification-laboratory/config.synthetic.json"))
                        .validatedJdbcUrl(url);

        try (Connection connection = DriverManager.getConnection(validatedUrl)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement();
                    ResultSet result =
                            statement.executeQuery(
                                    """
                                    SELECT DB_NAME(),
                                           CONVERT(nvarchar(128), SERVERPROPERTY('MachineName')),
                                           CONVERT(nvarchar(32), CONNECTIONPROPERTY('net_transport')),
                                           CONVERT(nvarchar(64), CONNECTIONPROPERTY('local_net_address')),
                                           CONVERT(int, CONNECTIONPROPERTY('local_tcp_port')),
                                           (SELECT COUNT(*) FROM sys.objects
                                             WHERE type = 'U' AND is_ms_shipped = 0),
                                           (SELECT COUNT(*) FROM sys.objects
                                             WHERE type = 'V' AND is_ms_shipped = 0),
                                           (SELECT COUNT(*) FROM sys.objects
                                             WHERE type = 'P' AND is_ms_shipped = 0),
                                           OBJECT_ID(N'ctl.flyway_schema_history', N'U')
                                    """)) {
                assertTrue(result.next());
                assertEquals("ETL_SISTEMA_V2_SHADOW", result.getString(1));
                assertTrue(isExpectedLocalMachineName(result.getString(2)));
                assertEquals("TCP", result.getString(3));
                assertTrue(
                        "127.0.0.1".equals(result.getString(4))
                                || "::1".equals(result.getString(4)));
                assertEquals(1433, result.getInt(5));
                assertEquals(0, result.getInt(6));
                assertEquals(0, result.getInt(7));
                assertEquals(0, result.getInt(8));
                assertNull(result.getObject(9));
                assertFalse(result.next());
            } finally {
                connection.rollback();
            }
        }
    }

    private static boolean isExpectedLocalMachineName(final String machineName) {
        return machineName != null
                && machineName.matches("[A-Za-z0-9-]{1,63}")
                && "LUCAS".equalsIgnoreCase(machineName);
    }
}
