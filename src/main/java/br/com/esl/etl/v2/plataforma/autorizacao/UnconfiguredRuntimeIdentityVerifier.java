package br.com.esl.etl.v2.plataforma.autorizacao;

/** Verificador padrão que mantém o runtime fechado enquanto não houver composição confiável. */
public final class UnconfiguredRuntimeIdentityVerifier implements RuntimeIdentityVerifier {

    private static final UnconfiguredRuntimeIdentityVerifier INSTANCE =
            new UnconfiguredRuntimeIdentityVerifier();

    private UnconfiguredRuntimeIdentityVerifier() {}

    public static UnconfiguredRuntimeIdentityVerifier instance() {
        return INSTANCE;
    }

    @Override
    public VerifiedRuntimeIdentity verify() {
        throw new RuntimeIdentityVerificationException(RuntimeIdentityRejectionReason.UNCONFIGURED);
    }

    @Override
    public String toString() {
        return "UnconfiguredRuntimeIdentityVerifier[deny-all]";
    }
}
