package br.com.esl.etl.v2.contratos.bloco62;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import br.com.esl.etl.v2.contratos.bloco58.ColetasCharacterization;
import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Layer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CharacterizationParserAccess;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseForm;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseNormalizer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Synthetic shape regression motivated by B62; no source records or source acceptance. */
class SourceObservationBoundaryTest {
    private static final String ROOT_ARRAY =
            "[{\"id\":1},{\"id\":1},{\"id\":1},{\"id\":2},{\"id\":2}]";

    @Test
    void normalizerPreservesPhysicalExpansionOfTheRootArray() throws Exception {
        final var parsed = CharacterizationParserAccess.parse(ROOT_ARRAY);
        assertEquals(DataExportResponseForm.ROOT_ARRAY, DataExportResponseForm.from(parsed));
        final var normalized = new DataExportResponseNormalizer().normalize(parsed);
        assertEquals(5, normalized.records().size());
        assertEquals(parsed.get(0), normalized.records().get(0));
        assertEquals(parsed.get(4), normalized.records().get(4));
    }

    @Test
    void sourceShapeAcceptanceDoesNotSilentlyWidenTheExistingContract() throws Exception {
        final var expected =
                new ObjectMapper()
                        .readTree("{\"/quarantine\":\"NONE\",\"/id/typed\":\"INTEGER:1\"}");
        final var report =
                ColetasCharacterization.pipeline(
                        page ->
                                new ByteArrayInputStream(
                                        ROOT_ARRAY.getBytes(StandardCharsets.UTF_8)),
                        DataExportFirstWaveContractCatalog.release(DataExportTemplate.COLETAS),
                        2,
                        1,
                        5,
                        ColetasCharacterization.Fault.NONE,
                        (page, row) -> expected);
        report.expect(Layer.NONE, 5, true);
        assertEquals(Layer.PARSER, report.refusal());
        assertEquals(0, report.mappedRows());
        assertEquals(0, report.stagedRows());
        assertFalse(report.matches());
    }
}
