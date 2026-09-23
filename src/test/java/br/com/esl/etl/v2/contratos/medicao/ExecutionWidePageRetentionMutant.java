package br.com.esl.etl.v2.contratos.medicao;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

/** Mutante negativo que deliberadamente retém cada objeto real durante toda a execução. */
public final class ExecutionWidePageRetentionMutant implements Consumer<Object> {

    private final ManagedPageGauge gauge;
    private final ArrayList<Object> retainedPages = new ArrayList<>();

    public ExecutionWidePageRetentionMutant(final ManagedPageGauge gauge) {
        this.gauge = Objects.requireNonNull(gauge, "O gauge do mutante é obrigatório.");
    }

    @Override
    public void accept(final Object page) {
        final Object requiredPage =
                Objects.requireNonNull(page, "A página do mutante é obrigatória.");
        gauge.pageRetained(requiredPage);
        try {
            retainedPages.add(requiredPage);
        } catch (final RuntimeException | Error failure) {
            gauge.retainedPageReleased(requiredPage);
            throw failure;
        }
    }

    public int retainedPageCount() {
        return retainedPages.size();
    }

    Object retainedPageAt(final int index) {
        return retainedPages.get(index);
    }

    public void clear() {
        while (!retainedPages.isEmpty()) {
            final int lastIndex = retainedPages.size() - 1;
            final Object retainedPage = retainedPages.get(lastIndex);
            gauge.retainedPageReleased(retainedPage);
            retainedPages.remove(lastIndex);
        }
    }

    @Override
    public String toString() {
        return "ExecutionWidePageRetentionMutant[retainedPageCount=" + retainedPages.size() + "]";
    }
}
