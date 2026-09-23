package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.raster.RasterGateway;
import java.io.IOException;

/** Raster source selection for the same analytical composition. */
@FunctionalInterface
public interface AnalyticRasterSources {
    RasterGateway open(int roots, int revision) throws IOException;

    static AnalyticRasterSources laboratory() {
        return AnalyticRasterFixtures::source;
    }
}
