package br.com.esl.etl.v2.modulos.localizacaocargas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LocalizacaoCargaArchitectureBoundaryTest {

    @Test
    void packageHasNoRemoteDispatcherRelationOrOpenJsonBinding() throws Exception {
        final Path packageRoot =
                Path.of("src/main/java/br/com/esl/etl/v2/modulos/localizacaocargas");
        final StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.walk(packageRoot)) {
            for (final Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(file)).append('\n');
            }
        }
        final String text = source.toString();
        final String lower = text.toLowerCase(Locale.ROOT);

        assertFalse(text.contains("java.net."));
        assertFalse(text.contains("HttpClient"));
        assertFalse(text.contains("JsonAnySetter"));
        try (Stream<Path> files = Files.walk(packageRoot.resolve("domain"))) {
            for (final Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse(Files.readString(file).contains("DataExportTemplate"));
            }
        }
        assertFalse(lower.contains(" top 1"));
        assertFalse(lower.contains("crosswalk"));
        assertTrue(text.contains("FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION"));
    }

    @Test
    void laboratoryTemplateCannotUseTheFirstWaveRemoteContractOrUpdatedFilter() {
        final var template =
                br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate.LOCALIZACAO_CARGAS;
        assertTrue(template.laboratoryBackfillOnly());
        assertThrows(IllegalArgumentException.class, template::updatedAtFilter);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        br.com.esl.etl.v2.plataforma.fonte.dataexport
                                .DataExportFirstWaveContractCatalog.release(template));
    }
}
