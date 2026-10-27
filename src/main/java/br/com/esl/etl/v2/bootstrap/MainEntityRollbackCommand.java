package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;

/** Fixed V6 rollback handoff. Public paths remain subject to the existing Root CODE gates. */
final class MainEntityRollbackCommand {
    private static final String ENTRY = "br.com.esl.etl.v2.bootstrap.RootBankV13EntryTemplate";
    private static final Set<String> PROFILES = Set.of("COLETAS6908", "MFT6399", "COT6906");
    private static final Set<String> BEFORE_READY =
            Set.of("INPUT", "PROFILE", "SUBJECT", "CODE", "ADMISSION", "RESERVATION");

    private MainEntityRollbackCommand() {}

    static int run(final String[] args, final PrintStream out, final PrintStream err) {
        if (args == null
                || args.length != 17
                || !"entity-rollback".equals(args[0])
                || !"--profile".equals(args[1])
                || !PROFILES.contains(args[2])
                || !"--root-v6".equals(args[3])
                || !"--run".equals(args[4])) {
            err.println("ENTITY_ROLLBACK_CLOSED_CLI_REQUIRED");
            return RuntimeExitCategory.CONFIG_AUTH.code();
        }
        final java.lang.reflect.Method method;
        try {
            final var type =
                    Class.forName(ENTRY, false, MainEntityRollbackCommand.class.getClassLoader());
            method = type.getDeclaredMethod("runMainTrial", String.class, String[].class);
            if (!Modifier.isPublic(method.getModifiers())
                    || !Modifier.isStatic(method.getModifiers())
                    || !method.getReturnType().getName().equals(ENTRY + "$MainTrialObservation")) {
                throw new NoSuchMethodException("FIXED_API_REQUIRED");
            }
        } catch (final ReflectiveOperationException
                | LinkageError
                | SecurityException unavailable) {
            err.println("ENTITY_ROLLBACK_GENUINE_V6_API_NOT_BOUND");
            return RuntimeExitCategory.CONFIG_AUTH.code();
        }
        try {
            final Object observation =
                    method.invoke(null, args[2], Arrays.copyOfRange(args, 4, 17));
            final Class<?> expected = method.getReturnType();
            require(observation != null && observation.getClass() == expected);
            final Object profile = expected.getDeclaredMethod("profile").invoke(observation);
            final Object exit = expected.getDeclaredMethod("exit").invoke(observation);
            final Object stage = expected.getDeclaredMethod("stage").invoke(observation);
            final Object ready = expected.getDeclaredMethod("readyPublished").invoke(observation);
            require(
                    args[2].equals(profile)
                            && exit instanceof Integer
                            && stage instanceof String
                            && ready instanceof Boolean);
            final int code = (Integer) exit;
            final String phase = (String) stage;
            final boolean published = (Boolean) ready;
            require(
                    (code == 0 || code == 1)
                            && (published
                                    ? Set.of("PHYSICAL", "RETURNED").contains(phase)
                                    : BEFORE_READY.contains(phase))
                            && (code != 0 || published && "RETURNED".equals(phase)));
            final var json = base("V6_RESULT_RETURNED");
            json.put("apiResultValidated", true);
            json.put("profile", args[2]);
            json.put("stage", phase);
            json.put("exit", code);
            json.put("readyPublished", published);
            json.put("countsKnown", !published);
            if (published) {
                json.putNull("sourceHttp");
                json.putNull("sqlConnections");
            } else {
                json.put("sourceHttp", 0);
                json.put("sqlConnections", 0);
            }
            out.println(json);
            return code;
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError uncertain) {
            // The invocation may have crossed READY. No zero count or cleanup inference is valid.
            final var json = base("V6_INVOCATION_UNCERTAIN");
            json.put("apiResultValidated", false);
            json.put("countsKnown", false);
            json.putNull("sourceHttp");
            json.putNull("sqlConnections");
            json.putNull("readyPublished");
            out.println(json);
            return RuntimeExitCategory.DEGRADED.code();
        }
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode base(final String status) {
        final var json = JsonNodeFactory.instance.objectNode();
        json.put("schema", "MAIN_ENTITY_V6_ROLLBACK_HANDOFF_V1");
        json.put("status", status);
        json.put("physicalAuthorityGranted", false);
        json.put("physicalQualificationProven", false);
        json.put("publicationProven", false);
        json.put("rootJobClosureProven", false);
        json.put("cleanupKnown", false);
        json.put("retainedCommit", false);
        json.put("newProductPhysicalQualificationStillRequired", true);
        json.put("executionEntry", "FIXED_V6_RETURNING_API");
        json.put("mainInvocationProven", false);
        return json;
    }

    private static void require(final boolean value) {
        if (!value) {
            throw new IllegalStateException("ENTITY_ROLLBACK_API_RESULT_INVALID");
        }
    }
}
