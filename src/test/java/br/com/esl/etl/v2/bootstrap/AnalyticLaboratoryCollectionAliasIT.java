package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryCollectionAliasIT {
    @Test
    void stagingValueWrapperPreservesNumericAliasAndTheCaptureContractRejectsTextualDrift()
            throws Exception {
        final String[][] cases = {
            {"100001", "INTEGER:100001"},
            {"\"100001\"", "STRING:100001"},
            {"\"001A\"", "STRING:001A"}
        };
        for (final var item : cases) {
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                final var fixture = AnalyticLaboratoryDimensionsIT.start(session, true);
                final var row =
                        "{\"id\":200001,\"sequence_code\":"
                                + item[0]
                                + ",\"request_date\":\"2036-04-01\",\"status\":\"pending\","
                                + "\"status_updated_at\":\"2036-04-01T10:00:00.123456789Z\","
                                + "\"synthetic_item_key\":1,\"synthetic_fixture\":true}";
                final var runtime =
                        new LocalRelationalRuntime(
                                session,
                                fixture.relational(),
                                AnalyticLaboratoryManifestPreparationIT.policy(),
                                AnalyticLaboratoryRasterIT.CLOCK,
                                Clock.systemUTC());
                if (item[0].startsWith("\"")) {
                    org.junit.jupiter.api.Assertions.assertThrows(
                            br.com.esl.etl.v2.plataforma.contrato.ContractDriftException.class,
                            () ->
                                    runtime.capture(
                                            DataExportTemplate.COLETAS,
                                            AnalyticLaboratoryRasterIT.DATE,
                                            ExecutionMode.BOOTSTRAP,
                                            null,
                                            new RelationalSyntheticSource(
                                                    page -> page == 1 ? "[" + row + "]" : "[]"),
                                            CancellationToken.none()));
                    assertEquals(
                            0,
                            AnalyticLaboratoryRasterIT.scalar(
                                    session,
                                    "SELECT COUNT_BIG(*) FROM core.relational_lab_root WHERE run_id=? AND entity_name=N'co"
                                            + "letas'",
                                    fixture.relational()));
                    continue;
                }
                runtime.capture(
                        DataExportTemplate.COLETAS,
                        AnalyticLaboratoryRasterIT.DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        new RelationalSyntheticSource(page -> page == 1 ? "[" + row + "]" : "[]"),
                        CancellationToken.none());
                try (var connection = session.getConnection();
                        var sql =
                                connection.prepareStatement(
                                        "SELECT alias_presence,alias_key FROM core.relational_lab_root WHERE run_id=? AND enti"
                                                + "ty_name=N'coletas'")) {
                    sql.setString(1, fixture.relational().toString());
                    sql.setQueryTimeout(10);
                    try (var result = sql.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals("VALUE", result.getString(1));
                        assertEquals(item[1], result.getString(2));
                        assertFalse(result.next());
                    }
                }
            }
        }
    }
}
