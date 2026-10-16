package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaAbsencePolicy;
import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernel;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepApplicability;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewEvidence;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Local state transitions are separate from the unchanged thirty-three canonical policies. */
final class DevLocalEngineSweep {
    private DevLocalEngineSweep() {}

    static JsonNode preview(final JsonNode dataset) {
        final String context = digest("synthetic-local-scope");
        final String snapshot =
                digest(new String(DevLocalEngine.bytes(dataset), StandardCharsets.UTF_8));
        final SweepScope scope = scope(context, snapshot, false, false);
        final SweepPreviewEvidence evidence = evidence(scope, false, false, null);
        final ArrayNode result = DevLocalEngine.JSON.createArrayNode();
        final SweepResponsibilityPlanner planner = new SweepResponsibilityPlanner();
        for (final var preview : planner.preview(planner.bindings(context, snapshot), evidence)) {
            final ObjectNode row = result.addObject();
            row.put("id", preview.responsibility().id());
            row.put("parent", preview.responsibility().parent());
            row.put("family", preview.responsibility().family());
            row.put("applicability", preview.responsibility().applicability().name());
            row.put("disposition", preview.assessment().disposition().name());
            row.put("reason", preview.assessment().reason().name());
            row.put("apply", false);
        }
        return result;
    }

    static JsonNode apply(
            final ObjectNode state,
            final JsonNode previous,
            final JsonNode request,
            final JsonNode dataset) {
        final ObjectNode receipt = DevLocalEngine.JSON.createObjectNode();
        receipt.put(
                "canonicalColetaChangesActiveState",
                ColetaAbsencePolicy.observe(
                                ColetaAbsencePolicy.Observation.SECOND_INDEPENDENT_ABSENCE)
                        .changesActiveState());
        receipt.put("authority", "SYNTHETIC_LOCAL_DECLARED_CONFIRMATIONS");
        receipt.put("physicalCompletenessProven", false);
        receipt.put("deactivated", 0);
        receipt.put("reactivated", 0);
        receipt.put("policy", "SYNTHETIC_ROOT_V1");
        if (request.isMissingNode()) {
            receipt.put("status", "PREVIEW_ONLY");
            return receipt;
        }
        final Set<String> fields =
                Set.of(
                        "synthetic",
                        "enabled",
                        "owner",
                        "completeSnapshot",
                        "emptyAllowed",
                        "apply",
                        "reactivate",
                        "proofs",
                        "snapshotSha256");
        if (!request.isObject()) {
            throw new IllegalArgumentException("DEV_LOCAL_SWEEP_SHAPE");
        }
        request.fieldNames()
                .forEachRemaining(
                        name -> {
                            if (!fields.contains(name)) {
                                throw new IllegalArgumentException("DEV_LOCAL_SWEEP_FIELD");
                            }
                        });
        for (final String name :
                Set.of(
                        "synthetic",
                        "enabled",
                        "owner",
                        "completeSnapshot",
                        "emptyAllowed",
                        "apply",
                        "reactivate")) {
            if (request.has(name) && !request.path(name).isBoolean()) {
                throw new IllegalArgumentException("DEV_LOCAL_SWEEP_BOOLEAN");
            }
        }
        if (!request.path("synthetic").asBoolean()
                || !request.path("enabled").asBoolean()
                || !request.path("owner").asBoolean()
                || !previous.isObject()
                || !"SYNTHETIC_STATE_V2".equals(previous.path("version").asText())) {
            throw new IllegalArgumentException("DEV_LOCAL_SWEEP_AUTHORITY");
        }
        final String snapshot = DevLocalLedger.sha256(DevLocalEngine.bytes(dataset));
        if (!snapshot.equals(request.path("snapshotSha256").asText())) {
            throw new IllegalArgumentException("DEV_LOCAL_SWEEP_SNAPSHOT");
        }
        boolean locallyComplete = request.path("completeSnapshot").asBoolean();
        boolean empty = true;
        for (final JsonNode entity : dataset.path("entities")) {
            locallyComplete &=
                    entity.path("terminal").asBoolean()
                            && entity.path("quarantined").asInt(-1) == 0;
            empty &= entity.path("rows").isEmpty();
        }
        final SweepScope scope =
                scope(
                        digest("synthetic-local-scope"),
                        snapshot,
                        true,
                        request.path("emptyAllowed").asBoolean());
        final var assessment =
                new FailClosedSweepPreviewKernel()
                        .assess(
                                scope,
                                evidence(scope, locallyComplete, empty, request.path("proofs")));
        receipt.put("disposition", assessment.disposition().name());
        receipt.put("reason", assessment.reason().name());
        if (!assessment.previewEligible()) {
            throw new IllegalArgumentException(
                    "DEV_LOCAL_SWEEP_GUARD_" + assessment.reason().name());
        }
        int deactivated = 0;
        int reactivated = 0;
        for (final JsonNode entity : state.path("entities")) {
            for (final JsonNode item : entity.path("rows")) {
                final ObjectNode row = (ObjectNode) item;
                if (!row.path("observed").asBoolean()
                        && row.path("active").asBoolean()
                        && request.path("apply").asBoolean()) {
                    row.put("active", false);
                    deactivated++;
                } else if (row.path("observed").asBoolean()
                        && !row.path("active").asBoolean()
                        && request.path("reactivate").asBoolean()) {
                    row.put("active", true);
                    reactivated++;
                }
            }
        }
        receipt.put("deactivated", deactivated);
        receipt.put("reactivated", reactivated);
        receipt.put(
                "status",
                request.path("apply").asBoolean() || request.path("reactivate").asBoolean()
                        ? "LOCAL_TRANSITION_APPLIED"
                        : "PREVIEW_ONLY");
        return receipt;
    }

    private static SweepScope scope(
            final String context,
            final String snapshot,
            final boolean owner,
            final boolean emptyAllowed) {
        final String policy =
                SweepScope.canonicalPolicyFingerprint(
                        SweepApplicability.ENABLED,
                        SweepScope.ResponsibilityKind.ROOT,
                        true,
                        owner,
                        emptyAllowed,
                        0,
                        0,
                        0,
                        0);
        return new SweepScope(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.ROOT,
                true,
                owner,
                emptyAllowed,
                0,
                0,
                0,
                0,
                policy,
                SweepScope.canonicalBindingFingerprint(policy, context, snapshot),
                context,
                snapshot);
    }

    private static SweepPreviewEvidence evidence(
            final SweepScope scope,
            final boolean complete,
            final boolean empty,
            final JsonNode proofs) {
        if (proofs != null && (!proofs.isArray() || proofs.size() != 4)) {
            throw new IllegalArgumentException("DEV_LOCAL_SWEEP_FOUR_PROOFS");
        }
        final SweepPreviewEvidence.Proof[] bound = new SweepPreviewEvidence.Proof[4];
        for (int index = 0; index < 4; index++) {
            final String run =
                    proofs == null
                            ? "preview-run-" + index
                            : proofs.path(index).path("run").asText();
            final String occurrence =
                    proofs == null
                            ? "preview-occurrence-" + index
                            : proofs.path(index).path("occurrence").asText();
            if (!run.matches("[A-Za-z][A-Za-z0-9_-]{1,79}")
                    || !occurrence.matches("[A-Za-z][A-Za-z0-9_-]{1,79}")) {
                throw new IllegalArgumentException("DEV_LOCAL_SWEEP_PROOF_ID");
            }
            bound[index] =
                    new SweepPreviewEvidence.Proof(
                            index + 1,
                            digest(occurrence),
                            digest(run),
                            scope.policyFingerprint(),
                            scope.scopeFingerprint(),
                            scope.snapshotFingerprint(),
                            scope.bindingFingerprint());
        }
        return new SweepPreviewEvidence(
                ExecutionMode.SWEEP,
                complete
                        ? SourceCompletenessStatus.PROVEN_COMPLETE
                        : SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF,
                complete
                        ? SweepPreviewEvidence.EvidenceStatus.PROVEN
                        : SweepPreviewEvidence.EvidenceStatus.UNVERIFIED,
                complete,
                complete,
                1,
                complete ? 1 : 0,
                complete ? 1 : 0,
                complete ? 0 : 1,
                false,
                0,
                0,
                empty,
                true,
                true,
                0,
                0,
                0,
                0,
                bound[0],
                bound[1],
                bound[2],
                bound[3]);
    }

    private static String digest(final String value) {
        return DevLocalLedger.sha256(value.getBytes(StandardCharsets.UTF_8));
    }
}
