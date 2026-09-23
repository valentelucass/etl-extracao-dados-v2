package br.com.esl.etl.v2.plataforma.autorizacao;

/**
 * Reuses authority protocol counterexamples; the real issuer/consumer adapter stays in the path.
 */
public final class RuntimeUsersAuthorityFixture {
    private final WindowsSqlRuntimeAuthorizationTest.Jdbc jdbc;
    private int connections;

    public RuntimeUsersAuthorityFixture(final RuntimeAuthorizationScope scope) {
        jdbc = new WindowsSqlRuntimeAuthorizationTest.Jdbc(scope);
    }

    public WindowsSqlRuntimeAuthorization authority() {
        return new WindowsSqlRuntimeAuthorization(
                WindowsSqlRuntimeAuthorizationTest.configuration(),
                () -> {
                    connections++;
                    return jdbc.connection();
                });
    }

    public void reject(final String reason) {
        if (reason.equals("denied")) {
            jdbc.denied = true;
        } else {
            jdbc.mutation = reason;
        }
    }

    public boolean consumed() {
        return jdbc.consumed.get();
    }

    public int connections() {
        return connections;
    }
}
