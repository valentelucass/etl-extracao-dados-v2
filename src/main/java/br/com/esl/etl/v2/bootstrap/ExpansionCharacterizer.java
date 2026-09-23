package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.contasapagar.aplicacao.ContaPagarDataExportMapper;
import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturaClienteDataExportMapper;
import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioDataExportMapper;
import br.com.esl.etl.v2.modulos.sinistros.aplicacao.SinistroDataExportMapper;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionArtifact;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionEnvelope;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;

/** Uses the delivered typed mappers; reports structure without asserting supplier evidence. */
public final class ExpansionCharacterizer {
    private ExpansionCharacterizer() {}

    public static Result inspect(final ExpansionArtifact input, final CancellationToken token) {
        final long[] counts = new long[2];
        final var traversal =
                input.inspect(
                        token,
                        row -> {
                            final ExpansionObservation observation =
                                    switch (input.template()) {
                                        case CONTAS_A_PAGAR ->
                                                new ContaPagarDataExportMapper().map(1, row);
                                        case FATURAS_POR_CLIENTE ->
                                                new FaturaClienteDataExportMapper().map(1, row);
                                        case INVENTARIO ->
                                                new InventarioDataExportMapper().map(1, row);
                                        case SINISTROS ->
                                                new SinistroDataExportMapper().map(1, row);
                                        default ->
                                                throw new IllegalArgumentException(
                                                        "EXP_ARTIFACT_FAMILY");
                                    };
                            ExpansionEnvelope.capture(row, observation);
                            counts[observation.valid() ? 0 : 1]++;
                        });
        return new Result(traversal, counts[0], counts[1]);
    }

    public record Result(ExpansionArtifact.Inspection traversal, long validRows, long invalidRows) {
        public boolean executable() {
            return traversal.completeSyntheticTraversal()
                    && invalidRows == 0
                    && traversal.absentBindings() == 0;
        }
    }
}
