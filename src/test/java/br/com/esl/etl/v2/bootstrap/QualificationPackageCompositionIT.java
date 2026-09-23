package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationProcessEvidence;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Actual packaged classes/dependencies, real SQL composition, and independent integrity mutants.
 */
class QualificationPackageCompositionIT {
    private static QualificationPackageFixture fixture;

    @BeforeAll
    static void packageFromThisMavenLifecycle() throws Exception {
        fixture = QualificationPackageFixture.create();
        fixture.verify();
    }

    @Test
    void verifiedPackageRejectsCrossDocumentDriftEvenWithResealedOuterHashes() throws Exception {
        final var incompleteSchema = fixture.fork();
        incompleteSchema.mutate(
                "schema/schema-index.json",
                value -> ((ArrayNode) value.path("migrations")).remove(98));
        assertEquals(
                "QUAL_JSON_ARRAY",
                assertThrows(IllegalArgumentException.class, incompleteSchema::verify)
                        .getMessage());
        final var schema = fixture.fork();
        schema.mutate(
                "schema/schema-index.json", value -> value.put("installationAutomatic", true));
        assertEquals(
                "QUAL_SCHEMA_INDEX_POLICY",
                assertThrows(IllegalArgumentException.class, schema::verify).getMessage());
        final var provenance = fixture.fork();
        provenance.mutate("provenance.json", value -> value.put("signed", true));
        assertEquals(
                "QUAL_PROVENANCE_SCOPE",
                assertThrows(IllegalArgumentException.class, provenance::verify).getMessage());
        final var sbom = fixture.fork();
        sbom.mutate(
                "sbom.cdx.json",
                value -> ((ObjectNode) value.path("components").get(0)).put("version", "999"));
        assertEquals(
                "QUAL_SBOM_LOCK_CORRESPONDENCE",
                assertThrows(IllegalArgumentException.class, sbom::verify).getMessage());
        final var oracle = fixture.fork();
        oracle.mutate(
                "oracles/outputs.synthetic.json", value -> value.put("foreignRevision", true));
        assertEquals(
                "QUAL_PACKAGE_JAR_RESOURCE_HASH",
                assertThrows(IllegalArgumentException.class, oracle::verify).getMessage());
        final var extra = fixture.fork();
        Files.writeString(extra.root().resolve("undeclared.txt"), "synthetic extra");
        assertEquals(
                "QUAL_PACKAGE_CONTENT_SET",
                assertThrows(IllegalArgumentException.class, extra::verify).getMessage());
        final var tampered = fixture.fork();
        Files.writeString(tampered.root().resolve("config/config.synthetic.json"), "{}");
        assertEquals(
                "QUAL_PACKAGE_MEMBER_HASH",
                assertThrows(IllegalArgumentException.class, tampered::verify).getMessage());
        final var packagePins = QualificationCampaign.parse(fixture.campaign()).pins();
        fixture.verify().verifyPins(packagePins);
        final var longPath = fixture.fork("native-loader-bound-" + "x".repeat(80));
        assertEquals(
                "QUAL_PACKAGE_NATIVE_PATH_LIMIT",
                assertThrows(IllegalArgumentException.class, longPath::verify).getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.verify().verifyRunningArtifact(QualificationLaboratoryMain.class));
    }

    @ParameterizedTest
    @CsvSource({
        "SCENARIO,BOOTSTRAP",
        "REPLAY,REPLAY",
        "RECOMPOSE,BACKFILL",
        "ABSENCE,BOOTSTRAP",
        "VARIANTS,BOOTSTRAP"
    })
    @Timeout(180)
    void workerConsumesDeclaredActionAndProducesBoundRollbackReceipt(
            final String action, final String mode) throws Exception {
        final var payload = fixture.verify();
        final var document = fixture.campaign();
        ((ObjectNode) document.path("cases").get(0)).put("action", action).put("mode", mode);
        final var campaign = QualificationCampaign.parse(document);
        final var configuration =
                QualificationConfiguration.read(
                        payload.root().resolve("config/config.synthetic.json"));
        final var files = controls(document);
        final var item = campaign.cases().get(0);
        final var nonce = UUID.randomUUID();
        final var directory = files.attempt(item.id(), true);
        QualificationControlFiles.atomic(
                directory.resolve("intent.json"),
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "qualification-intent-v1")
                        .put("case", item.id())
                        .put("nonce", nonce.toString())
                        .put(
                                "campaign",
                                QualificationJson.sha256(files.root().resolve("campaign.json")))
                        .put("package", payload.manifestSha256())
                        .put(
                                "configuration",
                                QualificationJson.sha256(
                                        files.root().resolve("configuration.json")))
                        .put("caseSeconds", configuration.caseSeconds()));
        final var current = ProcessHandle.current();
        QualificationControlFiles.atomic(
                directory.resolve("process.json"),
                JsonNodeFactory.instance
                        .objectNode()
                        .put("pid", current.pid())
                        .put("start", current.info().startInstant().orElseThrow().toString())
                        .put("nonce", nonce.toString())
                        .put("java", current.info().command().orElseThrow())
                        .put("jar", payload.members().get("etl-dataexport-v2.jar").sha256()));
        assertEquals(
                0, QualificationWorker.run(payload, campaign, configuration, files, item, nonce));
        final var receipt = QualificationJson.read(directory.resolve("receipt.json"), 1048576);
        assertEquals("PASS_LOCAL", receipt.path("state").asText(), receipt.toString());
        assertTrue(receipt.path("rollback").asBoolean());
        assertEquals(receipt.path("before"), receipt.path("after"));
        assertEquals(nonce.toString(), receipt.path("nonce").asText());
        assertEquals(current.pid(), receipt.path("pid").asLong());
        assertTrue(receipt.path("spid").asInt() > 0);
        QualificationProcessEvidence.coverage(receipt.path("report"));
        assertEquals(19, receipt.path("report").path("outputs").size());
        assertEquals(35, receipt.path("report").path("scopes").size());
        assertEquals(11, receipt.path("report").path("metrics").path("inputs").size());
        assertEquals(0, receipt.path("report").path("metrics").path("inFlight").asInt());
        if (action.equals("ABSENCE")) {
            assertTrue(receipt.path("report").path("absence").path("passed").asBoolean());
        }
        if (action.equals("VARIANTS")) {
            assertEquals("VALUES_AND_NULLS", receipt.path("report").path("variant").asText());
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QualificationWorker.run(
                                payload, campaign, configuration, files, item, UUID.randomUUID()));
    }

    @ParameterizedTest
    @CsvSource({"NONE,PASS_LOCAL", "BEFORE_SQL,CANCELLED", "BEFORE_RECEIPT,OUTCOME_UNKNOWN"})
    @Timeout(180)
    void supervisorRunsOwnedChildAndResumeObservesSealedOutcome(
            final String barrier, final String expected) throws Exception {
        final var payload = fixture.verify();
        final var document = fixture.campaign();
        ((ObjectNode) document.path("cases").get(0))
                .put("barrier", barrier)
                .put("expected", expected);
        final var campaign = QualificationCampaign.parse(document);
        final var configuration =
                QualificationConfiguration.read(
                        payload.root().resolve("config/config.synthetic.json"));
        final var files = controls(document);
        final var supervisor =
                new QualificationSupervisor(payload, campaign, configuration, files, true);
        assertFalse(supervisor.status().path("testPassed").asBoolean());
        final var result = supervisor.execute();
        assertTrue(result.path("testPassed").asBoolean(), result.toString());
        assertEquals(expected, result.path("cases").get(0).path("state").asText());
        final String head = result.path("journalHead").asText();
        final var resumed =
                new QualificationSupervisor(payload, campaign, configuration, files, false)
                        .resume();
        assertEquals(result, resumed);
        assertEquals(head, resumed.path("journalHead").asText());
        final var directory = files.attempt(campaign.cases().get(0).id(), false);
        assertTrue(Files.exists(directory.resolve("process.json")));
        assertEquals(
                !barrier.equals("BEFORE_RECEIPT"), Files.exists(directory.resolve("receipt.json")));
        Files.writeString(directory.resolve("stdout.log"), "tampered synthetic log");
        assertEquals(
                "QUAL_PROCESS_EVIDENCE_CHAIN",
                assertThrows(IllegalArgumentException.class, supervisor::status).getMessage());
    }

    @Test
    void admissionBlocksBeforeChildAndControlInventoryRejectsUndeclaredEvidence() throws Exception {
        final var payload = fixture.verify();
        final var document = fixture.campaign();
        ((ObjectNode) document.path("cases").get(0))
                .put("tick", "2036-03-01T12:00:00Z")
                .put("expected", "BLOCKED_DEPENDENCY");
        final var campaign = QualificationCampaign.parse(document);
        final var configuration =
                QualificationConfiguration.read(
                        payload.root().resolve("config/config.synthetic.json"));
        final var files = controls(document);
        final var supervisor =
                new QualificationSupervisor(payload, campaign, configuration, files, true);
        final var result = supervisor.execute();
        assertTrue(result.path("testPassed").asBoolean(), result.toString());
        assertEquals(result, supervisor.resume());
        assertFalse(Files.exists(files.attempt("bootstrap", false).resolve("process.json")));
        Files.writeString(files.root().resolve("foreign.json"), "{}");
        assertEquals(
                "QUAL_CONTROL_UNDECLARED_OR_TRUNCATED_FILE",
                assertThrows(IllegalArgumentException.class, supervisor::status).getMessage());
    }

    private static QualificationControlFiles controls(final ObjectNode campaign) throws Exception {
        final var root = fixture.root();
        final var files =
                new QualificationControlFiles(
                        root, root.resolveSibling("control-" + UUID.randomUUID()), true);
        QualificationControlFiles.atomic(files.root().resolve("campaign.json"), campaign);
        QualificationControlFiles.atomic(
                files.root().resolve("configuration.json"),
                QualificationJson.read(root.resolve("config/config.synthetic.json"), 8192));
        return files;
    }
}
