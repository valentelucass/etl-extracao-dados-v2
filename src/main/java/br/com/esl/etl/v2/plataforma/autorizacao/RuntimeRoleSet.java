package br.com.esl.etl.v2.plataforma.autorizacao;

import java.util.EnumSet;
import java.util.Objects;

/** Conjunto imutável e deliberadamente não enumerável de papéis internos verificados. */
public final class RuntimeRoleSet {

    private final EnumSet<RuntimeRole> roles;

    private RuntimeRoleSet(final EnumSet<RuntimeRole> roles) {
        this.roles = roles.clone();
    }

    public static RuntimeRoleSet none() {
        return new RuntimeRoleSet(EnumSet.noneOf(RuntimeRole.class));
    }

    public static RuntimeRoleSet of(final RuntimeRole... roles) {
        Objects.requireNonNull(roles, "Os papéis de runtime são obrigatórios.");
        final EnumSet<RuntimeRole> copy = EnumSet.noneOf(RuntimeRole.class);
        for (final RuntimeRole role : roles) {
            copy.add(Objects.requireNonNull(role, "Um papel de runtime não pode ser nulo."));
        }
        return new RuntimeRoleSet(copy);
    }

    public boolean has(final RuntimeRole role) {
        return roles.contains(Objects.requireNonNull(role, "O papel consultado é obrigatório."));
    }

    public int size() {
        return roles.size();
    }

    boolean containsAll(final RuntimeRoleSet requiredRoles) {
        return roles.containsAll(
                Objects.requireNonNull(requiredRoles, "Os papéis exigidos são obrigatórios.")
                        .roles);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RuntimeRoleSet that && roles.equals(that.roles);
    }

    @Override
    public int hashCode() {
        return roles.hashCode();
    }

    @Override
    public String toString() {
        return "RuntimeRoleSet[redacted]";
    }
}
