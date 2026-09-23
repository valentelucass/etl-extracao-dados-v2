package br.com.esl.etl.v2.plataforma.autorizacao;

import java.nio.file.Path;
import java.time.Instant;

/** Exercises the packaged manifest verifier and authority resources without SQL or ACL claims. */
public final class AdministeredBundleOfflineProbe {
    private AdministeredBundleOfflineProbe() {}

    public static void main(final String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("OFFLINE_BUNDLE_ARGUMENTS_REQUIRED");
        }
        final Path jar = Path.of(args[0]).toAbsolutePath().normalize();
        try {
            AdministeredArtifactVerifier.verifyInstalled(
                    jar, jar.getParent().getParent(), Instant.parse(args[1]), ignored -> {});
            RuntimeAuthorityConfiguration.load();
            System.out.println("OFFLINE_BUNDLE_MANIFEST_AND_AUTHORITY_ACCEPTED_SQL_NOT_OPENED");
        } catch (final java.io.IOException | RuntimeException failure) {
            final String message = failure.getMessage();
            System.err.println(
                    message != null && message.matches("[A-Z0-9_]{1,120}")
                            ? message
                            : "OFFLINE_BUNDLE_REFUSED_REDACTED");
            System.exit(12);
        }
    }
}
