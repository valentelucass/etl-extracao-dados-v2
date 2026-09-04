package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationException;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationReason;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeEnvironment;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RuntimeCompositionRootTest {

    @Test
    void directCompositionCannotBypassTheUnconfiguredAuthorizationBoundary() {
        final UUID invocationId = UUID.fromString("00000000-0000-0000-0000-000000000942");
        final RuntimeConfiguration configuration =
                new RuntimeConfiguration(
                        RuntimeEnvironment.LOCAL_SHADOW,
                        ZoneId.of("America/Sao_Paulo"),
                        Clock.fixed(Instant.parse("2026-08-30T15:00:00Z"), ZoneOffset.UTC),
                        Optional.empty(),
                        Optional.empty(),
                        ShadowStorageProperties.disabled());
        final RuntimeCompositionRoot compositionRoot = new RuntimeCompositionRoot(configuration);

        final RuntimeAuthorizationException exception =
                assertThrows(
                        RuntimeAuthorizationException.class,
                        () ->
                                compositionRoot.authorizeOperationalAction(
                                        invocationId, RuntimeAction.RUN));

        assertEquals(invocationId, exception.invocationId());
        assertEquals(RuntimeAction.RUN, exception.action());
        assertEquals(RuntimeAuthorizationReason.AUDIT_UNAVAILABLE, exception.reason());
    }
}
