package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.graphql.AnalyticCollectionSupplementMapper;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSupplements;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSupplements.Binding;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryCollectionSupplementIT {
    @Test
    void namedDataExportFieldsTraverseExistingCaptureAndLateralValuesRemainTypedAndSeparate()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            final var capture = capture(session, fixture, 1);
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.relational_lab_capture WHERE run_id=? AND entity_name=N'"
                                    + "coletas' AND physical_rows=3 AND root_rows=1",
                            fixture.relational()));
            final var binding =
                    new Binding(
                            "INTEGER:200001",
                            capture.executionId(),
                            1,
                            AnalyticCollectionsFixtures.supplement(),
                            null,
                            null);
            final var gateway = new JdbcAnalyticCollectionSupplements(session);
            assertEquals(
                    1, gateway.bind(fixture.run(), List.of(binding), CancellationToken.none()));
            assertEquals(
                    1, gateway.bind(fixture.run(), List.of(binding), CancellationToken.none()));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM stg.analytic_collection_supplement WHERE run_id=? ORDER BY supplement_id")) {
                sql.setQueryTimeout(10);
                sql.setString(1, fixture.run().toString());
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    for (final String field :
                            List.of(
                                    "request_hour",
                                    "vehicle_type_id",
                                    "customer_name",
                                    "customer_document",
                                    "address_line",
                                    "address_number",
                                    "address_complement",
                                    "branch_source_id",
                                    "cancellation_user_id",
                                    "destroy_reason",
                                    "destroy_user_id",
                                    "status_updated_at")) {
                        assertNotNull(row.getObject(field));
                        assertEquals("VALUE", row.getString(field + "_p"));
                        assertNotNull(row.getString(field + "_raw"));
                    }
                    assertEquals(36000123456789L, row.getLong("request_hour"));
                    assertEquals(123456789, row.getInt("status_updated_at_nano"));
                    assertEquals("CLIENTE SINTÉTICO", row.getString("customer_name"));
                    assertEquals("7A", row.getString("address_number"));
                    assertTrue(!row.next());
                }
            }
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM stg.coleta_record c JOIN ctl.relational_lab_capture cap ON c"
                                    + "ap.execution_id=c.execution_id WHERE cap.run_id=? AND (JSON_VALUE(c.payload_json,'$.r"
                                    + "equestHour') IS NOT NULL OR JSON_QUERY(c.payload_json,'$.customer') IS NOT NULL)",
                            fixture.relational()));
        }
    }

    @Test
    void divergentRetryForeignCaptureAndUnboundUserFailWithoutLosingPriorState() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = start(session);
            final var capture = capture(session, fixture, 1);
            final var gateway = new JdbcAnalyticCollectionSupplements(session);
            final var original = AnalyticCollectionsFixtures.supplement();
            gateway.bind(
                    fixture.run(),
                    List.of(
                            new Binding(
                                    "INTEGER:200001",
                                    capture.executionId(),
                                    1,
                                    original,
                                    null,
                                    null)),
                    CancellationToken.none());
            final ObjectNode envelope;
            try (var input =
                    getClass()
                            .getResourceAsStream(
                                    "/analytic-laboratory/collection-supplement.synthetic.json")) {
                envelope = (ObjectNode) new ObjectMapper().readTree(input);
            }
            ((ObjectNode) envelope.path("data").path("customer"))
                    .put("name", "DIVERGÊNCIA SINTÉTICA");
            final var changed = new AnalyticCollectionSupplementMapper().map(envelope);
            assertEquals(
                    53735,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            gateway.bind(
                                                    fixture.run(),
                                                    List.of(
                                                            new Binding(
                                                                    "INTEGER:200001",
                                                                    capture.executionId(),
                                                                    1,
                                                                    changed,
                                                                    null,
                                                                    null)),
                                                    CancellationToken.none()))
                            .getErrorCode());
            final var other = start(session);
            assertEquals(
                    53732,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            gateway.bind(
                                                    other.run(),
                                                    List.of(
                                                            new Binding(
                                                                    "INTEGER:200001",
                                                                    capture.executionId(),
                                                                    1,
                                                                    original,
                                                                    null,
                                                                    null)),
                                                    CancellationToken.none()))
                            .getErrorCode());
            assertEquals(
                    53734,
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            gateway.bind(
                                                    fixture.run(),
                                                    List.of(
                                                            new Binding(
                                                                    "INTEGER:200001",
                                                                    capture.executionId(),
                                                                    2,
                                                                    original,
                                                                    "STRING:missing",
                                                                    null)),
                                                    CancellationToken.none()))
                            .getErrorCode());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM stg.analytic_collection_supplement WHERE run_id=? AND custom"
                                    + "er_name=N'CLIENTE SINTÉTICO'",
                            fixture.run()));
        }
    }

    static AnalyticLaboratoryDimensionsIT.Fixture start(
            final ColetaTemporalLaboratorySession session) throws Exception {
        return AnalyticLaboratoryDimensionsIT.start(
                session, RelationalSyntheticSource.analyticContracts());
    }

    static LocalRelationalRuntime.Capture capture(
            final ColetaTemporalLaboratorySession session,
            final AnalyticLaboratoryDimensionsIT.Fixture fixture,
            final int roots)
            throws SQLException {
        return new LocalRelationalRuntime(
                        session,
                        fixture.relational(),
                        new RelationalLaboratoryPolicy(
                                DATE, DATE.plusDays(2), 1000, 10, 3, 60, 2, 0, 2, 100),
                        CLOCK,
                        Clock.systemUTC())
                .capture(
                        DataExportTemplate.COLETAS,
                        DATE,
                        ExecutionMode.BOOTSTRAP,
                        null,
                        AnalyticCollectionsFixtures.source(1, roots, 2),
                        CancellationToken.none());
    }
}
