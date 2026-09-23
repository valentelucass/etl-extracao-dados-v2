package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreightAnalyticAttributesMapper;
import br.com.esl.etl.v2.plataforma.analitico.FreightSupplementObservation;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcFreightAnalyticAttributes;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryFreightAttributesIT {
    @Test
    void maximumDeclaredUnicodeWidthsFitAnActualSqlRowWithoutTruncation() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            final var data = data();
            final var attributes = (ObjectNode) data.path("attributes");
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT TOP(0) * FROM stg.analytic_freight_attributes")) {
                sql.setQueryTimeout(10);
                try (var rows = sql.executeQuery()) {
                    final var metadata = rows.getMetaData();
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        final String name = metadata.getColumnName(column);
                        if (attributes.has(name)
                                && metadata.getColumnType(column) == java.sql.Types.NVARCHAR) {
                            attributes.put(name, "漢".repeat(metadata.getPrecision(column)));
                        }
                    }
                }
            }
            assertTrue(observation(data, 1).attributes().valid());
            assertEquals(
                    1,
                    new JdbcFreightAnalyticAttributes(session)
                            .captureBatch(
                                    fixture.run(),
                                    fixture.freight(),
                                    List.of(observation(data, 1)),
                                    CancellationToken.none()));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM core.ufn_analytic_freight_attributes(?)")) {
                sql.setString(1, fixture.run().toString());
                sql.setQueryTimeout(10);
                try (var rows = sql.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("READY", rows.getString("disposition"));
                    final var names = attributes.fieldNames();
                    while (names.hasNext()) {
                        final String name = names.next();
                        assertEquals(
                                attributes.path(name).asText(),
                                rows.getString(name + "_raw"),
                                name);
                        if (attributes.path(name).asText().startsWith("漢")) {
                            assertEquals(
                                    attributes.path(name).asText(), rows.getString(name), name);
                        }
                    }
                }
            }
        }
    }

    @Test
    void allNinetySourceAttributesPersistWithPhysicalTypesWireRawAndCaptureLineage()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            final var data = data();
            final var observation = observation(data, 1);
            assertTrue(observation.attributes().valid());
            final var repository = new JdbcFreightAnalyticAttributes(session);
            assertEquals(
                    1,
                    repository.captureBatch(
                            fixture.run(),
                            fixture.freight(),
                            List.of(observation),
                            CancellationToken.none()));
            assertEquals(
                    1,
                    repository.captureBatch(
                            fixture.run(),
                            fixture.freight(),
                            List.of(observation),
                            CancellationToken.none()));
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM core.ufn_analytic_freight_attributes(?)")) {
                sql.setString(1, fixture.run().toString());
                sql.setQueryTimeout(10);
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals(461, row.getMetaData().getColumnCount());
                    assertEquals("READY", row.getString("disposition"));
                    assertEquals(
                            fixture.freight().toString(),
                            row.getString("source_execution").toLowerCase(java.util.Locale.ROOT));
                    int fields = 0;
                    final var names = data.path("attributes").fieldNames();
                    while (names.hasNext()) {
                        final String name = names.next();
                        assertEquals("VALUE", row.getString(name + "_p"), name);
                        assertEquals(
                                data.path("attributes").path(name).asText(),
                                row.getString(name + "_raw"),
                                name);
                        final Object actual = row.getObject(name);
                        assertTrue(actual != null, name);
                        if (actual instanceof java.math.BigDecimal decimal) {
                            assertEquals(
                                    0,
                                    new java.math.BigDecimal(
                                                    data.path("attributes").path(name).asText())
                                            .compareTo(decimal),
                                    name);
                            assertEquals(8, row.getMetaData().getScale(row.findColumn(name)), name);
                            assertEquals(
                                    28, row.getMetaData().getPrecision(row.findColumn(name)), name);
                        } else {
                            assertEquals(
                                    data.path("attributes").path(name).asText(),
                                    actual.toString(),
                                    name);
                        }
                        fields++;
                    }
                    assertEquals(90, fields);
                    assertTrue(!row.next());
                }
            }
        }
    }

    @Test
    void conflictingRevisionAndInvalidScalarsCannotProduceReadyAttributes() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            final var repository = new JdbcFreightAnalyticAttributes(session);
            repository.captureBatch(
                    fixture.run(),
                    fixture.freight(),
                    List.of(observation(data(), 1)),
                    CancellationToken.none());
            final var conflicting = data();
            ((ObjectNode) conflicting.path("attributes")).put("total_cubic_volume", "17.00000000");
            repository.captureBatch(
                    fixture.run(),
                    fixture.freight(),
                    List.of(observation(conflicting, 1)),
                    CancellationToken.none());
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.ufn_analytic_freight_attributes(?) WHERE disposition='A"
                                    + "TTRIBUTE_CONFLICT'",
                            fixture.run()));
            final var invalid = data();
            ((ObjectNode) invalid.path("attributes")).put("total_cubic_volume", "1.123456789");
            repository.captureBatch(
                    fixture.run(),
                    fixture.freight(),
                    List.of(observation(invalid, 2)),
                    CancellationToken.none());
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.ufn_analytic_freight_attributes(?) WHERE disposition='I"
                                    + "NVALID_ATTRIBUTES'"
                                    + " AND total_cubic_volume_raw='1.123456789' AND total_cubic_volume IS NULL",
                            fixture.run()));
            repository.captureBatch(
                    fixture.run(),
                    fixture.freight(),
                    List.of(observation(data(), 3)),
                    CancellationToken.none());
            assertEquals(
                    1,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.ufn_analytic_freight_attributes(?) WHERE disposition='R"
                                    + "EADY' AND revision=3",
                            fixture.run()));
        }
    }

    @Test
    void nonCapturedKeyIsRefusedAndExistingObservationsAreImmutable() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var fixture = AnalyticLaboratoryDimensionsIT.start(session);
            final var repository = new JdbcFreightAnalyticAttributes(session);
            final var missing =
                    new FreightSupplementObservation(
                            "INTEGER:399999",
                            1,
                            observation(data(), 1).attributes(),
                            "synthetic-freight-attributes-v1");
            final var error =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    repository.captureBatch(
                                            fixture.run(),
                                            fixture.freight(),
                                            List.of(missing),
                                            CancellationToken.none()));
            assertEquals("ANA_FREIGHT_ATTRIBUTES_CAPTURE_REQUIRED", error.getMessage());
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM stg.analytic_freight_attributes WHERE run_id=?",
                            fixture.run()));
            repository.captureBatch(
                    fixture.run(),
                    fixture.freight(),
                    List.of(observation(data(), 1)),
                    CancellationToken.none());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "UPDATE stg.analytic_freight_attributes SET tipo_frete=N'changed' WHERE run_id=?")) {
                sql.setString(1, fixture.run().toString());
                sql.setQueryTimeout(10);
                assertEquals(
                        53572, assertThrows(SQLException.class, sql::executeUpdate).getErrorCode());
            }
        }
    }

    static ObjectNode data() throws Exception {
        try (var input =
                AnalyticLaboratoryFreightAttributesIT.class.getResourceAsStream(
                        "/analytic-laboratory/freight-attributes.synthetic.json")) {
            return (ObjectNode)
                    new ObjectMapper().readTree(java.util.Objects.requireNonNull(input));
        }
    }

    private static FreightSupplementObservation observation(
            final ObjectNode data, final int revision) {
        return new FreightSupplementObservation(
                "INTEGER:300001",
                revision,
                new FreightAnalyticAttributesMapper().map(data),
                "synthetic-freight-attributes-v1");
    }
}
