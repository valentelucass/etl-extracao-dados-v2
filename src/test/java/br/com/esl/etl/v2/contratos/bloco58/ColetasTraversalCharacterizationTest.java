package br.com.esl.etl.v2.contratos.bloco58;

import static br.com.esl.etl.v2.contratos.bloco58.CharacterizationFixtures.input;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.bloco58.ColetasCharacterization.Fault;
import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Layer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ColetasTraversalCharacterizationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, JsonNode> REPORTS = new TreeMap<>();
    private static final String ROW =
            "{\"id\":0,\"sequence_code\":700,\"updated_at\":\"synthetic\",\"pck_mik_mft_sequence_code\":null}";

    private static JsonNode expected() {
        return JSON.createObjectNode()
                .put("/quarantine", "NONE")
                .put("/id/typed", "INTEGER:0")
                .put("/sequence_code/typed", "700")
                .put("/freshness/origin", "UNAVAILABLE");
    }

    private static String page(final int rows) {
        return "{\"data\":[" + String.join(",", java.util.Collections.nCopies(rows, ROW)) + "]}";
    }

    @Test
    void physicalExpansionAndShortPagesKeepTheirOriginalRowsUntilEmptyTerminal() {
        final var report =
                ColetasCharacterization.pipeline(
                        number -> input(number == 1 ? page(30) : number == 2 ? page(1) : page(0)),
                        DataExportFirstWaveContractCatalog.release(DataExportTemplate.COLETAS),
                        2,
                        3,
                        100,
                        Fault.NONE,
                        (number, row) -> expected());
        report.expect(Layer.NONE, 31, true);
        assertEquals(3, report.pagesRead());
        assertTrue(report.matches(), report::toString);
        REPORTS.put("SYNTH_EXPANSION_SHORT_PAGE", report.sanitized());
    }

    @ParameterizedTest
    @EnumSource(Fault.class)
    void faultsAndCancellationNeverCompletePartialStaging(final Fault fault) {
        final var report =
                ColetasCharacterization.pipeline(
                        number -> input(number <= 2 ? page(1) : page(0)),
                        DataExportFirstWaveContractCatalog.release(DataExportTemplate.COLETAS),
                        1,
                        3,
                        2,
                        fault,
                        (number, row) -> expected());
        final Layer refusal =
                switch (fault) {
                    case NONE -> Layer.NONE;
                    case STAGE_FIRST, STAGE_SECOND -> Layer.STAGING;
                    default -> Layer.CANCELLED;
                };
        final int staged =
                switch (fault) {
                    case NONE -> 2;
                    case STAGE_SECOND, CANCEL_AFTER_STAGE -> 1;
                    default -> 0;
                };
        report.expect(refusal, staged, fault == Fault.NONE);
        assertTrue(report.matches(), report::toString);
        REPORTS.put("SYNTH_" + fault, report.sanitized());
    }

    @Test
    void pageAndRowCapsDoNotBecomeLocalTerminal() {
        for (final boolean pageCap : new boolean[] {true, false}) {
            final var report =
                    ColetasCharacterization.pipeline(
                            number -> input(page(pageCap ? 1 : 2)),
                            DataExportFirstWaveContractCatalog.release(DataExportTemplate.COLETAS),
                            2,
                            pageCap ? 1 : 3,
                            1,
                            Fault.NONE,
                            (number, row) -> expected());
            report.expect(Layer.TRAVERSAL, pageCap ? 1 : 0, false);
            assertTrue(report.matches(), report::toString);
            REPORTS.put(pageCap ? "SYNTH_PAGE_CAP" : "SYNTH_ROW_CAP", report.sanitized());
        }
    }

    @Test
    void distinctEntityLimitRejectsEvenWhenPhysicalPageIsSmall() {
        final String document =
                "{\"data\":[" + ROW + "," + ROW.replace("\"id\":0", "\"id\":1") + "]}";
        final var report =
                ColetasCharacterization.pipeline(
                        number -> input(document),
                        DataExportFirstWaveContractCatalog.release(DataExportTemplate.COLETAS),
                        1,
                        2,
                        2,
                        Fault.NONE,
                        (number, row) -> expected());
        report.expect(Layer.PAGE_LIMIT, 0, false);
        assertTrue(report.matches(), report::toString);
        REPORTS.put("SYNTH_ENTITY_CAP", report.sanitized());
    }

    @AfterAll
    static void save() throws Exception {
        CharacterizationFixtures.write("coletas-traversal", REPORTS);
    }
}
