package br.com.esl.etl.v2.bootstrap;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Small allowlist CLI; all rejected input is reported through fixed reason codes. */
record DevLocalMainArguments(
        String command,
        Path workspace,
        Path config,
        String executionId,
        String mode,
        String previousExecution,
        String scenarioCase,
        String inputSha256) {
    private static final Set<String> COMMANDS =
            Set.of(
                    "init",
                    "run",
                    "status",
                    "replay",
                    "cancel",
                    "error",
                    "stop",
                    "scenario",
                    "reconcile-start");

    static DevLocalMainArguments parse(final String[] args) {
        if (args == null || args.length < 4 || args.length > 12 || !COMMANDS.contains(args[0])) {
            throw new IllegalArgumentException("DEV_LOCAL_CLI_INVALID");
        }
        boolean synthetic = false;
        Path workspace = null;
        Path config = null;
        String executionId = null;
        String mode = "BOOTSTRAP";
        String previousExecution = null;
        String scenarioCase = null;
        String inputSha256 = null;
        final var seen = new HashSet<String>();
        for (int i = 1; i < args.length; i++) {
            final var flag = args[i];
            if (!seen.add(flag)) {
                throw new IllegalArgumentException("DEV_LOCAL_CLI_DUPLICATE");
            }
            if ("--synthetic".equals(flag)) {
                synthetic = true;
                continue;
            }
            if (!Set.of(
                                    "--workspace",
                                    "--config",
                                    "--execution",
                                    "--mode",
                                    "--previous",
                                    "--case",
                                    "--input-sha256")
                            .contains(flag)
                    || ++i >= args.length
                    || args[i].isBlank()
                    || args[i].length() > 512) {
                throw new IllegalArgumentException("DEV_LOCAL_CLI_INVALID");
            }
            switch (flag) {
                case "--workspace" -> workspace = localPath(args[i]);
                case "--config" -> config = localPath(args[i]);
                case "--execution" -> executionId = identity(args[i]);
                case "--mode" -> mode = args[i];
                case "--previous" -> previousExecution = identity(args[i]);
                case "--case" -> scenarioCase = args[i];
                case "--input-sha256" -> inputSha256 = args[i];
                default -> throw new IllegalArgumentException("DEV_LOCAL_CLI_INVALID");
            }
        }
        if (!synthetic) {
            throw new IllegalArgumentException("DEV_LOCAL_SYNTHETIC_REQUIRED");
        }
        final boolean reconcile = "reconcile-start".equals(args[0]);
        if (reconcile) {
            if (executionId == null
                    || inputSha256 == null
                    || !inputSha256.matches("[a-f0-9]{64}")
                    || config != null
                    || seen.contains("--mode")
                    || previousExecution != null
                    || scenarioCase != null) {
                throw new IllegalArgumentException("DEV_LOCAL_CLI_INVALID");
            }
            if ("workspace-init".equalsIgnoreCase(executionId)) {
                throw new IllegalArgumentException("DEV_LOCAL_EXECUTION_RESERVED");
            }
        } else if (inputSha256 != null) {
            throw new IllegalArgumentException("DEV_LOCAL_CLI_INVALID");
        }
        if (workspace == null
                || ("init".equals(args[0]) && executionId != null)
                || ("replay".equals(args[0]) && executionId == null)
                || !Set.of("BOOTSTRAP", "INCREMENTAL", "RECOMPOSE").contains(mode)
                || (!"run".equals(args[0])
                        && (seen.contains("--mode") || previousExecution != null))
                || (!"BOOTSTRAP".equals(mode) && previousExecution == null)
                || ("scenario".equals(args[0])
                        ? executionId == null
                                || executionId.length() > 60
                                || scenarioCase == null
                                || !Set.of(
                                                "ABSENCE_REACTIVATION",
                                                "PARTIAL_REFUSED",
                                                "QUARANTINE_REFUSED",
                                                "OWNER_REFUSED")
                                        .contains(scenarioCase)
                        : scenarioCase != null)) {
            throw new IllegalArgumentException("DEV_LOCAL_CLI_INVALID");
        }
        return new DevLocalMainArguments(
                args[0],
                workspace,
                config,
                executionId,
                mode,
                previousExecution,
                scenarioCase,
                inputSha256);
    }

    private static Path localPath(final String value) {
        if (value.startsWith("\\\\")
                || value.startsWith("//")
                || value.indexOf('\0') >= 0
                || value.contains("://")
                || value.substring(Math.min(2, value.length())).contains(":")) {
            throw new IllegalArgumentException("DEV_LOCAL_LOCAL_PATH_REQUIRED");
        }
        final var path = Path.of(value).toAbsolutePath().normalize();
        if (path.getParent() == null) {
            throw new IllegalArgumentException("DEV_LOCAL_LOCAL_PATH_REQUIRED");
        }
        return path;
    }

    private static String identity(final String value) {
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) {
            throw new IllegalArgumentException("DEV_LOCAL_EXECUTION_ID_REQUIRED");
        }
        return value;
    }
}
