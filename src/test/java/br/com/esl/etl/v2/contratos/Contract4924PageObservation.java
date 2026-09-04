package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPayloadProfile;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseForm;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;

/** Amostra 4924 em memória; somente seu perfil sanitizado é elegível ao summary. */
public record Contract4924PageObservation(
        int httpStatus,
        DataExportTransport acceptedTransport,
        boolean retryAfterObserved,
        boolean jsonContentTypeDeclared,
        DataExportResponseForm responseForm,
        List<JsonNode> records) {

    public Contract4924PageObservation {
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalArgumentException("A página auxiliar exige HTTP de sucesso.");
        }
        acceptedTransport =
                Objects.requireNonNull(acceptedTransport, "O transporte auxiliar é obrigatório.");
        responseForm = Objects.requireNonNull(responseForm, "A forma de resposta é obrigatória.");
        records =
                List.copyOf(
                        Objects.requireNonNull(
                                records, "Os registros auxiliares são obrigatórios."));
    }

    public ContractPayloadEvidence asPayloadEvidence() {
        final DataExportPayloadProfile profile = DataExportPayloadProfile.fromRecords(records);
        return new ContractPayloadEvidence(
                ContractPayloadObservation.AUXILIARY_4924_CLOSED_WINDOW,
                1,
                httpStatus,
                acceptedTransport,
                responseForm,
                jsonContentTypeDeclared,
                profile.recordCount(),
                profile.fields().stream().map(ContractFieldEvidence::from).toList());
    }

    @Override
    public String toString() {
        return "Contract4924PageObservation[httpStatus="
                + httpStatus
                + ", acceptedTransport="
                + acceptedTransport
                + ", retryAfterObserved="
                + retryAfterObserved
                + ", jsonContentTypeDeclared="
                + jsonContentTypeDeclared
                + ", responseForm="
                + responseForm
                + ", recordCount="
                + records.size()
                + "]";
    }
}
