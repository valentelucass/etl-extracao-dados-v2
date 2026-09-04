package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportMetadataField;
import java.util.Objects;

/** Campo de metadados sem rótulo ou texto remoto arbitrário. */
public record ContractMetadataEvidence(String technicalName, ContractMetadataType declaredType) {

    public ContractMetadataEvidence {
        technicalName =
                ContractEvidenceSanitizer.filterName(
                        technicalName, "O nome técnico do metadado é obrigatório.");
        declaredType =
                Objects.requireNonNull(
                        declaredType, "O tipo declarado categorizado é obrigatório.");
    }

    public static ContractMetadataEvidence from(final DataExportMetadataField field) {
        Objects.requireNonNull(field, "O campo de metadados é obrigatório.");
        return new ContractMetadataEvidence(
                field.technicalName(), ContractMetadataType.fromDeclaredType(field.declaredType()));
    }
}
