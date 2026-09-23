package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernel;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepApplicability;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewAssessment;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewEvidence;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Closed catalogue metadata only. Technical hierarchy validation grants no presence authority. */
final class SweepResponsibilityPlanner {
    private final Map<String, Responsibility> responsibilities;

    public SweepResponsibilityPlanner() {
        final var rows = new LinkedHashMap<String, Responsibility>();
        try (var input =
                SweepResponsibilityPlanner.class.getResourceAsStream(
                        "/sweep-laboratory/responsibilities.v1.csv")) {
            if (input == null) {
                throw new IllegalStateException("SWEEP_CATALOG_MISSING");
            }
            final byte[] bytes = input.readNBytes(65537);
            if (bytes.length > 65536) {
                throw new IllegalStateException("SWEEP_CATALOG_BOUND");
            }
            final var lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
            if (lines.size() != 34) {
                throw new IllegalStateException("SWEEP_CATALOG_COUNT");
            }
            for (int index = 1; index < lines.size(); index++) {
                final String[] fields = lines.get(index).split(",", -1);
                if (fields.length != 16) {
                    throw new IllegalStateException("SWEEP_CATALOG_COLUMNS");
                }
                final var row =
                        new Responsibility(
                                fields[0],
                                fields[1],
                                fields[4],
                                SweepScope.ResponsibilityKind.valueOf(fields[3]),
                                SweepApplicability.valueOf(fields[5]),
                                fields[8],
                                fields[11],
                                fields[12],
                                fields[14]);
                if (row.applicability() == SweepApplicability.ENABLED
                        || rows.put(row.id(), row) != null) {
                    throw new IllegalStateException("SWEEP_CATALOG_ACTIVATION_OR_DUPLICATE");
                }
            }
        } catch (final IOException failure) {
            throw new IllegalStateException("SWEEP_CATALOG_READ", failure);
        }
        responsibilities = java.util.Collections.unmodifiableMap(rows);
    }

    /**
     * Bind catalogue responsibility identifiers to one explicit execution context, never row keys.
     */
    public List<Binding> bindings(final String scope, final String snapshot) {
        final var result = new ArrayList<Binding>(33);
        for (final var row : responsibilities.values()) {
            result.add(new Binding(row.id(), row.parent(), scope, snapshot));
        }
        return List.copyOf(result);
    }

    public void validate(final List<Binding> bindings) {
        if (bindings == null || bindings.size() != 33) {
            throw new IllegalArgumentException("SWEEP_PLAN_COUNT");
        }
        final var indexed = new LinkedHashMap<String, Binding>();
        for (final var binding : bindings) {
            if (!responsibilities.containsKey(binding.id())
                    || indexed.put(binding.id(), binding) != null) {
                throw new IllegalArgumentException("SWEEP_PLAN_IDENTITY");
            }
        }
        for (final var binding : indexed.values()) {
            var cursor = binding;
            int depth = 0;
            while (!cursor.parent().isEmpty()) {
                final var parent = indexed.get(cursor.parent());
                if (parent == null) {
                    throw new IllegalArgumentException("SWEEP_PLAN_ORPHAN");
                }
                if (++depth > 33) {
                    throw new IllegalArgumentException("SWEEP_PLAN_CYCLE");
                }
                if (!cursor.scope().equals(parent.scope())
                        || !cursor.snapshot().equals(parent.snapshot())) {
                    throw new IllegalArgumentException("SWEEP_PLAN_SCOPE");
                }
                cursor = parent;
            }
            if (!binding.parent().equals(responsibilities.get(binding.id()).parent())) {
                throw new IllegalArgumentException("SWEEP_PLAN_PARENT_CONTRACT");
            }
        }
    }

    /** Every row reaches the existing kernel; the original applicability always remains binding. */
    public List<Preview> preview(
            final List<Binding> bindings, final SweepPreviewEvidence evidence) {
        validate(bindings);
        Objects.requireNonNull(evidence);
        final var result = new ArrayList<Preview>(33);
        for (final var binding : bindings) {
            if (!binding.scope().equals(evidence.firstTraversalEvidence().scopeFingerprint())
                    || !binding.snapshot()
                            .equals(evidence.firstTraversalEvidence().snapshotFingerprint())) {
                throw new IllegalArgumentException("SWEEP_PLAN_EVIDENCE_SCOPE");
            }
            final var row = responsibilities.get(binding.id());
            final String policy =
                    SweepScope.canonicalPolicyFingerprint(
                            row.applicability(), row.kind(), true, false, false, 0, 0, 0, 0);
            final var scope =
                    new SweepScope(
                            row.applicability(),
                            row.kind(),
                            true,
                            false,
                            false,
                            0,
                            0,
                            0,
                            0,
                            policy,
                            SweepScope.canonicalBindingFingerprint(
                                    policy, binding.scope(), binding.snapshot()),
                            binding.scope(),
                            binding.snapshot());
            result.add(
                    new Preview(row, new FailClosedSweepPreviewKernel().assess(scope, evidence)));
        }
        return List.copyOf(result);
    }

    public record Binding(String id, String parent, String scope, String snapshot) {
        public Binding {
            if (id == null
                    || !id.matches("SWP-[A-Z0-9-]{1,80}")
                    || parent == null
                    || !parent.isEmpty() && !parent.matches("SWP-[A-Z0-9-]{1,80}")
                    || scope == null
                    || !scope.matches("[a-f0-9]{64}")
                    || snapshot == null
                    || !snapshot.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("SWEEP_PLAN_BINDING");
            }
        }
    }

    public record Responsibility(
            String id,
            String family,
            String parent,
            SweepScope.ResponsibilityKind kind,
            SweepApplicability applicability,
            String hierarchyPolicy,
            String presenceStatus,
            String confirmationPolicy,
            String reason) {}

    public record Preview(Responsibility responsibility, SweepPreviewAssessment assessment) {}
}
