package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.producao.ProductionAllReceivingCycle;

/** Secrets arrive on bounded stdin from the existing dedicated DPAPI credential. */
public final class ProductionAllReceivingMain {
    private ProductionAllReceivingMain() {}

    public static void main(final String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("ALL_RECEIVING_STDIN_ONLY");
        }
        final byte[] input = System.in.readNBytes(65537);
        if (input.length == 0 || input.length > 65536) {
            throw new IllegalArgumentException("ALL_RECEIVING_CONFIGURATION_BOUND");
        }
        final var config = CurlSnapshotWire.JSON.readTree(input);
        java.util.Arrays.fill(input, (byte) 0);
        final boolean passed =
                new ProductionAllReceivingCycle(DataExportSnapshotMain::capture).run(config);
        System.exit(passed ? 0 : 1);
    }
}
