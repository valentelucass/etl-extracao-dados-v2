package br.com.esl.etl.v2.plataforma.resiliencia;

import java.util.concurrent.atomic.AtomicBoolean;

/** Fonte local de cancelamento cooperativo para um ciclo. */
public final class CancellationSignal implements CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    @Override
    public boolean isCancellationRequested() {
        return cancelled.get();
    }
}
