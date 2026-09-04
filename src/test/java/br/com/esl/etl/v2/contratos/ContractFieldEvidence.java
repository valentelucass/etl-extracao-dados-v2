package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPayloadFieldProfile;
import java.util.List;
import java.util.Objects;

/** Campo sanitizado destinado exclusivamente ao resumo de evidência em {@code target/}. */
public record ContractFieldEvidence(
        String technicalName,
        int presentCount,
        int nullCount,
        List<String> jsonTypes,
        List<String> textualFormats) {

    public ContractFieldEvidence {
        technicalName =
                ContractEvidenceSanitizer.fieldName(
                        technicalName, "O nome técnico do campo é obrigatório.");
        if (presentCount < 0 || nullCount < 0 || nullCount > presentCount) {
            throw new IllegalArgumentException("As contagens de evidência são inválidas.");
        }
        jsonTypes = ContractEvidenceSanitizer.jsonTypes(jsonTypes);
        textualFormats = ContractEvidenceSanitizer.textualFormats(textualFormats);
    }

    public static ContractFieldEvidence from(final DataExportPayloadFieldProfile field) {
        Objects.requireNonNull(field, "O perfil de campo é obrigatório.");
        return new ContractFieldEvidence(
                field.technicalName(),
                field.presentCount(),
                field.nullCount(),
                field.jsonTypes(),
                field.textualFormats());
    }
}
