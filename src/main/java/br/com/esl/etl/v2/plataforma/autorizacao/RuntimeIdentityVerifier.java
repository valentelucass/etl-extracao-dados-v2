package br.com.esl.etl.v2.plataforma.autorizacao;

/** Porta provider-neutral que entrega uma identidade verificada para cada invocação. */
@FunctionalInterface
public interface RuntimeIdentityVerifier {

    VerifiedRuntimeIdentity verify();

    static RuntimeIdentityVerifier denyAll() {
        return UnconfiguredRuntimeIdentityVerifier.instance();
    }
}
