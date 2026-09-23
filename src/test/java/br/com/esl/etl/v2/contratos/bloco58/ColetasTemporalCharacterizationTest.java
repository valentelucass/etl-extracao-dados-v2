package br.com.esl.etl.v2.contratos.bloco58;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaFreshnessOrigin;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CharacterizationParserAccess;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ColetasTemporalCharacterizationTest {
    @ParameterizedTest
    @ValueSource(strings = {"2035-02-29 10:00:00", "29/02/2035 10:00:00", "31/04/2036 9:00:00"})
    void impossibleLocalDatePreservesRawAndUsesValidFallback(final String invalid)
            throws Exception {
        // COL-02 / ADR 0023: invalid raw is never reinterpreted as another business date.
        final var row =
                CharacterizationParserAccess.parse(
                        "{\"id\":0,\"status_updated_at\":\""
                                + invalid
                                + "\",\"finish_date\":\"2036-03-01\"}");
        final var mapped = new ColetaDataExportRecordMapper().map(1, row);
        assertEquals(ColetaFreshnessOrigin.FINISH_DATE, mapped.freshnessOrigin());
        assertEquals(Instant.parse("2036-03-01T03:00:00Z"), mapped.freshnessAtUtc());
        assertEquals(invalid, mapped.freshnessRaw());
    }
}
