package br.com.esl.etl.v2.plataforma.autorizacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeAuthorizationPolicyTest {

    private final RuntimeAuthorizationPolicy policy = RuntimeAuthorizationPolicy.standard();

    @Test
    void exposesTheExactRoleMatrixWithoutImplicitHierarchy() {
        assertRequired(RuntimeAction.RUN, RuntimeRole.RUNTIME_EXECUTOR);
        assertRequired(
                RuntimeAction.REPLAY, RuntimeRole.RUNTIME_EXECUTOR, RuntimeRole.RUNTIME_REPLAY);
        assertRequired(
                RuntimeAction.SWEEP_PREVIEW,
                RuntimeRole.RUNTIME_OBSERVER,
                RuntimeRole.RUNTIME_SWEEP_REVIEW);
        assertRequired(
                RuntimeAction.SWEEP_APPLY,
                RuntimeRole.RUNTIME_EXECUTOR,
                RuntimeRole.RUNTIME_SWEEP_APPLY);
        assertRequired(
                RuntimeAction.FORCE_RUN,
                RuntimeRole.RUNTIME_EXECUTOR,
                RuntimeRole.RUNTIME_FORCE_RUN);
        assertRequired(RuntimeAction.STATUS, RuntimeRole.RUNTIME_OBSERVER);

        assertFalse(
                policy.authorizes(
                        RuntimeAction.RUN, RuntimeRoleSet.of(RuntimeRole.RUNTIME_OBSERVER)));
        assertFalse(
                policy.authorizes(
                        RuntimeAction.STATUS, RuntimeRoleSet.of(RuntimeRole.RUNTIME_EXECUTOR)));
        assertFalse(
                policy.authorizes(
                        RuntimeAction.REPLAY, RuntimeRoleSet.of(RuntimeRole.RUNTIME_REPLAY)));
        assertFalse(
                policy.authorizes(
                        RuntimeAction.SWEEP_PREVIEW,
                        RuntimeRoleSet.of(RuntimeRole.RUNTIME_SWEEP_REVIEW)));
        assertFalse(
                policy.authorizes(
                        RuntimeAction.SWEEP_APPLY,
                        RuntimeRoleSet.of(RuntimeRole.RUNTIME_SWEEP_APPLY)));
        assertFalse(
                policy.authorizes(
                        RuntimeAction.FORCE_RUN, RuntimeRoleSet.of(RuntimeRole.RUNTIME_FORCE_RUN)));
    }

    @Test
    void hasNoAdminRoleAndNoMigrationOrCutoverAction() {
        for (final RuntimeRole role : RuntimeRole.values()) {
            assertFalse(role.name().toUpperCase(Locale.ROOT).contains("ADMIN"));
        }
        final Set<String> actionNames =
                Set.of(
                        RuntimeAction.RUN.name(),
                        RuntimeAction.REPLAY.name(),
                        RuntimeAction.SWEEP_PREVIEW.name(),
                        RuntimeAction.SWEEP_APPLY.name(),
                        RuntimeAction.FORCE_RUN.name(),
                        RuntimeAction.STATUS.name());

        assertEquals(6, RuntimeAction.values().length);
        assertEquals(6, actionNames.size());
        assertFalse(actionNames.contains("MIGRATE"));
        assertFalse(actionNames.contains("CUTOVER"));
    }

    @Test
    void keepsThePolicyVersionedDeterministicAndItsRenderingSanitized() {
        final String fingerprint = policy.fingerprint().sha256();

        assertSame(policy, RuntimeAuthorizationPolicy.standard());
        assertEquals("runtime-rbac-v1", policy.version());
        assertEquals(64, fingerprint.length());
        assertEquals(policy.fingerprint(), RuntimeAuthorizationPolicy.standard().fingerprint());
        assertFalse(policy.toString().contains(fingerprint));
    }

    @Test
    void rejectsNullPolicyInputs() {
        assertThrows(
                NullPointerException.class, () -> policy.authorizes(null, RuntimeRoleSet.none()));
        assertThrows(NullPointerException.class, () -> policy.authorizes(RuntimeAction.RUN, null));
        assertThrows(NullPointerException.class, () -> policy.requires(RuntimeAction.RUN, null));
    }

    @Test
    void roleSetIsImmutableDeduplicatedAndNonEnumerating() {
        final RuntimeRole[] source = {
            RuntimeRole.RUNTIME_EXECUTOR, RuntimeRole.RUNTIME_EXECUTOR, RuntimeRole.RUNTIME_REPLAY
        };
        final RuntimeRoleSet roles = RuntimeRoleSet.of(source);
        source[0] = RuntimeRole.RUNTIME_OBSERVER;

        assertEquals(2, roles.size());
        assertTrue(roles.has(RuntimeRole.RUNTIME_EXECUTOR));
        assertFalse(roles.has(RuntimeRole.RUNTIME_OBSERVER));
        assertEquals(
                RuntimeRoleSet.of(RuntimeRole.RUNTIME_REPLAY, RuntimeRole.RUNTIME_EXECUTOR), roles);
        assertEquals(
                RuntimeRoleSet.of(RuntimeRole.RUNTIME_EXECUTOR, RuntimeRole.RUNTIME_REPLAY)
                        .hashCode(),
                roles.hashCode());
        assertEquals(0, RuntimeRoleSet.none().size());
        assertEquals("RuntimeRoleSet[redacted]", roles.toString());
        assertThrows(NullPointerException.class, () -> roles.has(null));
        assertThrows(NullPointerException.class, () -> RuntimeRoleSet.of((RuntimeRole[]) null));
        assertThrows(
                NullPointerException.class,
                () -> RuntimeRoleSet.of(RuntimeRole.RUNTIME_EXECUTOR, null));
        assertNotEquals(roles, "not-a-role-set");
    }

    private void assertRequired(final RuntimeAction action, final RuntimeRole... expectedRoles) {
        final EnumSet<RuntimeRole> expected = EnumSet.noneOf(RuntimeRole.class);
        for (final RuntimeRole role : expectedRoles) {
            expected.add(role);
        }
        final RuntimeRoleSet granted = RuntimeRoleSet.of(expectedRoles);

        assertTrue(policy.authorizes(action, granted));
        assertEquals(expected.size(), policy.requiredRoleCount(action));
        for (final RuntimeRole role : RuntimeRole.values()) {
            assertEquals(expected.contains(role), policy.requires(action, role));
        }
    }
}
