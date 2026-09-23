package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.SyntheticCaptureObserver;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;

/** Optional bounded instrumentation; it receives the active page, never a captured universe. */
public interface AnalyticScenarioObserver
        extends ExpansionSyntheticSource.Observer,
                ExpansionDependencySource.Observer,
                RelationalSyntheticSource.Observer,
                LocalRasterRuntime.Observer,
                SyntheticCaptureObserver {
    AnalyticScenarioObserver NONE = new AnalyticScenarioObserver() {};

    enum Input {
        COL,
        FRE,
        MAN,
        COT,
        LOC,
        CAP,
        FAT,
        INV,
        SIN,
        USER,
        RASTER
    }

    default AnalyticScenarioObserver forInput(Input input) {
        java.util.Objects.requireNonNull(input);
        return this;
    }

    default AnalyticScenarioObserver forTemplate(DataExportTemplate template) {
        return forInput(
                switch (template) {
                    case COLETAS -> Input.COL;
                    case FRETES -> Input.FRE;
                    case MANIFESTOS -> Input.MAN;
                    case COTACOES -> Input.COT;
                    case LOCALIZACAO_CARGAS -> Input.LOC;
                    case CONTAS_A_PAGAR -> Input.CAP;
                    case FATURAS_POR_CLIENTE -> Input.FAT;
                    case INVENTARIO -> Input.INV;
                    case SINISTROS -> Input.SIN;
                });
    }

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
