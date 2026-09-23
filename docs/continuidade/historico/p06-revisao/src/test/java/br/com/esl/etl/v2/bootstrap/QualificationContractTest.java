package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationPlanner;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QualificationContractTest {
    static ObjectNode campaign() throws Exception {
        return (ObjectNode)
                QualificationJson.parse(
                        ("""
                {"version":"qualification-campaign-v1","id":"synthetic-one",
                 "pins":{"revision":"%s","jar":"%s","schema":"%s","contracts":"%s",
                         "fixture":"%s","oracle":"%s"},"roots":2,"pageSize":2,
                 "maximumSeconds":300,"cases":[{
                    "id":"bootstrap","wave":"all","dependsOn":[],"action":"SCENARIO",
                    "mode":"BOOTSTRAP","fault":"NONE","barrier":"NONE","outputs":["SQL-01"],
                    "tick":"2036-04-02T12:00:00Z","start":"2036-04-01","endExclusive":"2036-04-02",
                    "zone":"America/Sao_Paulo","lookbackSeconds":3600,"deadlineSeconds":86400,
                    "maximumCatchUp":2,"blackouts":[],"expected":"PASS_LOCAL"}]}
                """)
                                .formatted(
                                        "a".repeat(64),
                                        "b".repeat(64),
                                        "c".repeat(64),
                                        "d".repeat(64),
                                        "e".repeat(64),
                                        "f".repeat(64))
                                .getBytes(StandardCharsets.UTF_8),
                        131072);
    }

    @Test
    void actionCannotSilentlySkipItsReplayCorrectionOrAbsenceMode() throws Exception {
        for (final var action : List.of("REPLAY", "RECOMPOSE")) {
            final var invalid = campaign();
            ((ObjectNode) invalid.path("cases").get(0)).put("action", action);
            assertThrows(
                    IllegalArgumentException.class, () -> QualificationCampaign.parse(invalid));
        }
        final var absence = campaign();
        ((ObjectNode) absence.path("cases").get(0))
                .put("action", "ABSENCE")
                .put("mode", "INCREMENTAL");
        assertThrows(IllegalArgumentException.class, () -> QualificationCampaign.parse(absence));
    }

    @Test
    void strictParserRejectsDuplicateExtraTruncatedMalformedUtf8AndCoercion() throws Exception {
        for (final String invalid : List.of("{\"a\":1,\"a\":2}", "{}{}", "{", "\ufeff{}")) {
            assertThrows(
                    Exception.class,
                    () -> QualificationJson.parse(invalid.getBytes(StandardCharsets.UTF_8), 128));
        }
        assertThrows(Exception.class, () -> QualificationJson.parse(new byte[] {(byte) 0xc0}, 128));
        assertThrows(Exception.class, () -> QualificationJson.parse(new byte[129], 128));
        final var extra = campaign().put("command", "arbitrary");
        assertThrows(IllegalArgumentException.class, () -> QualificationCampaign.parse(extra));
        final var coercion = campaign().put("roots", "2");
        assertThrows(IllegalArgumentException.class, () -> QualificationCampaign.parse(coercion));
    }

    @Test
    void closedCampaignRejectsDivergentPinsCountsEnumsAndDependencyCycles() throws Exception {
        assertEquals(1, QualificationCampaign.parse(campaign()).cases().size());
        final var badPin = campaign();
        ((ObjectNode) badPin.path("pins")).put("fixture", "unknown");
        assertThrows(IllegalArgumentException.class, () -> QualificationCampaign.parse(badPin));
        final var self = campaign();
        ((ObjectNode) self.path("cases").get(0)).putArray("dependsOn").add("bootstrap");
        assertThrows(IllegalArgumentException.class, () -> QualificationCampaign.parse(self));
        final var duplicate = campaign();
        ((com.fasterxml.jackson.databind.node.ArrayNode) duplicate.path("cases"))
                .add(duplicate.path("cases").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> QualificationCampaign.parse(duplicate));
        for (final String key : List.of("roots", "pageSize", "maximumSeconds")) {
            final var invalid = campaign().put(key, 100000);
            assertThrows(
                    IllegalArgumentException.class, () -> QualificationCampaign.parse(invalid));
        }
        final var badMode = campaign();
        ((ObjectNode) badMode.path("cases").get(0)).put("mode", "SWEEP");
        assertThrows(IllegalArgumentException.class, () -> QualificationCampaign.parse(badMode));
    }

    @Test
    void configurationRejectsTargetLocksVersionAndIncoherentTimeoutsBeforeJdbc() throws Exception {
        final Path path =
                Path.of("src/main/resources/qualification-laboratory/config.synthetic.json");
        assertTrue(
                QualificationConfiguration.read(path)
                        .jdbcUrl()
                        .startsWith("jdbc:sqlserver://localhost;"));
        for (final var key : List.of("host", "database", "version")) {
            final var invalid = (ObjectNode) QualificationJson.read(path, 8192);
            invalid.put(key, "forbidden");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> QualificationConfiguration.parse(invalid));
        }
        for (final var key :
                List.of("profileActive", "integrationEnabled", "syntheticOnly", "rollbackOnly")) {
            final var invalid = (ObjectNode) QualificationJson.read(path, 8192);
            invalid.put(key, false);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> QualificationConfiguration.parse(invalid));
        }
        final var timeout = (ObjectNode) QualificationJson.read(path, 8192);
        timeout.put("socketMillis", 2000);
        assertThrows(
                IllegalArgumentException.class, () -> QualificationConfiguration.parse(timeout));
        final var schema = (ObjectNode) QualificationJson.read(path, 8192);
        schema.put("schemaVersion", 100);
        assertThrows(
                IllegalArgumentException.class, () -> QualificationConfiguration.parse(schema));
    }

    @Test
    void independentPassAndSuccessfulRefusalCannotApproveDependent() {
        final var cap =
                new QualificationGate("CAP", QualificationGate.State.PASS_LOCAL, "PROVEN", "JDBC");
        final var raster =
                new QualificationGate(
                        "RASTER", QualificationGate.State.FAILED, "INCOMPLETE", "JDBC");
        final var observed = Map.of("CAP", cap, "RASTER", raster);
        assertEquals(
                QualificationGate.State.PASS_LOCAL,
                QualificationGate.aggregate("FINANCIAL", List.of("CAP"), observed).state());
        assertEquals(
                QualificationGate.State.BLOCKED_DEPENDENCY,
                QualificationGate.aggregate("SQL_13", List.of("RASTER"), observed).state());
        assertEquals(
                QualificationGate.State.BLOCKED_DEPENDENCY,
                QualificationGate.aggregate("ALL", List.of("CAP", "MISSING"), observed).state());
        for (final var state : QualificationGate.State.values()) {
            if (state == QualificationGate.State.PASS_LOCAL) {
                continue;
            }
            final var gate = new QualificationGate("RASTER", state, "EXPECTED_REFUSAL", "PROCESS");
            assertEquals(
                    QualificationGate.State.BLOCKED_DEPENDENCY,
                    QualificationGate.aggregate("ALL", List.of("RASTER"), Map.of("RASTER", gate))
                            .state());
        }
    }

    @Test
    void existingPlannerConsumesTickBlackoutDeadlineCatchupAndOverlap() throws Exception {
        final var original = QualificationCampaign.parse(campaign()).cases().get(0);
        final var plan = QualificationPlanner.plan(original);
        assertEquals(QualificationGate.State.PASS_LOCAL, plan.state());
        assertEquals(
                3600,
                java.time.Duration.between(
                                plan.windows().get(0).extractionStart(),
                                plan.windows().get(0).partitionStart())
                        .getSeconds());
        final var blackout = campaign();
        ((ObjectNode) blackout.path("cases").get(0)).putArray("blackouts").add("2036-04-01");
        assertEquals(
                "BLACKOUT",
                QualificationPlanner.plan(QualificationCampaign.parse(blackout).cases().get(0))
                        .reason());
        final var late = campaign();
        ((ObjectNode) late.path("cases").get(0)).put("tick", "2036-04-15T12:00:00Z");
        final var expired =
                QualificationPlanner.plan(QualificationCampaign.parse(late).cases().get(0));
        assertEquals("LOGICAL_DEADLINE_EXCEEDED", expired.reason());
        assertTrue(expired.backlog());
        final var early = campaign();
        ((ObjectNode) early.path("cases").get(0)).put("tick", "2036-04-01T12:00:00Z");
        final var deferred =
                QualificationPlanner.plan(QualificationCampaign.parse(early).cases().get(0));
        assertEquals("WINDOW_NOT_DUE", deferred.reason());
        assertFalse(deferred.backlog());
    }
}
