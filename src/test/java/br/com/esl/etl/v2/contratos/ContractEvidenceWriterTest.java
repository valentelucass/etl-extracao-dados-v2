package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseForm;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContractEvidenceWriterTest {

    @TempDir Path buildDirectory;

    @Test
    void writesOnlyTheSanitizedSummaryUnderTheBuildDirectory() throws Exception {
        final ContractEvidenceSummary summary =
                new ContractEvidenceSummary(
                        "synthetic-run-1",
                        "2026-08-25T00:00:00Z",
                        List.of(
                                new ContractTemplateEvidence(
                                        6908,
                                        200,
                                        false,
                                        List.of(
                                                new ContractMetadataEvidence(
                                                        "id", ContractMetadataType.INTEGER),
                                                new ContractMetadataEvidence(
                                                        "updated_at",
                                                        ContractMetadataType.DATE_TIME)),
                                        List.of(
                                                new ContractMetadataEvidence(
                                                        "picks.request_date",
                                                        ContractMetadataType.DATE)),
                                        List.of(DataExportTransport.GET_WITH_BODY),
                                        List.of(
                                                new ContractPayloadEvidence(
                                                        ContractPayloadObservation
                                                                .POPULATED_FIRST_APPROVED_PAGE_SIZE,
                                                        5,
                                                        200,
                                                        DataExportTransport.GET_WITH_BODY,
                                                        DataExportResponseForm.ROOT_OBJECT,
                                                        1,
                                                        List.of(
                                                                new ContractFieldEvidence(
                                                                        "id",
                                                                        1,
                                                                        0,
                                                                        List.of("integer"),
                                                                        List.of())))),
                                        Map.of(ContractEvidenceCheck.IDENTITY_VOLUME_EQUAL, true)),
                                new ContractTemplateEvidence(
                                        4924,
                                        200,
                                        false,
                                        List.of(
                                                new ContractMetadataEvidence(
                                                        "cte_key", ContractMetadataType.STRING)),
                                        List.of(
                                                new ContractMetadataEvidence(
                                                        "closed_at", ContractMetadataType.DATE)),
                                        List.of(DataExportTransport.POST_JSON),
                                        List.of(
                                                new ContractPayloadEvidence(
                                                        ContractPayloadObservation
                                                                .AUXILIARY_4924_CLOSED_WINDOW,
                                                        1,
                                                        200,
                                                        DataExportTransport.POST_JSON,
                                                        DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                                                        1,
                                                        List.of(
                                                                new ContractFieldEvidence(
                                                                        "cte_key",
                                                                        1,
                                                                        0,
                                                                        List.of("string"),
                                                                        List.of("text"))))),
                                        Map.of(
                                                ContractEvidenceCheck
                                                        .CTE_INVOICE_RELATION_CONFIRMED,
                                                false))));

        final Path targetDirectory = buildDirectory.resolve("target");
        final Path summaryFile =
                ContractEvidenceWriter.forTargetDirectory(targetDirectory).write(summary);
        final String content = Files.readString(summaryFile);

        assertTrue(summaryFile.startsWith(targetDirectory));
        assertTrue(content.contains("IDENTITY_VOLUME_EQUAL"));
        assertTrue(content.contains("POPULATED_FIRST_APPROVED_PAGE_SIZE"));
        assertTrue(content.contains("crossDomainEvidence"));
        assertTrue(content.contains("AUXILIARY_4924_CLOSED_WINDOW"));
        assertFalse(content.contains("900001"));
        assertFalse(content.contains("synthetic-dataexport-token"));
        assertFalse(content.contains("https://"));
    }

    @Test
    void rejectsAnEvidenceRootOutsideTargetAndFreeTextThatCouldLeakRemoteData() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ContractEvidenceWriter.forTargetDirectory(buildDirectory));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ContractFieldEvidence(
                                "https://tenant.example.test",
                                1,
                                0,
                                List.of("string"),
                                List.of("text")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContractEvidenceSummary("synthetic-run-1", "not-a-timestamp", List.of()));
    }
}
