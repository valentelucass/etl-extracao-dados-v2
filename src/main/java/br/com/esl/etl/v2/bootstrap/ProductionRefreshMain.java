package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportMetadataCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.producao.ProductionRefreshCycle;

/** Explicit finite production batch. No credential arguments, scheduler or dashboard activation. */
public final class ProductionRefreshMain {
    private ProductionRefreshMain() {}

    public static void main(final String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("REFRESH_STDIN_ONLY");
        }
        final var input = System.in.readNBytes(65537);
        if (input.length == 0 || input.length > 65536) {
            throw new IllegalArgumentException("REFRESH_INPUT_BOUND");
        }
        final var config = CurlSnapshotWire.JSON.readTree(input);
        java.util.Arrays.fill(input, (byte) 0);
        final var extractor = new ReportSnapshotExtractor(new ReportMetadataCatalog());
        final boolean passed =
                new ProductionRefreshCycle(
                                (template, dates, updates, wire, sink, limits) ->
                                        updates == null
                                                ? extractor.extractRecords(
                                                        template,
                                                        dates,
                                                        wire,
                                                        sink,
                                                        record ->
                                                                SnapshotMapperCatalog.map(
                                                                        template, record),
                                                        limits)
                                                : extractor.extractUpdatedRecords(
                                                        template,
                                                        dates,
                                                        updates,
                                                        wire,
                                                        sink,
                                                        record ->
                                                                SnapshotMapperCatalog.map(
                                                                        template, record),
                                                        limits))
                        .run(config);
        System.exit(passed ? 0 : 1);
    }
}
