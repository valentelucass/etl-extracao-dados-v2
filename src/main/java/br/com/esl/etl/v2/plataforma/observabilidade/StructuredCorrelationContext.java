package br.com.esl.etl.v2.plataforma.observabilidade;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Contexto thread-bound que propaga somente a referência opaca, nunca o identificador bruto. */
public final class StructuredCorrelationContext {

    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private StructuredCorrelationContext() {}

    public static Scope openExecution(final UUID executionId) {
        return open(CorrelationReference.fromExecutionId(executionId));
    }

    public static Scope open(final CorrelationReference correlationReference) {
        final CorrelationReference required =
                Objects.requireNonNull(
                        correlationReference, "A referência de correlação é obrigatória.");
        final Frame installed = new Frame(required, CURRENT.get());
        CURRENT.set(installed);
        return new Scope(installed, Thread.currentThread());
    }

    public static Optional<CorrelationReference> current() {
        final Frame frame = CURRENT.get();
        return frame == null ? Optional.empty() : Optional.of(frame.reference());
    }

    public static CorrelationReference currentOrTechnicalScope(final String technicalScope) {
        final Frame current = CURRENT.get();
        return current == null
                ? CorrelationReference.fromTechnicalScope(technicalScope)
                : current.reference();
    }

    public static final class Scope implements AutoCloseable {

        private final Frame installed;
        private final Thread ownerThread;
        private boolean closed;

        private Scope(final Frame installed, final Thread ownerThread) {
            this.installed = installed;
            this.ownerThread = ownerThread;
        }

        @Override
        public void close() {
            if (Thread.currentThread() != ownerThread) {
                throw new IllegalStateException(
                        "O contexto de correlação deve ser fechado na thread de origem.");
            }
            if (closed) {
                return;
            }
            if (CURRENT.get() != installed) {
                throw new IllegalStateException(
                        "Os contextos de correlação devem ser fechados em ordem LIFO.");
            }
            closed = true;
            if (installed.previous() == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(installed.previous());
            }
        }
    }

    private record Frame(CorrelationReference reference, Frame previous) {}
}
