package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Set;

/**
 * Four closed synthetic scenarios, with actual persisted witnesses and guarded Engine transitions.
 */
public final class DevLocalEngineScenario {
    private DevLocalEngineScenario() {}

    public static JsonNode execute(
            final DevLocalLedger ledger,
            final String prefix,
            final JsonNode dataset,
            final JsonNode configuration,
            final String scenario)
            throws IOException {
        if (prefix == null
                || !prefix.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,59}")
                || !Set.of(
                                "ABSENCE_REACTIVATION",
                                "PARTIAL_REFUSED",
                                "QUARANTINE_REFUSED",
                                "OWNER_REFUSED")
                        .contains(scenario)) {
            throw new IllegalArgumentException("DEV_LOCAL_SCENARIO_CONTRACT");
        }
        final ObjectNode identity = DevLocalEngine.JSON.createObjectNode();
        identity.set("dataset", dataset);
        identity.set("configuration", configuration);
        identity.put("scenario", scenario);
        final var parent =
                ledger.start(prefix, DevLocalLedger.sha256(DevLocalEngine.bytes(identity)));
        if (parent.state() != DevLocalLedger.State.STARTED) {
            final JsonNode replay =
                    DevLocalEngine.JSON.readTree(ledger.readOutput(prefix, "result.json"));
            if (!parent.state().name().equals(replay.path("status").asText())
                    || !parent.reasonCode().equals(replay.path("reasonCode").asText())) {
                throw new IOException("DEV_LOCAL_SCENARIO_REPLAY_TERMINAL_MISMATCH");
            }
            return replay;
        }
        ledger.event(prefix, "SCENARIO_BEGIN", "SYNTHETIC_LOCAL_ONLY");
        final ObjectNode report = DevLocalEngine.JSON.createObjectNode();
        report.put("synthetic", true);
        report.put("identityScope", DevLocalEngine.SCOPE);
        report.put("scenario", scenario);
        report.put("published", false);
        report.put("physicalCompletenessProven", false);
        final ArrayNode runs = report.putArray("runs");
        final ObjectNode checks = report.putObject("checks");
        final ObjectNode clean = clean(dataset);
        final JsonNode initial =
                child(ledger, prefix + "-clean", "CLEAN", clean, configuration, runs);
        final byte[] beforeOutput = ledger.readOutput(prefix + "-clean", "result.json");
        final String beforeState =
                DevLocalLedger.sha256(DevLocalEngine.bytes(initial.path("state")));
        final ObjectNode absent = clean.deepCopy();
        final ObjectNode users = entity(absent, "Users");
        if (users.path("rows").isEmpty()) {
            throw new IllegalArgumentException("DEV_LOCAL_SCENARIO_USERS_REQUIRED");
        }
        ((ArrayNode) users.path("rows")).remove(0);
        users.put("inputRows", users.path("rows").size());
        users.put("accepted", users.path("rows").size());
        final ObjectNode request = configuration.deepCopy();
        request.put("mode", "INCREMENTAL");
        request.set("previousState", initial.path("state"));
        if ("ABSENCE_REACTIVATION".equals(scenario)) {
            final ArrayNode witnesses =
                    witnesses(ledger, prefix + "-witness", absent, clean, configuration, runs);
            request.set("sweep", sweep(absent, witnesses));
            final JsonNode removed =
                    child(ledger, prefix + "-absent", "ABSENCE", absent, request, runs);
            final int expected = configuration.path("scale").asInt(2);
            require(
                    removed.path("absence").path("deactivated").asInt() == expected,
                    "DEV_LOCAL_SCENARIO_ABSENCE_COUNT");
            request.set("previousState", removed.path("state"));
            final ArrayNode reappearance = witnesses.deepCopy();
            for (final JsonNode proof : reappearance) {
                ((ObjectNode) proof)
                        .put(
                                "occurrence",
                                "return-"
                                        + DevLocalLedger.sha256(
                                                proof.path("run")
                                                        .asText()
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8)));
            }
            final ObjectNode reactivation = sweep(clean, reappearance);
            reactivation.put("apply", false);
            reactivation.put("reactivate", true);
            request.set("sweep", reactivation);
            final JsonNode restored =
                    child(ledger, prefix + "-reactivated", "REACTIVATION", clean, request, runs);
            require(
                    restored.path("absence").path("reactivated").asInt() == expected,
                    "DEV_LOCAL_SCENARIO_REACTIVATION_COUNT");
            require(
                    active(initial.path("state")).equals(active(restored.path("state"))),
                    "DEV_LOCAL_SCENARIO_REACTIVATION_IDENTITIES");
            checks.put("deactivated", expected);
            checks.put("reactivated", expected);
            checks.put("retainedIdentitySetRestored", true);
        } else {
            final ObjectNode failedDataset = absent.deepCopy();
            final ArrayNode unused = proofNames(prefix + "-unused");
            final ObjectNode guard = sweep(failedDataset, unused);
            if ("PARTIAL_REFUSED".equals(scenario)) {
                guard.put("completeSnapshot", false);
            } else if ("OWNER_REFUSED".equals(scenario)) {
                guard.put("owner", false);
            } else {
                JsonNode quarantine = null;
                for (final JsonNode row : entity(dataset, "Users").path("rows")) {
                    if (!row.path("valid").asBoolean()) {
                        quarantine = row;
                    }
                }
                require(quarantine != null, "DEV_LOCAL_SCENARIO_QUARANTINE_REQUIRED");
                final ObjectNode quarantined = entity(failedDataset, "Users");
                ((ArrayNode) quarantined.path("rows")).add(quarantine);
                quarantined.put("inputRows", quarantined.path("rows").size());
                quarantined.put("quarantined", 1);
                guard.put(
                        "snapshotSha256",
                        DevLocalLedger.sha256(DevLocalEngine.bytes(failedDataset)));
            }
            request.set("sweep", guard);
            final String failedId = prefix + "-refused";
            String refusal = null;
            try {
                DevLocalEngine.execute(ledger, failedId, failedDataset, request);
            } catch (final IllegalArgumentException observed) {
                refusal = observed.getMessage();
            }
            require(
                    refusal != null && refusal.startsWith("DEV_LOCAL_SWEEP_"),
                    "DEV_LOCAL_SCENARIO_EXPECTED_REFUSAL");
            final var failed = ledger.readback(failedId);
            require(
                    failed.state() == DevLocalLedger.State.FAILED && failed.outputs().isEmpty(),
                    "DEV_LOCAL_SCENARIO_REFUSAL_NO_OUTPUT");
            record(runs, failed, "REFUSED_APPLY");
            checks.put("applyRefused", true);
            checks.put("refusalCode", refusal);
            checks.put("proofsUsedToGrantApply", false);
            checks.put("beforeStateSha256", beforeState);
            checks.put(
                    "afterStateSha256",
                    DevLocalLedger.sha256(
                            DevLocalEngine.bytes(
                                    DevLocalEngine.JSON
                                            .readTree(
                                                    ledger.readOutput(
                                                            prefix + "-clean", "result.json"))
                                            .path("state"))));
            require(
                    checks.path("beforeStateSha256").equals(checks.path("afterStateSha256")),
                    "DEV_LOCAL_SCENARIO_PRIOR_CHANGED");
        }
        require(
                java.util.Arrays.equals(
                        beforeOutput, ledger.readOutput(prefix + "-clean", "result.json")),
                "DEV_LOCAL_SCENARIO_PRIOR_OUTPUT_CHANGED");
        report.put("status", "COMPLETED");
        report.put("reasonCode", "LOCAL_SCENARIO_VERIFIED");
        ledger.output(prefix, "result.json", DevLocalEngine.bytes(report));
        ledger.finish(prefix, DevLocalLedger.State.COMPLETED, "LOCAL_SCENARIO_VERIFIED");
        require(
                ledger.readback(prefix).state() == DevLocalLedger.State.COMPLETED,
                "DEV_LOCAL_SCENARIO_TERMINAL_READBACK");
        return DevLocalEngine.JSON.readTree(ledger.readOutput(prefix, "result.json"));
    }

    private static ArrayNode witnesses(
            final DevLocalLedger ledger,
            final String prefix,
            final JsonNode observation,
            final JsonNode reappearance,
            final JsonNode configuration,
            final ArrayNode runs)
            throws IOException {
        final ArrayNode proofs = proofNames(prefix);
        for (int index = 0; index < 4; index++) {
            final String id = proofs.path(index).path("run").asText();
            final ObjectNode identity = DevLocalEngine.JSON.createObjectNode();
            identity.set("absence", observation);
            identity.set("reappearance", reappearance);
            identity.set("configuration", configuration);
            ledger.start(id, DevLocalLedger.sha256(DevLocalEngine.bytes(identity)));
            ledger.event(id, "LOCAL_WITNESS_BEGIN", "SYNTHETIC_LOCAL_ONLY");
            final JsonNode result = DevLocalEngine.execute(observation, configuration);
            final JsonNode returned = DevLocalEngine.execute(reappearance, configuration);
            require(
                    result.path("state").path("entities").size()
                                    == reappearance.path("entities").size()
                            && "COMPLETED".equals(result.path("status").asText())
                            && "COMPLETED".equals(returned.path("status").asText()),
                    "DEV_LOCAL_SCENARIO_WITNESS_SCOPE");
            final byte[] absenceBytes = DevLocalEngine.bytes(result);
            final byte[] returnedBytes = DevLocalEngine.bytes(returned);
            ledger.output(id, "result.json", absenceBytes);
            ledger.output(id, "reappearance.json", returnedBytes);
            ledger.finish(id, DevLocalLedger.State.COMPLETED, "LOCAL_COMPUTATION_COMPLETED");
            final var observed = ledger.readback(id);
            require(
                    observed.state() == DevLocalLedger.State.COMPLETED
                            && observed.outputs().size() == 2
                            && java.util.Arrays.equals(
                                    absenceBytes, ledger.readOutput(id, "result.json"))
                            && java.util.Arrays.equals(
                                    returnedBytes, ledger.readOutput(id, "reappearance.json")),
                    "DEV_LOCAL_SCENARIO_WITNESS_READBACK");
            record(runs, observed, "LOCAL_WITNESS_" + (index + 1));
            final ObjectNode receipt = (ObjectNode) runs.path(runs.size() - 1);
            receipt.put("reappearanceOutputName", "reappearance.json");
            receipt.put(
                    "absenceSnapshotSha256",
                    DevLocalLedger.sha256(DevLocalEngine.bytes(observation)));
            receipt.put(
                    "reappearanceSnapshotSha256",
                    DevLocalLedger.sha256(DevLocalEngine.bytes(reappearance)));
            receipt.put("absenceOutputSha256", DevLocalLedger.sha256(absenceBytes));
            receipt.put("reappearanceOutputSha256", DevLocalLedger.sha256(returnedBytes));
        }
        return proofs;
    }

    private static ArrayNode proofNames(final String prefix) {
        final ArrayNode proofs = DevLocalEngine.JSON.createArrayNode();
        for (int index = 1; index <= 4; index++) {
            final String id = prefix + "-proof" + index;
            proofs.addObject()
                    .put("run", id)
                    .put(
                            "occurrence",
                            "obs-"
                                    + DevLocalLedger.sha256(
                                            id.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        return proofs;
    }

    private static ObjectNode sweep(final JsonNode dataset, final ArrayNode proofs) {
        final ObjectNode request = DevLocalEngine.JSON.createObjectNode();
        request.put("synthetic", true);
        request.put("enabled", true);
        request.put("owner", true);
        request.put("completeSnapshot", true);
        request.put("emptyAllowed", false);
        request.put("apply", true);
        request.put("reactivate", false);
        request.put("snapshotSha256", DevLocalLedger.sha256(DevLocalEngine.bytes(dataset)));
        request.set("proofs", proofs);
        return request;
    }

    private static JsonNode child(
            final DevLocalLedger ledger,
            final String id,
            final String phase,
            final JsonNode dataset,
            final JsonNode options,
            final ArrayNode runs)
            throws IOException {
        final JsonNode result = DevLocalEngine.execute(ledger, id, dataset, options);
        final var observed = ledger.readback(id);
        require(
                observed.state() == DevLocalLedger.State.COMPLETED
                        && "COMPLETED".equals(result.path("status").asText()),
                "DEV_LOCAL_SCENARIO_CHILD_TERMINAL");
        record(runs, observed, phase);
        return result;
    }

    private static void record(
            final ArrayNode runs, final DevLocalLedger.Run run, final String phase) {
        final ObjectNode receipt = runs.addObject();
        receipt.put("executionId", run.executionId());
        receipt.put("state", run.state().name());
        receipt.put("reasonCode", run.reasonCode());
        receipt.put("phase", phase);
        receipt.put("outputName", run.outputs().isEmpty() ? "" : "result.json");
    }

    private static ObjectNode clean(final JsonNode dataset) {
        final ObjectNode result = dataset.deepCopy();
        for (final JsonNode item : result.path("entities")) {
            final ObjectNode entity = (ObjectNode) item;
            final ArrayNode accepted = DevLocalEngine.JSON.createArrayNode();
            entity.path("rows")
                    .forEach(
                            row -> {
                                if (row.path("eligible").asBoolean()) {
                                    accepted.add(row);
                                }
                            });
            entity.set("rows", accepted);
            entity.put("inputRows", accepted.size());
            entity.put("accepted", accepted.size());
            entity.put("quarantined", 0);
            entity.put("filtered", 0);
        }
        return result;
    }

    private static ObjectNode entity(final JsonNode dataset, final String name) {
        for (final JsonNode row : dataset.path("entities")) {
            if (name.equals(row.path("entity").asText())) {
                return (ObjectNode) row;
            }
        }
        throw new IllegalArgumentException("DEV_LOCAL_SCENARIO_ENTITY");
    }

    private static JsonNode active(final JsonNode state) {
        final ObjectNode active = DevLocalEngine.JSON.createObjectNode();
        for (final JsonNode entity : state.path("entities")) {
            for (final JsonNode row : entity.path("rows")) {
                active.put(row.path("technicalKey").asText(), row.path("active").asBoolean());
            }
        }
        return active;
    }

    private static void require(final boolean condition, final String code) {
        if (!condition) {
            throw new IllegalStateException(code);
        }
    }
}
