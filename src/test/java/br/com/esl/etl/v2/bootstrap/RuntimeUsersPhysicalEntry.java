package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.autorizacao.DurableAuthorizationException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Test-only entry point with the same authorization refusal category as the official CLI. */
public final class RuntimeUsersPhysicalEntry {
    private RuntimeUsersPhysicalEntry() {}

    public static void main(final String[] args) {
        final var result =
                execute(
                        () -> {
                            RuntimeUsersPhysicalProbe.main(args);
                            return RuntimeExitCategory.SUCCESS;
                        },
                        System.err::println);
        System.exit(result.code());
    }

    public static RuntimeExitCategory execute(
            final Supplier<RuntimeExitCategory> operation, final Consumer<String> diagnostic) {
        try {
            return operation.get();
        } catch (final DurableAuthorizationException failure) {
            diagnostic.accept("Autorização operacional recusada: " + failure.reason());
            return RuntimeExitCategory.CONFIG_AUTH;
        }
    }
}
