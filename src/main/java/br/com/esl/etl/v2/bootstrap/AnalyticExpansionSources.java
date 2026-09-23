package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionCaptureSource;

/** Source selection at the composition boundary; capture does not construct its own input. */
@FunctionalInterface
public interface AnalyticExpansionSources {
    ExpansionCaptureSource open(
            DataExportTemplate template,
            int roots,
            int pageSize,
            int revision,
            ExpansionCaptureSource.Observer observer);

    static AnalyticExpansionSources laboratory() {
        return (template, roots, pageSize, revision, observer) ->
                AnalyticScenarioFixtures.expansion(template, 1, roots, pageSize, revision)
                        .observed(observer);
    }
}
