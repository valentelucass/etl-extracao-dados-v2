package br.com.esl.etl.v2.plataforma.autorizacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Matriz RBAC interna, fechada e sem hierarquia ou papel administrador. */
public final class RuntimeAuthorizationPolicy {

    private static final String VERSION = "runtime-rbac-v1";
    private static final RuntimeRoleSet OBSERVER = RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER);
    private static final RuntimeRoleSet EXECUTOR = RuntimeRoleSet.of(RuntimeRole.RUNTIME_EXECUTOR);
    private static final RuntimeRoleSet REPLAY =
            RuntimeRoleSet.of(RuntimeRole.RUNTIME_EXECUTOR, RuntimeRole.RUNTIME_REPLAY);
    private static final RuntimeRoleSet SWEEP_PREVIEW =
            RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER, RuntimeRole.RUNTIME_SWEEP_REVIEW);
    private static final RuntimeRoleSet SWEEP_APPLY =
            RuntimeRoleSet.of(RuntimeRole.RUNTIME_EXECUTOR, RuntimeRole.RUNTIME_SWEEP_APPLY);
    private static final RuntimeRoleSet FORCE_RUN =
            RuntimeRoleSet.of(RuntimeRole.RUNTIME_EXECUTOR, RuntimeRole.RUNTIME_FORCE_RUN);
    private static final AuthorizationPolicyFingerprint FINGERPRINT = fingerprintPolicy();
    private static final RuntimeAuthorizationPolicy STANDARD = new RuntimeAuthorizationPolicy();

    private RuntimeAuthorizationPolicy() {}

    public static RuntimeAuthorizationPolicy standard() {
        return STANDARD;
    }

    public String version() {
        return VERSION;
    }

    public AuthorizationPolicyFingerprint fingerprint() {
        return FINGERPRINT;
    }

    public boolean authorizes(final RuntimeAction action, final RuntimeRoleSet grantedRoles) {
        return Objects.requireNonNull(grantedRoles, "Os papéis concedidos são obrigatórios.")
                .containsAll(requiredRoles(action));
    }

    public boolean requires(final RuntimeAction action, final RuntimeRole role) {
        return requiredRoles(action)
                .has(Objects.requireNonNull(role, "O papel consultado é obrigatório."));
    }

    public int requiredRoleCount(final RuntimeAction action) {
        return requiredRoles(action).size();
    }

    private static RuntimeRoleSet requiredRoles(final RuntimeAction action) {
        return switch (Objects.requireNonNull(action, "A ação de runtime é obrigatória.")) {
            case RUN -> EXECUTOR;
            case REPLAY -> REPLAY;
            case SWEEP_PREVIEW -> SWEEP_PREVIEW;
            case SWEEP_APPLY -> SWEEP_APPLY;
            case FORCE_RUN -> FORCE_RUN;
            case STATUS -> OBSERVER;
        };
    }

    private static AuthorizationPolicyFingerprint fingerprintPolicy() {
        final StringBuilder canonical = new StringBuilder(VERSION);
        for (final RuntimeAction action : RuntimeAction.values()) {
            canonical.append(';').append(action.name()).append('=');
            for (final RuntimeRole role : RuntimeRole.values()) {
                if (requiredRoles(action).has(role)) {
                    canonical.append(role.name()).append(',');
                }
            }
        }
        try {
            final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return new AuthorizationPolicyFingerprint(
                    HexFormat.of()
                            .formatHex(
                                    sha256.digest(
                                            canonical
                                                    .toString()
                                                    .getBytes(StandardCharsets.UTF_8))));
        } catch (final NoSuchAlgorithmException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Override
    public String toString() {
        return "RuntimeAuthorizationPolicy[version=" + VERSION + "]";
    }
}
