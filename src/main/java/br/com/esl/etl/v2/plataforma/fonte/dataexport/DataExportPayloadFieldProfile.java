package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.List;
import java.util.Objects;

/** Perfil sem valores de uma coluna observada no payload Data Export. */
public record DataExportPayloadFieldProfile(
        String technicalName,
        int presentCount,
        int nullCount,
        List<String> jsonTypes,
        List<String> textualFormats) {

    public DataExportPayloadFieldProfile {
        if (technicalName == null || technicalName.isBlank()) {
            throw new IllegalArgumentException("O nome técnico do campo é obrigatório.");
        }
        if (presentCount < 0 || nullCount < 0 || nullCount > presentCount) {
            throw new IllegalArgumentException("As contagens do perfil de payload são inválidas.");
        }
        jsonTypes =
                List.copyOf(Objects.requireNonNull(jsonTypes, "Os tipos JSON são obrigatórios."));
        textualFormats =
                List.copyOf(
                        Objects.requireNonNull(
                                textualFormats, "Os formatos textuais são obrigatórios."));
    }
}
