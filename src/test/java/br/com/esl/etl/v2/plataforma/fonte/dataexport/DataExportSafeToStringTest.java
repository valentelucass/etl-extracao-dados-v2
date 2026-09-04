package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataExportSafeToStringTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void omitsPayloadValuesAndRetryHeadersFromDiagnosticToStrings() throws Exception {
        final DataExportHttpResponse httpResponse =
                new DataExportHttpResponse(
                        200, "{\"id\":900001,\"secret\":\"payload-value\"}", Optional.of("120"));
        final DataExportPageResponse response =
                new DataExportPageResponse(
                        List.of(
                                objectMapper.readTree(
                                        "{\"id\":900001,\"secret\":\"payload-value\"}")));
        final DataExportPageFetch fetch =
                new DataExportPageFetch(
                        response,
                        DataExportTransport.GET_WITH_BODY,
                        200,
                        Optional.of("120"),
                        DataExportResponseForm.ROOT_OBJECT);
        final DataExportTemplateInfo info =
                new DataExportTemplateInfo(
                        DataExportTemplate.COLETAS,
                        200,
                        Optional.of("120"),
                        List.of(
                                new DataExportMetadataField(
                                        "id", Optional.of("integer"), Optional.of("Human label"))),
                        List.of());
        final DataExportMetadataField metadataField =
                new DataExportMetadataField(
                        "synthetic_secret_name",
                        Optional.of("synthetic-secret-type"),
                        Optional.of("synthetic secret label"));

        assertFalse(httpResponse.toString().contains("900001"));
        assertFalse(httpResponse.toString().contains("payload-value"));
        assertFalse(httpResponse.toString().contains("120"));
        assertFalse(fetch.toString().contains("900001"));
        assertFalse(fetch.toString().contains("payload-value"));
        assertFalse(fetch.toString().contains("120"));
        assertFalse(info.toString().contains("Human label"));
        assertFalse(info.toString().contains("120"));
        assertFalse(metadataField.toString().contains("synthetic_secret_name"));
        assertFalse(metadataField.toString().contains("synthetic-secret-type"));
        assertFalse(metadataField.toString().contains("synthetic secret label"));
    }
}
