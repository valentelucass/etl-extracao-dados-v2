package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;

/** Optional bounded instrumentation; it receives the active page, never a captured universe. */
public interface AnalyticScenarioObserver
        extends ExpansionSyntheticSource.Observer,
                ExpansionDependencySource.Observer,
                RelationalSyntheticSource.Observer,
                LocalRasterRuntime.Observer {
    AnalyticScenarioObserver NONE = new AnalyticScenarioObserver() {};

    @Override
    default void beforeFetch() {}

    @Override
    default void pageFetched(DataExportPageResponse page, long bytes) {}

    @Override
    default void batchStarted(int records) {}

    @Override
    default void batchStaged(int records) {}

    @Override
    default void captureClosed() {}
}
