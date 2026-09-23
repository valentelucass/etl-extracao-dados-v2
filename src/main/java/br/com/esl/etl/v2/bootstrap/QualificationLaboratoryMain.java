package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationPlanner;
import br.com.esl.etl.v2.plataforma.qualificacao.QualifiedPackage;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fixed opt-in entrypoint carried by the verified package; the ordinary Main remains unchanged. */
public final class QualificationLaboratoryMain {
    private QualificationLaboratoryMain() {}

    public static void main(final String[] arguments) {
        int exit;
        try {
            exit = execute(arguments);
        } catch (final Exception failure) {
            final String message = failure.getMessage();
            final String code =
                    message != null && message.matches("[A-Z][A-Z0-9_]{1,80}")
                            ? message
                            : "QUAL_COMMAND_REFUSED";
            System.err.println(
                    JsonNodeFactory.instance
                            .objectNode()
                            .put("state", "REFUSED")
                            .put("code", code)
                            .put("failureClass", failure.getClass().getSimpleName()));
            exit = 2;
        }
        if (exit != 0) {
            System.exit(exit);
        }
    }

    static int execute(final String[] arguments) throws Exception {
        if (arguments.length == 0) {
            System.out.println("QUALIFICATION_EXPLICIT_COMMAND_REQUIRED");
            return 2;
        }
        final String command = arguments[0];
        if (!Set.of("inspect", "plan", "run", "status", "resume", "compare", "worker")
                .contains(command)) {
            throw new IllegalArgumentException("QUAL_COMMAND_INVALID");
        }
        final Map<String, String> options = new HashMap<>();
        for (int index = 1; index < arguments.length; index++) {
            final String argument = arguments[index];
            final int equals = argument.indexOf('=');
            if (!argument.startsWith("--")
                    || equals < 3
                    || argument.length() > 4096
                    || options.put(argument.substring(2, equals), argument.substring(equals + 1))
                            != null) {
                throw new IllegalArgumentException("QUAL_COMMAND_ARGUMENT");
            }
        }
        final Set<String> allowed =
                switch (command) {
                    case "inspect" -> Set.of("manifest-sha");
                    case "plan" -> Set.of("manifest-sha", "campaign", "configuration");
                    case "run" -> Set.of("manifest-sha", "campaign", "configuration", "control");
                    case "worker" -> Set.of("manifest-sha", "control", "case", "nonce");
                    default -> Set.of("manifest-sha", "control");
                };
        if (!allowed.containsAll(options.keySet())) {
            throw new IllegalArgumentException("QUAL_COMMAND_EXTRA_ARGUMENT");
        }
        final var jar =
                Path.of(
                        QualificationLaboratoryMain.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        final var payload =
                QualifiedPackage.verify(jar.getParent(), required(options, "manifest-sha"));
        payload.verifyRunningArtifact(QualificationLaboratoryMain.class);
        if (command.equals("inspect")) {
            System.out.println(
                    JsonNodeFactory.instance
                            .objectNode()
                            .put("state", "PACKAGE_VERIFIED")
                            .put("revision", payload.revision())
                            .put("members", payload.members().size()));
            return 0;
        }
        final boolean input = command.equals("plan") || command.equals("run");
        final Path control = command.equals("plan") ? null : Path.of(required(options, "control"));
        final QualificationControlFiles existing =
                input ? null : new QualificationControlFiles(payload.root(), control, false);
        final var campaignPath =
                input
                        ? Path.of(required(options, "campaign"))
                        : existing.root().resolve("campaign.json");
        final var configurationPath =
                input
                        ? options.containsKey("configuration")
                                ? Path.of(options.get("configuration"))
                                : payload.member("config/config.synthetic.json", "CONFIGURATION")
                        : existing.root().resolve("configuration.json");
        final var campaignDocument = QualificationJson.read(campaignPath, 131072);
        final var configurationDocument = QualificationJson.read(configurationPath, 8192);
        final var campaign = QualificationCampaign.parse(campaignDocument);
        final var configuration = QualificationConfiguration.parse(configurationDocument);
        payload.verifyPins(campaign.pins());
        if (campaign.maximumSeconds() > configuration.campaignSeconds()) {
            throw new IllegalArgumentException("QUAL_CAMPAIGN_BUDGET");
        }
        if (command.equals("plan")) {
            final var report = JsonNodeFactory.instance.objectNode().put("campaign", campaign.id());
            final var cases = report.putArray("cases");
            for (final var item : campaign.cases()) {
                final var plan = QualificationPlanner.plan(item);
                final var node =
                        cases.addObject()
                                .put("case", item.id())
                                .put("wave", item.wave())
                                .put("state", plan.state().name())
                                .put("reason", plan.reason())
                                .put("backlog", plan.backlog());
                final var windows = node.putArray("windows");
                plan.windows()
                        .forEach(
                                window ->
                                        windows.addObject()
                                                .put("start", window.partitionStart().toString())
                                                .put(
                                                        "endExclusive",
                                                        window.endExclusive().toString())
                                                .put(
                                                        "extractionStart",
                                                        window.extractionStart().toString())
                                                .put("deadline", window.deadlineAt().toString()));
            }
            System.out.println(report);
            return 0;
        }
        if (command.equals("worker")) {
            final var item =
                    campaign.cases().stream()
                            .filter(value -> value.id().equals(required(options, "case")))
                            .findFirst()
                            .orElseThrow();
            return QualificationWorker.run(
                    payload,
                    campaign,
                    configuration,
                    existing,
                    item,
                    UUID.fromString(required(options, "nonce")));
        }
        final var files =
                command.equals("run")
                        ? new QualificationControlFiles(payload.root(), control, true)
                        : existing;
        if (command.equals("run")) {
            QualificationControlFiles.atomic(
                    QualificationControlFiles.member(files.root(), "campaign.json"),
                    campaignDocument);
            QualificationControlFiles.atomic(
                    QualificationControlFiles.member(files.root(), "configuration.json"),
                    configurationDocument);
        }
        final var supervisor =
                new QualificationSupervisor(
                        payload, campaign, configuration, files, command.equals("run"));
        final var result =
                switch (command) {
                    case "run" -> supervisor.execute();
                    case "resume" -> supervisor.resume();
                    default -> supervisor.status();
                };
        System.out.println(result);
        return result.path("exit").intValue();
    }

    private static String required(final Map<String, String> options, final String key) {
        final var value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("QUAL_COMMAND_REQUIRED_ARGUMENT");
        }
        return value;
    }
}
