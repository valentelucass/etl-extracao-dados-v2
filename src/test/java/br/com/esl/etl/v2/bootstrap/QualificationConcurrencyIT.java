package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationConcurrencyIT {
    @Test
    @Timeout(120)
    void separateSpidsRefuseAndCancelAContendedClaimThenConsumeAfterOwnerRollback()
            throws Exception {
        final var configuration =
                QualificationConfiguration.read(
                        Path.of(
                                "src/main/resources/qualification-laboratory/config.synthetic.json"));
        final var result =
                QualificationConcurrency.execute(configuration, CancellationToken.none());
        assertNotEquals(result.ownerSpid(), result.contenderSpid());
        assertEquals(53401, result.timeoutCode());
        assertEquals(1, result.cancelledStatements());
        assertTrue(result.cancellationMillis() < 5000);
        assertEquals(1, result.consumed());
        assertTrue(result.rollback());
        assertTrue(
                result.jdbcCalls() > 50 && result.jdbcCalls() <= configuration.maximumJdbcCalls());
    }
}
