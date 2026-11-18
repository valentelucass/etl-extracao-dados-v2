package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoricalQualifiedPolicyBindingTest {
    @TempDir Path directory;

    private ObjectNode[] fixture() throws Exception {
        return fixture("zeroPendingRequiredForQualification");
    }

    private ObjectNode[] fixture(final String zeroPendingField) throws Exception {
        final var fixtureDirectory = Files.createTempDirectory(directory, "policy-");
        final var time =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("approved", true)
                        .put("twoTimestampColumnsQualifiedAtWorkbookPrecision", true)
                        .put("epoch", "1900")
                        .put("numFmtId", "164")
                        .put("applyNumberFormat", "1");
        time.putArray("items").add("item_135309").add("item_131395");
        final var timePin =
                HistoricalContentFixture.upper(
                        HistoricalContentFixture.write(fixtureDirectory, "timestamp.json", time));
        final var timeAcceptance =
                HistoricalContentFixture.upper(
                        HistoricalContentFixture.write(
                                fixtureDirectory,
                                "timestamp-acceptance.json",
                                ExportValidationSupport.JSON
                                        .createObjectNode()
                                        .put("synthetic", true)));
        final var record =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("recordKind", "JOINT31_131_POLICY_AND_ADVERSARIAL_GUARDS")
                        .put("allExistingDecisionsReused", true)
                        .put(zeroPendingField, true)
                        .put("ordinalPairingApplied", false)
                        .put("deduplicationApplied", false)
                        .put("originalAtomsAndAxesChanged", false);
        final var order = record.putArray("tupleItemOrder");
        ColetasCommonAdapter.catalog()
                .required("columns")
                .forEach(column -> order.add(column.path("itemId").asText()));
        record.set("timestampDisplayPolicy", timePin);
        record.set("timestampDisplayAcceptance", timeAcceptance);
        record.putArray("nullOmissionFieldAllowlist").add("item_221149").add("item_135308");
        record.putArray("dateNullBlankFieldAllowlist").add("item_124818");
        final byte[] logical =
                (ExportValidationSupport.JSON.writeValueAsString(record) + "\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(logical);
        }
        final var reportPath = fixtureDirectory.resolve("qualified.jsonl.gz").toAbsolutePath();
        Files.write(reportPath, bytes.toByteArray());
        final var reportPin =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("path", reportPath.toString())
                        .put("bytes", bytes.size())
                        .put(
                                "sha256",
                                ExportValidationSupport.sha256(bytes.toByteArray())
                                        .toUpperCase(java.util.Locale.ROOT));
        final var pair = ExportValidationSupport.JSON.createObjectNode();
        final var report =
                pair.putObject("businessPins")
                        .putObject("fullComparisonReport")
                        .put("complete", true)
                        .put("logicalBytes", logical.length)
                        .put("records", 1)
                        .put(
                                "logicalSha256",
                                ExportValidationSupport.sha256(logical)
                                        .toUpperCase(java.util.Locale.ROOT));
        report.set("pin", reportPin);
        final var evidence = pair.putObject("independentEvidence");
        evidence.set("timestampPolicy", timePin);
        evidence.set("independentTimestampDisplayAcceptance", timeAcceptance);
        final var policy = ExportValidationSupport.JSON.createObjectNode();
        policy.putArray("nullOmissionItems").add("item_135308");
        policy.putArray("dateBlankItems").add("item_124818");
        policy.set("timestampIntegerSecondDisplayItems", time.required("items"));
        return new ObjectNode[] {pair, policy};
    }

    @Test
    void exactFrozen131ListsAreAdmitted() throws Exception {
        final var values = fixture();
        HistoricalContentControlPreparation.requireQualifiedPolicy(
                values[1], values[0], new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
        final var wrongField = fixture("zeroPendingRequired");
        assertThrows(
                Exception.class,
                () ->
                        HistoricalContentControlPreparation.requireQualifiedPolicy(
                                wrongField[1],
                                wrongField[0],
                                new ExportValidationSupport.Budget(Duration.ofSeconds(10))));
    }

    @Test
    void acceptancePinDoesNotAuthorizeArbitraryDateBlankPolicy() throws Exception {
        final var values = fixture();
        values[1].withArray("dateBlankItems").add("item_123121");
        assertThrows(
                Exception.class,
                () ->
                        HistoricalContentControlPreparation.requireQualifiedPolicy(
                                values[1],
                                values[0],
                                new ExportValidationSupport.Budget(Duration.ofSeconds(10))));
    }

    @Test
    void acceptancePinDoesNotAuthorizeArbitraryTimestampPolicy() throws Exception {
        final var values = fixture();
        values[1].withArray("timestampIntegerSecondDisplayItems").removeAll();
        assertThrows(
                Exception.class,
                () ->
                        HistoricalContentControlPreparation.requireQualifiedPolicy(
                                values[1],
                                values[0],
                                new ExportValidationSupport.Budget(Duration.ofSeconds(10))));
    }

    @Test
    void acceptancePinDoesNotAuthorizeArbitraryAbsencePolicy() throws Exception {
        final var values = fixture();
        values[1].withArray("nullOmissionItems").add("item_123123");
        assertThrows(
                Exception.class,
                () ->
                        HistoricalContentControlPreparation.requireQualifiedPolicy(
                                values[1],
                                values[0],
                                new ExportValidationSupport.Budget(Duration.ofSeconds(10))));
    }

    @Test
    void missingPinnedTimestampAcceptanceRefuses() throws Exception {
        final var values = fixture();
        Files.delete(
                Path.of(
                        values[0]
                                .path("independentEvidence")
                                .path("independentTimestampDisplayAcceptance")
                                .path("path")
                                .asText()));
        assertThrows(
                Exception.class,
                () ->
                        HistoricalContentControlPreparation.requireQualifiedPolicy(
                                values[1],
                                values[0],
                                new ExportValidationSupport.Budget(Duration.ofSeconds(10))));
    }

    @Test
    void driftedPinnedTimestampAcceptanceRefuses() throws Exception {
        final var values = fixture();
        Files.writeString(
                Path.of(
                        values[0]
                                .path("independentEvidence")
                                .path("independentTimestampDisplayAcceptance")
                                .path("path")
                                .asText()),
                "{\"changed\":true}");
        assertThrows(
                Exception.class,
                () ->
                        HistoricalContentControlPreparation.requireQualifiedPolicy(
                                values[1],
                                values[0],
                                new ExportValidationSupport.Budget(Duration.ofSeconds(10))));
    }
}
