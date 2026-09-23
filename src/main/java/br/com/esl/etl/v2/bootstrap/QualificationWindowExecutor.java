package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticMaterializationRequest;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionMaterializations;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationPlanner;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Planned civil recuts consume existing fact procedures; they never publish a source frontier. */
public final class QualificationWindowExecutor {
    public record Applied(
            String fact,
            UUID receipt,
            LocalDate start,
            LocalDate endExclusive,
            long candidates,
            long inserts,
            long updates,
            long noops,
            long ready,
            long blocked) {}

    public record Result(
            QualificationPlanner.Plan plan,
            List<Applied> applied,
            LocalDate sourceBefore,
            LocalDate sourceAfter,
            QualificationGate gate) {
        public Result {
            if (applied == null || applied.size() > 15) {
                throw new IllegalArgumentException("QUAL_WINDOW_RESULT_BOUND");
            }
            applied = List.copyOf(applied);
        }
    }

    private final ColetaTemporalLaboratorySession session;
    private final Clock clock;

    public QualificationWindowExecutor(
            final ColetaTemporalLaboratorySession session, final Clock clock) {
        this.session = Objects.requireNonNull(session);
        this.clock = Objects.requireNonNull(clock);
    }

    public Result execute(
            final AnalyticScenarioRuntime.Run run,
            final QualificationCampaign.Case item,
            final CancellationToken token)
            throws SQLException {
        final var plan = QualificationPlanner.plan(item);
        if (plan.state() != QualificationGate.State.PASS_LOCAL) {
            return new Result(
                    plan,
                    List.of(),
                    null,
                    null,
                    new QualificationGate("WINDOWS", plan.state(), plan.reason(), "PLANNER"));
        }
        // Fixture dates are declared; an arbitrary due window must not be silently relabelled.
        if (item.start().isBefore(AnalyticScenarioRuntime.START)
                || item.endExclusive().isAfter(AnalyticScenarioRuntime.END)) {
            throw new IllegalArgumentException("QUAL_WINDOW_OUTSIDE_FIXTURE");
        }
        final var before = frontier(run.id());
        final var applied = new ArrayList<Applied>();
        final var material = new JdbcAnalyticMaterializations(session, clock);
        final var expansion =
                new JdbcExpansionMaterializations(session, AnalyticScenarioRuntime.LOGICAL_CLOCK);
        for (final var window : plan.windows()) {
            token.throwIfCancellationRequested();
            final var extraction = window.extractionStart().atZone(item.zone()).toLocalDate();
            final var start =
                    extraction.isBefore(AnalyticScenarioRuntime.START)
                            ? AnalyticScenarioRuntime.START
                            : extraction;
            final var end = window.endExclusive().atZone(item.zone()).toLocalDate();
            for (final var fact : List.of("MAT01", "MAT02", "MAT03", "MAT04", "MAT05")) {
                token.throwIfCancellationRequested();
                final var receipt = UUID.randomUUID();
                final var request =
                        new AnalyticMaterializationRequest(
                                run.id(),
                                receipt,
                                AnalyticScenarioRuntime.REFERENCE_REVISION,
                                item.mode(),
                                false,
                                start,
                                end);
                final JdbcAnalyticMaterializations.Receipt result;
                if (fact.equals("MAT03") || fact.equals("MAT04")) {
                    final var old =
                            fact.equals("MAT03")
                                    ? expansion.revenue(
                                            run.expansion(),
                                            receipt,
                                            1,
                                            item.mode(),
                                            false,
                                            start,
                                            end)
                                    : expansion.invoices(
                                            run.expansion(),
                                            receipt,
                                            1,
                                            item.mode(),
                                            false,
                                            start,
                                            end);
                    result =
                            new JdbcAnalyticMaterializations.Receipt(
                                    old.candidates(),
                                    old.inserts(),
                                    old.updates(),
                                    old.noops(),
                                    old.ready(),
                                    old.blocked());
                } else {
                    result =
                            switch (fact) {
                                case "MAT01" -> material.freight(request, token);
                                case "MAT02" -> material.collectors(request, token);
                                case "MAT05" -> material.manifests(request, token);
                                default -> throw new IllegalStateException("QUAL_WINDOW_FACT");
                            };
                }
                final var observed =
                        new Applied(
                                fact,
                                receipt,
                                start,
                                end,
                                result.candidates(),
                                result.inserts(),
                                result.updates(),
                                result.noops(),
                                result.ready(),
                                result.blocked());
                verifyReceipt(run, item, observed);
                applied.add(observed);
            }
        }
        final var after = frontier(run.id());
        if (!before.equals(after)) {
            throw new SQLException("QUAL_FACT_CHANGED_SOURCE_FRONTIER");
        }
        final boolean blocked = applied.stream().anyMatch(value -> value.blocked() != 0);
        return new Result(
                plan,
                applied,
                before,
                after,
                new QualificationGate(
                        "WINDOWS",
                        blocked
                                ? QualificationGate.State.BLOCKED_DEPENDENCY
                                : QualificationGate.State.PASS_LOCAL,
                        blocked ? "FACT_DEPENDENCY_UNRESOLVED" : "SQL_WINDOWS_AND_FRONTIER_PROVEN",
                        "JDBC"));
    }

    private LocalDate frontier(final UUID run) throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT next_date FROM ctl.analytic_scenario_frontier WHERE run_id=?")) {
            sql.setQueryTimeout(15);
            sql.setString(1, run.toString());
            try (var rows = sql.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("QUAL_SOURCE_FRONTIER_MISSING");
                }
                final var result = rows.getObject(1, LocalDate.class);
                if (result == null || rows.next()) {
                    throw new SQLException("QUAL_SOURCE_FRONTIER_INVALID");
                }
                return result;
            }
        }
    }

    private void verifyReceipt(
            final AnalyticScenarioRuntime.Run run,
            final QualificationCampaign.Case item,
            final Applied expected)
            throws SQLException {
        final boolean expansion =
                expected.fact().equals("MAT03") || expected.fact().equals("MAT04");
        final String kind =
                expected.fact().equals("MAT03")
                        ? "REVENUE"
                        : expected.fact().equals("MAT04") ? "INVOICE" : expected.fact();
        final String query =
                "SELECT kind,mode,full_scope,window_start,window_end_exclusive,"
                        + "candidates,inserts,updates,noops,ready,blocked FROM "
                        + (expansion
                                ? "ctl.expansion_lab_materialization_receipt"
                                : "ctl.analytic_lab_materialization_receipt")
                        + " WHERE run_id=? AND receipt_id=?";
        try (var connection = session.getConnection();
                var sql = connection.prepareStatement(query)) {
            sql.setQueryTimeout(15);
            sql.setString(1, (expansion ? run.expansion() : run.id()).toString());
            sql.setString(2, expected.receipt().toString());
            try (var rows = sql.executeQuery()) {
                if (!rows.next()
                        || !kind.equals(rows.getString(1))
                        || !item.mode().name().equals(rows.getString(2))
                        || rows.getBoolean(3)
                        || !expected.start().equals(rows.getObject(4, LocalDate.class))
                        || !expected.endExclusive().equals(rows.getObject(5, LocalDate.class))
                        || expected.candidates() != rows.getLong(6)
                        || expected.inserts() != rows.getLong(7)
                        || expected.updates() != rows.getLong(8)
                        || expected.noops() != rows.getLong(9)
                        || expected.ready() != rows.getLong(10)
                        || expected.blocked() != rows.getLong(11)
                        || rows.next()) {
                    throw new SQLException("QUAL_WINDOW_RECEIPT_MISMATCH");
                }
            }
        }
    }
}
