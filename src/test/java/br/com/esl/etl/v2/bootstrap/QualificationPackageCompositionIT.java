package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Offline package correspondence and integrity mutants; no worker or JDBC entrypoint. */
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
        final String nativeMember = "native/mssql-jdbc_auth-12.8.2.x64.dll";
        final String prefix = "native-loader-bound-";
        final int baseLength =
                fixture.root()
                        .getParent()
                        .getParent()
                        .resolve(prefix + "x".repeat(18))
                        .resolve("payload")
                        .resolve(nativeMember)
                        .toString()
                        .length();
        final int padding = 241 - baseLength;
        assertTrue(padding > 0 && padding <= 120);
        final var longPath = fixture.fork(prefix + "x".repeat(padding));
        assertEquals(241, longPath.root().resolve(nativeMember).toString().length());
        assertEquals(
                "QUAL_PACKAGE_NATIVE_PATH_LIMIT",
                assertThrows(IllegalArgumentException.class, longPath::verify).getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.verify().verifyRunningArtifact(QualificationLaboratoryMain.class));
    }

    @Test
    void resealedExternalV105ContractStillHasToMatchTheJarResource() throws Exception {
        final var physicalV105 = fixture.fork();
        physicalV105.mutate(
                "contracts/physical-columns.v105.json",
                value -> ((ObjectNode) value.path("columns").get(16)).put("bytes", 28));
        assertEquals(
                "QUAL_PACKAGE_JAR_RESOURCE_HASH",
                assertThrows(IllegalArgumentException.class, physicalV105::verify).getMessage());
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
