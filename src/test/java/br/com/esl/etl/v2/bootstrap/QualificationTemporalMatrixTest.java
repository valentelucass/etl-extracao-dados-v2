package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class QualificationTemporalMatrixTest {
    @Test
    void packagedMatrixMatchesAllFiveCurrentPoliciesBytePinsAndDocuments() throws Exception {
        final var json = new ObjectMapper();
        final var matrix =
                json.readTree(
                        Files.readAllBytes(
                                Path.of(
                                        "src/main/resources/analytic-laboratory/temporal-matrix-v2.synthetic.json")));
        assertEquals("qualification-temporal-matrix-v2", matrix.path("version").textValue());
        assertEquals(5, matrix.path("workloads").size());
        for (final var row : matrix.path("workloads")) {
            final var source = Path.of(row.path("path").textValue());
            assertEquals(QualificationJson.sha256(source), row.path("sha256").textValue());
            assertEquals(json.readTree(Files.readAllBytes(source)), row.path("document"));
        }
        assertEquals(
                java.util.Set.of(
                        "coletas", "fretes", "manifestos", "cotacoes", "localizacao_cargas"),
                QualificationTemporalPolicyCatalog.policies().stream()
                        .map(operation -> operation.workload)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void captureWindowKeepsCivilLookbackSeparateFromPublicationAndRejectsUnboundedRange() {
        final var start = Instant.parse("2036-04-02T03:00:00Z");
        final var end = Instant.parse("2036-04-03T03:00:00Z");
        final var window =
                LaboratoryCaptureWindow.planned(
                        new RuntimeTemporalPlanner.Window(
                                start, end, start.minusSeconds(3600), end, end.plusSeconds(60)),
                        ZoneId.of("America/Sao_Paulo"));
        assertEquals(start, window.start());
        assertEquals(LocalDate.of(2036, 4, 1), window.dates().startInclusive());
        assertEquals(LocalDate.of(2036, 4, 2), window.dates().endInclusive());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LaboratoryCaptureWindow(
                                start, start, window.dates(), RuntimeWindowStrategy.INTERVAL));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LaboratoryCaptureWindow(
                                start,
                                start.plusSeconds(33L * 86400),
                                window.dates(),
                                RuntimeWindowStrategy.INTERVAL));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LaboratoryCaptureWindow(
                                start,
                                end,
                                new BusinessDateRange(
                                        LocalDate.of(2036, 1, 1), LocalDate.of(2036, 4, 1)),
                                RuntimeWindowStrategy.INTERVAL));
    }
}
