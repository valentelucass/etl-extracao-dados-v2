package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionResult;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoverySnapshot.Reason;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.util.UUID;

/** Apenas processo filho do teste; não compõe a CLI nem abre rede/SQL físico. */
public final class RuntimeRecoveryProcessProbe {
    private RuntimeRecoveryProcessProbe() {}

    public static void main(final String[] args) throws Exception {
        final boolean write = args[0].equals("write");
        final var template = DataExportTemplate.valueOf(args[1]);
        final var path = Path.of(args[3]);
        final var jdbc =
                write
                        ? new RuntimeSyntheticJdbc(LocalRuntimeIntegrationTest.NOW)
                        : RuntimeRecoverySyntheticState.load(path);
        final var fixture = new LocalRuntimeIntegrationTest.Fixture(jdbc);
        final var work =
                new LocalRuntimeIntegrationTest.Work(
                        fixture,
                        template,
                        "aa-work",
                        UUID.fromString("00000000-0000-0000-0000-000000000052"));
        if (write) {
            jdbc.loseApplyResponse = args[2].equals("apply");
            jdbc.losePrepareResponse = args[2].equals("prepare");
            if (fixture.dispatch(work).result(work.id).status()
                    != RuntimeExecutionResult.Status.RECOVERY_REQUIRED) {
                throw new IllegalStateException("writer did not reach durable uncertainty");
            }
        } else {
            final var result =
                    RuntimeDurableRecoveryIntegrationTest.recover(
                            fixture, CancellationToken.none(), work);
            if (result.snapshot(work.id).reason() != Reason.PUBLISHED
                    || work.fetches.get() != 0
                    || jdbc.attempts.get(work.execution.toString()).applies != 1
                    || jdbc.openConnections != 0) {
                throw new IllegalStateException(
                        "reader failed durable recovery: " + result.snapshot(work.id).reason());
            }
        }
        jdbc.recovery.save(path);
    }
}
