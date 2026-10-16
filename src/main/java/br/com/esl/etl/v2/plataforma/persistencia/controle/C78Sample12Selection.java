package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Scope;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlan;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlanItem;

import java.util.Objects;

/** Descriptive immutable selected plan only. This class grants no authority or physical effects. */
public final class C78Sample12Selection {
    private final Scope scope;
    private final RuntimeExecutionPlan plan;
    private final RuntimeExecutionPlanItem item;

    private C78Sample12Selection(final Scope scope, final RuntimeExecutionPlan plan) {
        this.scope = Objects.requireNonNull(scope);
        this.plan = Objects.requireNonNull(plan);
        if (plan.executionCount() != 1
                || scope.authorization().action() != RuntimeAction.RUN
                || scope.template().templateId() != 6908
                || scope.pageSize() != 1
                || scope.extractionLimits().maxPages() != 1
                || scope.extractionLimits().maxRecords() != 12) {
            throw new IllegalArgumentException("C78_SAMPLE12_SELECTED_PLAN_REQUIRED");
        }
        final RuntimeExecutionPlanItem[] selected = new RuntimeExecutionPlanItem[1];
        plan.forEach(value -> selected[0] = value);
        item = selected[0];
        if (!item.request().executionId().equals(scope.authorization().executionId())
                || !item.partition().entity().equals("coletas")
                || !item.definition().sourceKind().equals("DATA_EXPORT")
                || !item.definition().leaseDuration().equals(java.time.Duration.ofSeconds(60))
                || item.partition().mode() != ExecutionMode.BACKFILL
                || item.request().replayOfExecutionId().isPresent()) {
            throw new IllegalArgumentException("C78_SAMPLE12_FRESH_COLETAS_BACKFILL_REQUIRED");
        }
        item.definition()
                .forEachDependency(
                        value -> {
                            throw new IllegalArgumentException(
                                    "C78_SAMPLE12_DEPENDENCY_NOT_ADMITTED");
                        });
    }

    public static C78Sample12Selection from(final Scope scope, final RuntimeExecutionPlan plan) {
        return new C78Sample12Selection(scope, plan);
    }

    Scope scope() {
        return scope;
    }

    RuntimeExecutionPlan plan() {
        return plan;
    }

    RuntimeExecutionPlanItem item() {
        return item;
    }

    @Override
    public String toString() {
        return "C78Sample12Selection[redacted; descriptive-not-authority]";
    }
}
