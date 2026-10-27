package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCapturePlan;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedStagingFacade;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.UUID;

/** Optional fixed external API. Resolving its name never creates or supplies Root authority. */
final class ObservedStagingTrialBridge {
    private static final String WORKFLOW =
            "br.com.esl.etl.v2.plataforma.persistencia.observed.P3RootPhysicalWorkflow";
    private static final String OWNER =
            "br.com.esl.etl.v2.plataforma.persistencia.observed.P3PhysicalTrialOwner";
    private static final Set<String> WORKFLOW_STATUSES =
            Set.of(
                    "REQUEST_BINDING_REQUIRED",
                    "REQUEST_MISMATCH",
                    "ROOT_PHYSICAL_SCOPE_PENDING",
                    "SOURCE_OR_PROOF_REFUSED",
                    "SQL_OWNER_RETURNED");
    private static final Set<String> OWNER_STATUSES =
            Set.of(
                    "ROOT_RELEASE_PENDING",
                    "INPUT_REFUSED",
                    "CONNECT_REFUSED",
                    "WORK_REFUSED",
                    "READBACK_AND_ROLLBACK_OBSERVED",
                    "CLEANUP_UNCERTAIN");

    private ObservedStagingTrialBridge() {}

    /** Uses this product's loader and one fixed public signature; no caller class/path/SPI. */
    static Binding locate() {
        try {
            final var type =
                    Class.forName(
                            WORKFLOW, false, ObservedStagingTrialBridge.class.getClassLoader());
            final var method =
                    type.getDeclaredMethod(
                            "runRootUnit",
                            UUID.class,
                            ObservedExpansionCapturePlan.class,
                            String.class);
            if (!Modifier.isPublic(method.getModifiers())
                    || !Modifier.isStatic(method.getModifiers())
                    || !method.getReturnType().getName().equals(WORKFLOW + "$Observation")) {
                return null;
            }
            return new Binding(method);
        } catch (final ReflectiveOperationException | LinkageError | SecurityException absent) {
            return null;
        }
    }

    record Result(int exit, String technicalJson) {}

    static final class Binding {
        private final Method run;

        private Binding(final Method run) {
            this.run = run;
        }

        Result invoke(final ObservedStagingFacade.Prepared prepared) {
            try {
                // Exact Prepared plan reference and the kernel's effective configuration
                // fingerprint.
                // The external method compares both before its own CODE
                // reservation/provider/effects.
                final Object observation =
                        run.invoke(
                                null,
                                prepared.plan().captureExecutionId(),
                                prepared.plan(),
                                prepared.configurationSha256());
                return describe(observation, run.getReturnType());
            } catch (final ReflectiveOperationException
                    | RuntimeException
                    | LinkageError uncertain) {
                // A thrown invocation can have effects. Unknown counts/close never become
                // zero/known.
                final var json = base("ROOT_WORKFLOW_RESULT_UNAVAILABLE");
                json.put("workflowResultValidated", false);
                json.put("countsKnown", false);
                json.putNull("infoAttempts");
                json.putNull("dataAttempts");
                json.putNull("sqlConnectionAttempted");
                json.put("readbackAndRollbackObserved", false);
                json.put("sqlCleanupKnown", false);
                return new Result(RuntimeExitCategory.DEGRADED.code(), json.toString());
            }
        }
    }

    private static Result describe(final Object observation, final Class<?> expected)
            throws ReflectiveOperationException {
        require(observation != null && observation.getClass() == expected);
        final String status =
                enumeration(observation, "status", WORKFLOW + "$Status", WORKFLOW_STATUSES);
        final int info = integer(observation, "infoAttempts", 1);
        final int data = integer(observation, "dataAttempts", 4);
        final Object owner = field(observation, "sqlOwner");
        final boolean beforeEffects =
                status.equals("REQUEST_BINDING_REQUIRED")
                        || status.equals("REQUEST_MISMATCH")
                        || status.equals("ROOT_PHYSICAL_SCOPE_PENDING");
        require(!beforeEffects || info == 0 && data == 0 && owner == null);
        require(status.equals("SQL_OWNER_RETURNED") == (owner != null));
        require(!status.equals("SQL_OWNER_RETURNED") || info == 1 && data >= 1);
        final var json = base(status);
        json.put("workflowResultValidated", true);
        json.put("countsKnown", true);
        json.put("infoAttempts", info);
        json.put("dataAttempts", data);
        if (owner == null) {
            if (beforeEffects) {
                json.put("sqlConnectionAttempted", false);
            } else {
                json.putNull("sqlConnectionAttempted");
            }
            json.putNull("sqlOwnerStatus");
            json.put("readbackAndRollbackObserved", false);
            json.put("sqlCleanupKnown", false);
            return new Result(
                    beforeEffects
                            ? RuntimeExitCategory.CONFIG_AUTH.code()
                            : RuntimeExitCategory.DEGRADED.code(),
                    json.toString());
        }
        require(
                owner.getClass().getName().equals(OWNER + "$Observation")
                        && owner.getClass().getClassLoader() == expected.getClassLoader());
        final String ownerStatus = enumeration(owner, "status", OWNER + "$Status", OWNER_STATUSES);
        final boolean modelOnly = bool(owner, "modelOnly");
        final boolean connectionAttempted = bool(owner, "connectionAttempted");
        final boolean connectCleanupUncertain = bool(owner, "connectCleanupUncertain");
        final boolean readback = bool(owner, "exactReadback");
        final boolean rollback = bool(owner, "originalRollback");
        final boolean headerAbsent = bool(owner, "headerAbsent");
        final boolean pageAbsent = bool(owner, "pageAbsent");
        final boolean closed = bool(owner, "connectionClosed");
        final boolean cleanup = bool(owner, "cleanupKnown");
        final int reads = integer(owner, "catalogReads", 7);
        final int readsAttempted = integer(owner, "catalogReadsAttempted", 7);
        final int readsCompleted = integer(owner, "catalogReadsCompleted", 7);
        require(readsCompleted <= readsAttempted);
        json.put("sqlOwnerStatus", ownerStatus);
        json.put("modelOnly", modelOnly);
        json.put("sqlConnectionAttempted", connectionAttempted);
        json.put("connectCleanupUncertain", connectCleanupUncertain);
        json.put("catalogReadsAttempted", readsAttempted);
        json.put("catalogReadsCompleted", readsCompleted);
        json.put("catalogReads", reads);
        json.put("commandsReserved", integer(owner, "commandsReserved", 96));
        json.put("exactReadback", readback);
        json.put("originalRollback", rollback);
        json.put("headerAbsent", headerAbsent);
        json.put("pageAbsent", pageAbsent);
        json.put("sqlConnectionClosed", closed);
        json.put("sqlCleanupKnown", cleanup);
        final boolean observed =
                ownerStatus.equals("READBACK_AND_ROLLBACK_OBSERVED")
                        && !modelOnly
                        && connectionAttempted
                        && !connectCleanupUncertain
                        && reads == 7
                        && readsAttempted == 7
                        && readsCompleted == 7
                        && readback
                        && rollback
                        && headerAbsent
                        && pageAbsent
                        && closed
                        && cleanup;
        json.put("readbackAndRollbackObserved", observed);
        final int exit =
                observed
                        ? 0
                        : ownerStatus.equals("ROOT_RELEASE_PENDING")
                                ? RuntimeExitCategory.CONFIG_AUTH.code()
                                : RuntimeExitCategory.DEGRADED.code();
        return new Result(exit, json.toString());
    }

    private static ObjectNode base(final String status) {
        final var json = JsonNodeFactory.instance.objectNode();
        json.put("version", "observed-staging-trial-v1");
        json.put("mode", "ROLLBACK_TRIAL");
        json.put("status", status);
        json.put("physicalAuthorityGranted", false);
        json.put("globalCompletenessProven", false);
        json.put("domainIdentityProven", false);
        json.put("retainedData", false);
        json.put("publicationProven", false);
        json.put("rootJobClosureProven", false);
        json.put("sourceTraversalOutcome", "NOT_REPORTED_BY_ROOT_API");
        json.put("goalComplete", false);
        return json;
    }

    private static Object field(final Object instance, final String name)
            throws ReflectiveOperationException {
        return instance.getClass().getDeclaredMethod(name).invoke(instance);
    }

    private static int integer(final Object instance, final String name, final int maximum)
            throws ReflectiveOperationException {
        final Object value = field(instance, name);
        require(value instanceof Integer && (Integer) value >= 0 && (Integer) value <= maximum);
        return (Integer) value;
    }

    private static boolean bool(final Object instance, final String name)
            throws ReflectiveOperationException {
        final Object value = field(instance, name);
        require(value instanceof Boolean);
        return (Boolean) value;
    }

    private static String enumeration(
            final Object instance,
            final String name,
            final String expected,
            final Set<String> allowed)
            throws ReflectiveOperationException {
        final Object value = field(instance, name);
        require(value instanceof Enum<?>);
        final var enumeration = (Enum<?>) value;
        require(
                enumeration.getDeclaringClass().getName().equals(expected)
                        && enumeration.getDeclaringClass().getClassLoader()
                                == instance.getClass().getClassLoader()
                        && allowed.contains(enumeration.name()));
        return enumeration.name();
    }

    private static void require(final boolean accepted) {
        if (!accepted) {
            throw new IllegalStateException("OBSERVED_STAGING_ROOT_RESULT_INVALID");
        }
    }
}
