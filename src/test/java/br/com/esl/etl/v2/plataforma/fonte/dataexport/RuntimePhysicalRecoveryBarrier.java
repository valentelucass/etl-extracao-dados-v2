package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryPort;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoverySnapshot;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

final class RuntimePhysicalRecoveryBarrier implements RuntimeRecoveryPort {
    private final RuntimeRecoveryPort delegate;
    private final String barrier;

    RuntimePhysicalRecoveryBarrier(final RuntimeRecoveryPort delegate, final String barrier) {
        this.delegate = delegate;
        this.barrier = barrier;
    }

    @Override
    public void seal(
            final ControlPlaneStart start,
            final ImmutableFingerprint plan,
            final ContractPromotionPermit permit,
            final DataQualityPolicyReference policy,
            final CancellationToken token) {
        delegate.seal(start, plan, permit, policy, token);
    }

    @Override
    public void resume(
            final RuntimeRecoveryRequest request,
            final String revision,
            final CancellationToken token) {
        delegate.resume(request, revision, token);
    }

    @Override
    public RuntimeRecoverySnapshot read(
            final RuntimeRecoveryRequest request, final CancellationToken token) {
        final var snapshot = delegate.read(request, token);
        if (snapshot.reason() == RuntimeRecoverySnapshot.Reason.ELIGIBLE) {
            if (barrier.equals("LEASE")) {
                try {
                    Thread.sleep(11000);
                } catch (final InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("LEASE_WAIT_INTERRUPTED", failure);
                }
                return snapshot;
            }
            awaitPair(barrier);
        }
        return snapshot;
    }

    static void awaitPair(final String barrier) {
        final Path directory = Path.of("target/bloco53");
        try {
            Files.writeString(
                    directory.resolve(barrier + "." + ProcessHandle.current().pid() + ".ready"),
                    "ELIGIBLE",
                    java.nio.file.StandardOpenOption.CREATE_NEW);
            final long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (true) {
                try (var entries = Files.list(directory)) {
                    if (entries.filter(
                                            path ->
                                                    path.getFileName()
                                                                    .toString()
                                                                    .startsWith(barrier + ".")
                                                            && path.toString().endsWith(".ready"))
                                    .count()
                            == 2) {
                        break;
                    }
                }
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("REAL_RESUME_BARRIER_DEADLINE");
                }
                Thread.sleep(25);
            }
        } catch (final InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BARRIER_INTERRUPTED", failure);
        } catch (final java.io.IOException failure) {
            throw new IllegalStateException("BARRIER_IO", failure);
        }
    }
}
