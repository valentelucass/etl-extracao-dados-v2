package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import java.util.function.IntFunction;

/** Fixture adapter retained for existing laboratory scenarios. */
public final class ExpansionSyntheticSource extends ExpansionPageSource {
    private final IntFunction<String> pages;

    public ExpansionSyntheticSource(final IntFunction<String> pages) {
        this(pages, Observer.NONE);
    }

    private ExpansionSyntheticSource(final IntFunction<String> pages, final Observer observer) {
        super(pages, observer);
        this.pages = pages;
    }

    @Override
    public ExpansionSyntheticSource observed(final Observer observer) {
        return new ExpansionSyntheticSource(pages, observer);
    }

    public static SourceContractRelease release(final DataExportTemplate template) {
        return ExpansionLocalContract.release(template);
    }
}
