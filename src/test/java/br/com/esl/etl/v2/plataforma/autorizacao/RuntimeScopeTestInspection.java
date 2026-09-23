package br.com.esl.etl.v2.plataforma.autorizacao;

/** Test-classpath inspection of descriptive scope only; cannot create a capability. */
public final class RuntimeScopeTestInspection {
    private RuntimeScopeTestInspection() {}

    public static String material(final RuntimeAuthorizationScope scope) {
        return scope.material();
    }
}
