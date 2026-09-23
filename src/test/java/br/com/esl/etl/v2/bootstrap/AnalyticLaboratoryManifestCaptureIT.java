package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryManifestCaptureIT {
    @Test
    void expandedManifestProfileTraversesExistingMapperAndRelationalSqlWithoutChangingBaseline()
            throws Exception {
        final var baseline = RelationalSyntheticSource.contracts();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var policy =
                    new RelationalLaboratoryPolicy(
                            DATE, DATE.plusDays(2), 1000, 10, 3, 60, 2, 0, 2, 100);
            new JdbcRelationalLaboratory(session, CLOCK)
                    .start(run, policy, RelationalSyntheticSource.analyticManifestContracts());
            final var row = manifest();
            assertNull(
                    new br.com.esl.etl.v2.modulos.manifestos.aplicacao
                                    .ManifestoDataExportRecordMapper()
                            .map(1, row)
                            .quarantineReasonCode());
            final var source =
                    new RelationalSyntheticSource(
                                    page -> page == 1 ? "[" + row + "," + row + "]" : "[]")
                            .withAnalyticManifestDetails();
            final var result =
                    new LocalRelationalRuntime(session, run, policy, CLOCK, Clock.systemUTC())
                            .capture(
                                    DataExportTemplate.MANIFESTOS,
                                    DATE,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    source,
                                    CancellationToken.none());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT c.physical_rows,c.root_rows,r.status_code,COUNT_BIG(*) OVER() roots FROM core."
                                            + "relational_lab_root r"
                                            + " JOIN ctl.relational_lab_capture c ON c.execution_id=r.execution_id WHERE r.run_id=?")) {
                sql.setString(1, run.toString());
                sql.setQueryTimeout(10);
                try (var rows = sql.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getLong(1));
                    assertEquals(1, rows.getLong(2));
                    assertEquals("closed", rows.getString(3));
                    assertEquals(1, rows.getLong(4));
                    assertTrue(!rows.next());
                }
            }
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT payload_json FROM stg.manifesto_observation WHERE execution_id=?"
                                            + " ORDER BY input_batch_number,input_record_ordinal")) {
                sql.setString(1, result.executionId().toString());
                sql.setQueryTimeout(10);
                try (var rows = sql.executeQuery()) {
                    int seen = 0;
                    while (rows.next()) {
                        final var captured = new ObjectMapper().readTree(rows.getString(1));
                        assertEquals(row, captured);
                        seen++;
                    }
                    assertEquals(2, seen);
                }
            }
        }
        assertEquals(baseline, RelationalSyntheticSource.contracts());
    }

    static ObjectNode manifest() throws Exception {
        final var json = new ObjectMapper();
        final var row = json.createObjectNode();
        try (var input =
                AnalyticLaboratoryManifestCaptureIT.class.getResourceAsStream(
                        "/analytic-laboratory/manifest-fields.synthetic.json")) {
            final var fields =
                    json.readTree(java.util.Objects.requireNonNull(input)).path("fields");
            for (final var field : fields) {
                final String name = field.path("name").asText();
                switch (field.path("wire").asText()) {
                    case "INTEGER" -> row.put(name, 1);
                    case "BOOLEAN" -> row.put(name, true);
                    case "ARRAY" -> row.putArray(name).add("SYNTHETIC A").add("SYNTHETIC B");
                    case "STRING" ->
                            row.put(
                                    name,
                                    name.endsWith("_at")
                                            ? "2036-04-01T12:00:00.123456789Z"
                                            : name.matches(
                                                            ".*(?:subtotal|total|cost|weight|capacity|value|volume|km)$")
                                                    ? "100.12500000"
                                                    : "SYN");
                    default -> throw new IllegalArgumentException("ANA_MANIFEST_FIXTURE_TYPE");
                }
            }
        }
        row.put("mft_mfs_key", "1".repeat(44));
        row.put("status", "closed");
        row.put("mdfe_status", "authorized");
        row.put("mft_man_name", "SYNTHETIC NORMAL");
        row.put("mft_crn_psn_nickname", "SYNTHETIC BRANCH A");
        row.put("synthetic_fixture", true);
        return row;
    }
}
