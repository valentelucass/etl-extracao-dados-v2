package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticScenario;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.DeclaredWireRows;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationLineageEvidence;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationLocationOracle;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationTopology;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationWireOracle;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The campaign's streaming comparator and dependency gates share one bounded, physical observation.
 */
public final class QualificationScenarioVerifier {
    public record Result(
            Map<String, QualificationGate> scopes,
            List<QualificationComparator.Result> outputs,
            QualificationGate selected,
            int retainedTechnicalRecords,
            List<SweepResponsibilityPlanner.Preview> sweepPreview) {
        public Result(
                final Map<String, QualificationGate> scopes,
                final List<QualificationComparator.Result> outputs,
                final QualificationGate selected,
                final int retainedTechnicalRecords) {
            this(scopes, outputs, selected, retainedTechnicalRecords, List.of());
        }

        public Result {
            if (scopes == null || scopes.size() != 35 || outputs == null || outputs.size() != 19) {
                throw new IllegalArgumentException("QUAL_VERIFIER_RESULT_BOUND");
            }
            scopes = Map.copyOf(scopes);
            outputs = List.copyOf(outputs);
            if (sweepPreview == null || (!sweepPreview.isEmpty() && sweepPreview.size() != 33)) {
                throw new IllegalArgumentException("QUAL_VERIFIER_SWEEP_BOUND");
            }
            sweepPreview = List.copyOf(sweepPreview);
        }
    }

    private final QualificationOracles oracles;
    private final QualificationComparator.Binding binding;
    private final DeclaredWireRows declaredWire;
    private final LocalFactOracle declaredFacts;
    private final DeclaredSqlOracles integralOracles;

    public QualificationScenarioVerifier(
            final QualificationOracles oracles,
            final QualificationComparator.Binding approved,
            final QualificationComparator.Binding supplied) {
        this(oracles, approved, supplied, null, null);
    }

    public QualificationScenarioVerifier(
            final QualificationOracles oracles,
            final QualificationComparator.Binding approved,
            final QualificationComparator.Binding supplied,
            final DeclaredWireRows declaredWire,
            final LocalFactOracle declaredFacts) {
        if (!approved.equals(supplied)) {
            throw new IllegalArgumentException("QUAL_VERIFIER_ORACLE_REVISION");
        }
        integralOracles = null;
        this.oracles = java.util.Objects.requireNonNull(oracles);
        binding = approved;
        if ((declaredWire == null) != (declaredFacts == null)) {
            throw new IllegalArgumentException("LOCAL_ORACLE_INPUTS_REQUIRED_TOGETHER");
        }
        this.declaredWire = declaredWire;
        this.declaredFacts = declaredFacts;
    }

    public QualificationScenarioVerifier(
            final DeclaredSqlOracles oracles,
            final QualificationComparator.Binding binding,
            final DeclaredWireRows wire,
            final LocalFactOracle facts) {
        integralOracles = java.util.Objects.requireNonNull(oracles);
        this.binding = java.util.Objects.requireNonNull(binding);
        declaredWire = java.util.Objects.requireNonNull(wire);
        declaredFacts = java.util.Objects.requireNonNull(facts);
        if (!wire.integral()) {
            throw new IllegalArgumentException("INTEGRAL_WIRE_ORACLE_REQUIRED");
        }
        this.oracles = null;
    }

    public Result verifyIntegral(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final AnalyticScenarioRuntime.Cycle cycle,
            final DeclaredIntegralInputs input,
            final Instant started,
            final CancellationToken token)
            throws Exception {
        return verifyIntegral(session, run, List.of(cycle), input, started, token);
    }

    public Result verifyIntegral(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final List<AnalyticScenarioRuntime.Cycle> cycles,
            final DeclaredIntegralInputs input,
            final Instant started,
            final CancellationToken token)
            throws Exception {
        return verifyIntegral(session, run, cycles, input, started, token, List.of());
    }

    Result verifyIntegral(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final List<AnalyticScenarioRuntime.Cycle> cycles,
            final DeclaredIntegralInputs input,
            final Instant started,
            final CancellationToken token,
            final List<SequenceRecomposition.Receipt> recompositions)
            throws Exception {
        return new SqlVerification()
                .verifyIntegral(session, run, cycles, input, started, token, recompositions);
    }

    public Result verify(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final List<AnalyticScenarioRuntime.Cycle> cycles,
            final Instant started,
            final List<AnalyticSqlContract> selected,
            final CancellationToken token)
            throws Exception {
        return new SqlVerification().verify(session, run, cycles, started, selected, token);
    }

    QualificationComparator.ExpectedRows expected(
            final AnalyticScenarioRuntime.Fault fault,
            final AnalyticSqlContract contract,
            final QualificationOracles.Context context) {
        final boolean empty =
                fault == AnalyticScenarioRuntime.Fault.RASTER_INCOMPLETE
                                && contract == AnalyticSqlContract.SQL_13
                        || fault == AnalyticScenarioRuntime.Fault.MANIFEST_FLEET_MISSING
                                && (contract == AnalyticSqlContract.SQL_08
                                        || contract == AnalyticSqlContract.SQL_09);
        // Omitting MAN fleet assignments leaves only the tractor bound by the independent SIN
        // input,
        // with its three declared validity rows; the two trailers have no selected binding.
        final boolean tractorOnly =
                fault == AnalyticScenarioRuntime.Fault.MANIFEST_FLEET_MISSING
                        && contract == AnalyticSqlContract.SQL_16;
        if (!empty && !tractorOnly) {
            return oracles.expected(contract, context);
        }
        final var original = oracles.expected(contract, context);
        return new QualificationComparator.ExpectedRows() {
            @Override
            public long count() {
                return tractorOnly ? 3 : 0;
            }

            @Override
            public List<AnalyticSqlValue> at(final long ordinal) {
                if (ordinal < 0 || ordinal >= count()) {
                    throw new IllegalArgumentException("QUAL_BLOCKED_OUTPUT_HAS_NO_EXPECTED_ROW");
                }
                return original.at(ordinal);
            }
        };
    }

    private final class SqlVerification {
        Result verifyIntegral(
                final ColetaTemporalLaboratorySession session,
                final AnalyticScenarioRuntime.Run run,
                final List<AnalyticScenarioRuntime.Cycle> cycles,
                final DeclaredIntegralInputs input,
                final Instant started,
                final CancellationToken token,
                final List<SequenceRecomposition.Receipt> recompositions)
                throws Exception {
            if (cycles.isEmpty() || cycles.size() > 8) {
                throw new IllegalArgumentException("INTEGRAL_VERIFY_CYCLE_BOUND");
            }
            final var cycle = cycles.get(cycles.size() - 1);
            final int cohort =
                    Math.toIntExact(
                            cycles.stream()
                                    .filter(
                                            value ->
                                                    value.sourceRevision()
                                                            == cycle.sourceRevision())
                                    .count());
            java.util.Objects.requireNonNull(integralOracles).verifyFiles(token);
            final var lineage =
                    new QualificationLineageEvidence(
                            session,
                            run.id(),
                            run.expansion(),
                            run.roots(),
                            input.revision(),
                            false,
                            cohort,
                            new QualificationWireOracle(run.variant(), declaredWire),
                            input.references().revision(),
                            input.captureDate());
            final var monitoring = integralOracles.monitoring(session, run, cycles, recompositions);
            final Map<String, QualificationGate> scopes = new LinkedHashMap<>();
            sourceGates(session, run, cycle, scopes);
            if (recompositions.isEmpty()) {
                factGates(session, run, cycle, scopes, integralOracles.factCandidates());
            } else {
                factGates(
                        session,
                        run,
                        recompositions.get(recompositions.size() - 1).facts(),
                        scopes,
                        integralOracles.factCandidates());
            }
            final var outputs = new ArrayList<QualificationComparator.Result>();
            for (final var contract : AnalyticSqlContract.values()) {
                final var comparator =
                        new QualificationComparator(
                                contract,
                                binding,
                                binding,
                                AnalyticSqlCatalog.columns(contract),
                                integralOracles.expected(
                                        contract,
                                        run.id(),
                                        lineage,
                                        monitoring,
                                        started,
                                        Instant.now()));
                new JdbcAnalyticQueries(session)
                        .read(
                                run.id(),
                                contract,
                                input.references().revision(),
                                4096,
                                token,
                                comparator::metadata,
                                comparator);
                final var result = comparator.finish();
                outputs.add(result);
                scopes.put(
                        contract.id(),
                        new QualificationGate(
                                contract.id(),
                                result.gate(),
                                result.differences() == 0
                                        ? "EXACT_INDEPENDENT_ORACLE"
                                        : "ORACLE_DIVERGENCE",
                                "ORACLE"));
            }
            declaredFacts.verify(session, run.id(), run.expansion(), token);
            final var evaluated = QualificationTopology.evaluate(scopes);
            return new Result(
                    evaluated,
                    outputs,
                    QualificationTopology.selected(
                            List.of(AnalyticSqlContract.values()), evaluated),
                    lineage.retainedTechnicalRecords() + monitoring.count());
        }

        public Result verify(
                final ColetaTemporalLaboratorySession session,
                final AnalyticScenarioRuntime.Run run,
                final List<AnalyticScenarioRuntime.Cycle> cycles,
                final Instant started,
                final List<AnalyticSqlContract> selected,
                final CancellationToken token)
                throws Exception {
            if (cycles.isEmpty()
                    || cycles.size() > 8
                    || selected.isEmpty()
                    || new HashSet<>(selected).size() != selected.size()) {
                throw new IllegalArgumentException("QUAL_VERIFIER_SCOPE");
            }
            token.throwIfCancellationRequested();
            final var latest = cycles.get(cycles.size() - 1);
            final var lineage =
                    new QualificationLineageEvidence(
                            session,
                            run.id(),
                            run.expansion(),
                            run.roots(),
                            latest.sourceRevision(),
                            latest.correction(),
                            Math.toIntExact(
                                    cycles.stream()
                                            .filter(
                                                    cycle ->
                                                            cycle.sourceRevision()
                                                                            == latest
                                                                                    .sourceRevision()
                                                                    && cycle.correction()
                                                                            == latest.correction())
                                            .count()),
                            new QualificationWireOracle(run.variant(), declaredWire));
            final var monitoring = new QualificationMonitoring(session, run, cycles);
            final var evidence =
                    new Evidence(
                            lineage, new QualificationLocationOracle(run.variant()), monitoring);
            final var context =
                    new QualificationOracles.Context(
                            run.id(),
                            run.roots(),
                            latest.sourceRevision(),
                            latest.correction(),
                            0,
                            null,
                            started,
                            Instant.now(),
                            evidence,
                            run.variant());
            final Map<String, QualificationGate> scopes = new LinkedHashMap<>();
            sourceGates(session, run, latest, scopes);
            factGates(session, run, latest, scopes);
            final var outputs = new ArrayList<QualificationComparator.Result>();
            for (final var contract : AnalyticSqlContract.values()) {
                final var comparator =
                        new QualificationComparator(
                                contract,
                                binding,
                                binding,
                                AnalyticSqlCatalog.columns(contract),
                                expected(run.fault(), contract, context));
                new JdbcAnalyticQueries(session)
                        .read(run.id(), contract, 2, 4096, token, comparator::metadata, comparator);
                final var result = comparator.finish();
                outputs.add(result);
                scopes.put(
                        contract.id(),
                        new QualificationGate(
                                contract.id(),
                                result.gate(),
                                result.differences() == 0
                                        ? "EXACT_INDEPENDENT_ORACLE"
                                        : "ORACLE_DIVERGENCE",
                                "ORACLE"));
            }
            // Existing independently specified SQL sums remain a separate physical fact assertion.
            if (run.fault() == AnalyticScenarioRuntime.Fault.NONE) {
                if (declaredFacts == null) {
                    new JdbcAnalyticScenario(session).verifyFixtureFacts(run.id(), run.roots());
                } else {
                    declaredFacts.verify(session, run.id(), run.expansion(), token);
                }
            }
            final var evaluated = QualificationTopology.evaluate(scopes);
            token.throwIfCancellationRequested();
            return new Result(
                    evaluated,
                    outputs,
                    outputs.stream()
                                    .anyMatch(
                                            value ->
                                                    value.gate()
                                                            != QualificationGate.State.PASS_LOCAL)
                            ? new QualificationGate(
                                    "SELECTED",
                                    QualificationGate.State.FAILED,
                                    "ORACLE_DIVERGENCE",
                                    "ORACLE")
                            : QualificationTopology.selected(selected, evaluated),
                    lineage.retainedTechnicalRecords() + monitoring.count());
        }

        private static void sourceGates(
                final ColetaTemporalLaboratorySession session,
                final AnalyticScenarioRuntime.Run run,
                final AnalyticScenarioRuntime.Cycle cycle,
                final Map<String, QualificationGate> scopes)
                throws SQLException {
            final Map<String, UUID> expected = new HashMap<>();
            cycle.sources().forEach(source -> expected.put(source.entity(), source.execution()));
            final var seen = new HashSet<String>();
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    """
                    SELECT TOP(12) s.entity,s.execution_id,r.valid
                    FROM ctl.analytic_scenario_step s LEFT JOIN core.analytic_scenario_source_receipt r
                    ON r.run_id=? AND r.entity=s.entity AND r.execution_id=s.execution_id
                    WHERE s.cycle_id=?
                    """)) {
                sql.setQueryTimeout(30);
                sql.setString(1, run.id().toString());
                sql.setString(2, cycle.intent().cycle().toString());
                try (var rows = sql.executeQuery()) {
                    while (rows.next()) {
                        final String entity = rows.getString(1);
                        final String execution = rows.getString(2);
                        if (!expected.containsKey(entity)
                                || !seen.add(entity)
                                || !java.util.Objects.equals(
                                        expected.get(entity),
                                        execution == null ? null : UUID.fromString(execution))) {
                            throw new SQLException("QUAL_SOURCE_FOREIGN_OR_DUPLICATE");
                        }
                        final boolean valid = rows.getBoolean(3) && !rows.wasNull();
                        final boolean rejectedSnapshot =
                                entity.equals("COL")
                                        && run.fault()
                                                == AnalyticScenarioRuntime.Fault
                                                        .COLLECTION_SNAPSHOT_INVALID;
                        final boolean complete = valid && !rejectedSnapshot;
                        final String scope = "INPUT_" + (entity.equals("FRETE") ? "FRE" : entity);
                        scopes.put(
                                scope,
                                new QualificationGate(
                                        scope,
                                        complete
                                                ? QualificationGate.State.PASS_LOCAL
                                                : QualificationGate.State.BLOCKED_DEPENDENCY,
                                        complete
                                                ? "LOCAL_CAPTURE_RECEIPT"
                                                : rejectedSnapshot
                                                        ? "SNAPSHOT_COHORT_REJECTED"
                                                        : "CAPTURE_UNPROVEN",
                                        "JDBC"));
                    }
                }
            }
            if (seen.size() != 11) {
                throw new SQLException("QUAL_SOURCE_MISSING");
            }
        }

        private static void factGates(
                final ColetaTemporalLaboratorySession session,
                final AnalyticScenarioRuntime.Run run,
                final AnalyticScenarioRuntime.Cycle cycle,
                final Map<String, QualificationGate> scopes)
                throws SQLException {
            final var defaultCounts =
                    Map.of(
                            "MAT01",
                            2L * run.roots(),
                            "MAT02",
                            3L * run.roots(),
                            "MAT03",
                            (long) run.roots(),
                            "MAT04",
                            (long) run.roots(),
                            "MAT05",
                            (long) run.roots());
            factGates(session, run, cycle, scopes, defaultCounts);
        }

        private static void factGates(
                final ColetaTemporalLaboratorySession session,
                final AnalyticScenarioRuntime.Run run,
                final AnalyticScenarioRuntime.Cycle cycle,
                final Map<String, QualificationGate> scopes,
                final Map<String, Long> counts)
                throws SQLException {
            final var expected =
                    Map.of(
                            cycle.intent().mat01(),
                            "MAT01",
                            cycle.intent().mat02(),
                            "MAT02",
                            cycle.intent().mat03(),
                            "MAT03",
                            cycle.intent().mat04(),
                            "MAT04",
                            cycle.intent().mat05(),
                            "MAT05");
            factGates(session, run, expected, scopes, counts);
        }

        private static void factGates(
                final ColetaTemporalLaboratorySession session,
                final AnalyticScenarioRuntime.Run run,
                final Map<UUID, String> expected,
                final Map<String, QualificationGate> scopes,
                final Map<String, Long> counts)
                throws SQLException {
            final var seen = new HashSet<String>();
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    """
                    SELECT TOP(65) receipt_id,candidates,ready,blocked,inserts,updates,noops
                    FROM ctl.analytic_lab_materialization_receipt WHERE run_id=?
                    UNION ALL
                    SELECT TOP(65) receipt_id,candidates,ready,blocked,inserts,updates,noops
                    FROM ctl.expansion_lab_materialization_receipt WHERE run_id=?
                    """)) {
                sql.setQueryTimeout(30);
                sql.setString(1, run.id().toString());
                sql.setString(2, run.expansion().toString());
                int visited = 0;
                try (var rows = sql.executeQuery()) {
                    while (rows.next()) {
                        if (++visited > 64) {
                            throw new SQLException("QUAL_FACT_RECEIPT_LIMIT");
                        }
                        final String fact = expected.get(UUID.fromString(rows.getString(1)));
                        if (fact == null) {
                            continue;
                        }
                        if (!seen.add(fact)) {
                            throw new SQLException("QUAL_FACT_DUPLICATE");
                        }
                        final long candidates = rows.getLong(2);
                        final long ready = rows.getLong(3);
                        final long blocked = rows.getLong(4);
                        final boolean equation =
                                candidates == counts.get(fact)
                                        && candidates == ready + blocked
                                        && candidates
                                                == rows.getLong(5)
                                                        + rows.getLong(6)
                                                        + rows.getLong(7);
                        scopes.put(
                                fact,
                                new QualificationGate(
                                        fact,
                                        !equation
                                                ? QualificationGate.State.FAILED
                                                : blocked > 0
                                                        ? QualificationGate.State.BLOCKED_DEPENDENCY
                                                        : QualificationGate.State.PASS_LOCAL,
                                        !equation
                                                ? "FACT_EQUATION_DIVERGENCE"
                                                : blocked > 0
                                                        ? "FACT_INPUT_BLOCKED"
                                                        : "FACT_EQUATION_PROVEN",
                                        "JDBC"));
                    }
                }
            }
            if (seen.size() != 5) {
                throw new SQLException("QUAL_FACT_MISSING");
            }
        }

        private record Evidence(
                QualificationLineageEvidence lineage,
                QualificationLocationOracle location,
                QualificationMonitoring monitoring)
                implements QualificationOracles.Evidence {
            @Override
            public List<AnalyticSqlValue> monitor(final long row) {
                return monitoring.row(row);
            }

            @Override
            public int monitorCount() {
                return monitoring.count();
            }

            @Override
            public boolean lineage(
                    final String entity,
                    final int root,
                    final int component,
                    final JsonNode actual) {
                return lineage.compare(entity, root, component, actual);
            }

            @Override
            public String locationHash(final int root) {
                return location.hash(root);
            }

            @Override
            public String componentIdentity(
                    final String entity, final int root, final int component) {
                return lineage.componentIdentity(entity, root, component);
            }
        }
    }
}
