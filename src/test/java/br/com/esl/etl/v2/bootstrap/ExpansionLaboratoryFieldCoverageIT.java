package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Every declared field crosses mapper, JDBC binding order and its own typed SQL destination. */
class ExpansionLaboratoryFieldCoverageIT {
    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void allOneHundredFiftyOneFieldsHaveIndependentTypedReadback(final DataExportTemplate template)
            throws Exception {
        final var mapper = new ObjectMapper();
        final var catalog =
                mapper.readTree(
                        Path.of("docs/catalogos/macrobloco-expansao/campos-tipados.json").toFile());
        JsonNode fields = null;
        for (final var spec : catalog.path("specs")) {
            if (spec.path("template").asText().equals(template.name())) {
                fields = spec.path("fields");
            }
        }
        if (fields == null) {
            throw new IllegalArgumentException("EXP_FIELD_SPEC_REQUIRED");
        }
        assertEquals(
                switch (template) {
                    case CONTAS_A_PAGAR -> 28;
                    case FATURAS_POR_CLIENTE -> 53;
                    case INVENTARIO -> 26;
                    case SINISTROS -> 44;
                    default -> throw new IllegalArgumentException("EXP_TEST_TEMPLATE");
                },
                fields.size());
        final ObjectNode data = mapper.createObjectNode();
        for (final var field : fields) {
            final String name = field.path("name").asText();
            assertTrue(name.matches("[a-z][a-z0-9_]{0,80}"));
            switch (field.path("kind").asText()) {
                case "TEXT" -> data.put(name, "SYNTHETIC-" + name);
                case "INTEGER", "IDENTIFIER" -> data.put(name, 123);
                case "DECIMAL" -> data.put(name, "12.34567890");
                case "BOOLEAN" -> data.put(name, true);
                case "DATE" -> data.put(name, "2036-04-01");
                case "INSTANT" -> data.put(name, "2036-04-01T12:00:00.123456789-03:00");
                case "TIME" -> data.put(name, "12:34:56.123456789");
                case "STRINGS" -> data.putArray(name).add("SYNTHETIC-A").add("SYNTHETIC-B");
                default -> throw new IllegalArgumentException("EXP_TEST_FIELD_KIND");
            }
        }
        final var envelope = ExpansionLaboratoryFixtures.envelope(template, data, 1, 1, 1);
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 2, 100);
            final var capture =
                    runtime.capture(
                            template,
                            DATE,
                            ExecutionMode.BOOTSTRAP,
                            null,
                            new ExpansionSyntheticSource(
                                    page -> page == 1 ? "[" + envelope + "]" : "[]"),
                            NONE);
            assertEquals(1, capture.receipt().inserts());
            assertEquals(0, capture.receipt().quarantine());
            final String table =
                    "stg.expansion_lab_"
                            + JdbcExpansionLaboratory.vertical(template)
                                    .toLowerCase(java.util.Locale.ROOT);
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT * FROM "
                                            + table
                                            + " WHERE execution_id=? AND occurrence=1")) {
                sql.setQueryTimeout(10);
                sql.setString(1, capture.executionId().toString());
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    for (final var field : fields) {
                        assertField(row, data, field);
                    }
                }
            }
        }
    }

    private static void assertField(final ResultSet row, final JsonNode data, final JsonNode field)
            throws SQLException {
        final String name = field.path("name").asText();
        final var value = data.path(name);
        assertEquals("VALUE", row.getString(name + "_p"), name);
        final String wire =
                value.isBoolean()
                        ? "BOOLEAN"
                        : value.isIntegralNumber()
                                ? "INTEGER"
                                : value.isArray() ? "ARRAY" : "STRING";
        assertEquals(wire, row.getString(name + "_w"), name);
        assertEquals(
                value.isArray() ? value.toString() : value.asText(),
                row.getString(name + "_raw"),
                name);
        switch (field.path("kind").asText()) {
            case "TEXT", "INTEGER", "IDENTIFIER" ->
                    assertEquals(value.asText(), row.getString(name), name);
            case "DECIMAL" ->
                    assertEquals(
                            new BigDecimal(value.asText()).setScale(8),
                            row.getBigDecimal(name),
                            name);
            case "BOOLEAN" -> assertEquals(value.asBoolean(), row.getBoolean(name), name);
            case "DATE" ->
                    assertEquals(
                            LocalDate.parse(value.asText()), row.getDate(name).toLocalDate(), name);
            case "INSTANT" -> {
                final var instant = OffsetDateTime.parse(value.asText()).toInstant();
                assertEquals(instant.getEpochSecond(), row.getLong(name), name);
                assertEquals(instant.getNano(), row.getInt(name + "_nano"), name);
            }
            case "TIME" ->
                    assertEquals(
                            LocalTime.parse(value.asText()).toNanoOfDay(), row.getLong(name), name);
            case "STRINGS" -> assertEquals(2, row.getInt(name), name);
            default -> throw new IllegalArgumentException("EXP_TEST_FIELD_KIND");
        }
    }
}
