package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageFetch;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPayloadProfile;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseForm;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import java.util.List;
import java.util.Objects;

/** Perfil de payload sem registros, identificadores, URL ou hash de identificador. */
public record ContractPayloadEvidence(
        ContractPayloadObservation observation,
        int requestedPageSize,
        int httpStatus,
        DataExportTransport acceptedTransport,
        DataExportResponseForm responseForm,
        boolean jsonContentTypeDeclared,
        int recordCount,
        boolean paginationEntityCountVerifiable,
        int paginationEntityCount,
        List<ContractFieldEvidence> fields) {

    public ContractPayloadEvidence(
            final ContractPayloadObservation observation,
            final int requestedPageSize,
            final int httpStatus,
            final DataExportTransport acceptedTransport,
            final DataExportResponseForm responseForm,
            final int recordCount,
            final List<ContractFieldEvidence> fields) {
        this(
                observation,
                requestedPageSize,
                httpStatus,
                acceptedTransport,
                responseForm,
                false,
                recordCount,
                false,
                0,
                fields);
    }

    public ContractPayloadEvidence(
            final ContractPayloadObservation observation,
            final int requestedPageSize,
            final int httpStatus,
            final DataExportTransport acceptedTransport,
            final DataExportResponseForm responseForm,
            final boolean jsonContentTypeDeclared,
            final int recordCount,
            final List<ContractFieldEvidence> fields) {
        this(
                observation,
                requestedPageSize,
                httpStatus,
                acceptedTransport,
                responseForm,
                jsonContentTypeDeclared,
                recordCount,
                false,
                0,
                fields);
    }

    public ContractPayloadEvidence {
        observation = Objects.requireNonNull(observation, "A observação do payload é obrigatória.");
        if (requestedPageSize <= 0 || httpStatus < 200 || httpStatus >= 300) {
            throw new IllegalArgumentException("Os metadados da página observada são inválidos.");
        }
        acceptedTransport =
                Objects.requireNonNull(acceptedTransport, "O transporte aceito é obrigatório.");
        responseForm = Objects.requireNonNull(responseForm, "A forma da resposta é obrigatória.");
        if (recordCount < 0) {
            throw new IllegalArgumentException("A contagem de registros não pode ser negativa.");
        }
        if (paginationEntityCount < 0) {
            throw new IllegalArgumentException("A contagem de entidades não pode ser negativa.");
        }
        fields =
                List.copyOf(
                        Objects.requireNonNull(fields, "Os campos de evidência são obrigatórios."));
    }

    public static ContractPayloadEvidence from(
            final ContractPayloadObservation observation,
            final DataExportTemplate template,
            final int requestedPageSize,
            final DataExportPageFetch pageFetch) {
        Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        Objects.requireNonNull(pageFetch, "A página observada é obrigatória.");
        final DataExportPayloadProfile profile =
                DataExportPayloadProfile.fromRecords(pageFetch.response().records());
        final ContractDataExportEntityIndex entities =
                ContractDataExportEntityIndex.forTemplate(template, pageFetch.response().records());
        return new ContractPayloadEvidence(
                observation,
                requestedPageSize,
                pageFetch.httpStatus(),
                pageFetch.acceptedTransport(),
                pageFetch.responseForm(),
                pageFetch.jsonContentTypeDeclared(),
                profile.recordCount(),
                entities.entityIdsVerifiable(),
                entities.entityCount(),
                profile.fields().stream().map(ContractFieldEvidence::from).toList());
    }

    public boolean recordCountWithinRequestedPageSize() {
        return recordCount <= requestedPageSize;
    }

    /** Confirma o teto sem confundir as linhas físicas expandidas com as entidades paginadas. */
    public boolean paginationEntityCountWithinRequestedPageSize() {
        return paginationEntityCountVerifiable && paginationEntityCount <= requestedPageSize;
    }

    public boolean isEmptyArrayResponse() {
        return recordCount == 0 && responseForm.isArray();
    }
}
