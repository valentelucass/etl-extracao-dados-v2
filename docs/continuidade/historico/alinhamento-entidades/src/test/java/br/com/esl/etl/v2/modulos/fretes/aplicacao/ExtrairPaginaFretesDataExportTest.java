package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportGateway;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtrairPaginaFretesDataExportTest {

    @Test
    void delegatesToGatewayWithTemplate6389() {
        final CapturingGateway gateway = new CapturingGateway();
        final DataExportPageResponse response =
                new ExtrairPaginaFretesDataExport(gateway)
                        .execute(
                                new BusinessDateRange(
                                        LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13)),
                                new SourceDateTimeRange(
                                        Instant.parse("2026-08-13T03:00:00Z"),
                                        Instant.parse("2026-08-14T02:59:59Z")),
                                3);

        assertEquals(DataExportTemplate.FRETES, gateway.request.template());
        assertEquals(3, gateway.request.page());
        assertEquals(1, response.records().size());
    }

    private static final class CapturingGateway implements DataExportGateway {
        private DataExportPageRequest request;

        @Override
        public DataExportPageResponse fetch(final DataExportPageRequest pageRequest) {
            request = pageRequest;
            return new DataExportPageResponse(
                    List.of(JsonNodeFactory.instance.objectNode().put("id", "sample")));
        }
    }
}
