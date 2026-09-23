package br.com.esl.etl.v2.modulos.fretes.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FreteStageBatchTest {
    private static final Instant NOW = Instant.parse("2036-03-20T12:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private final FreteDataExportRecordMapper mapper = new FreteDataExportRecordMapper();

    @Test
    void boundsTheContractedPageRejectsEmptyRepeatedOrdinalAndOversize() throws Exception {
        final UUID execution = UUID.fromString("00000000-0000-0000-0000-000000006389");
        final FreteStageRecord one = valid(1, 1);
        assertEquals(1, new FreteStageBatch(execution, 1, List.of(one), NOW).size());
        assertThrows(
                IllegalArgumentException.class,
                () -> new FreteStageBatch(execution, 1, List.of(), NOW));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FreteStageBatch(execution, 1, List.of(one, one), NOW));

        final List<FreteStageRecord> tooMany = new ArrayList<>();
        for (int ordinal = 1; ordinal <= FreteStageRecord.MAXIMUM_PAGE_SIZE; ordinal++) {
            tooMany.add(valid(ordinal, ordinal));
        }
        assertEquals(
                FreteStageRecord.MAXIMUM_PAGE_SIZE,
                new FreteStageBatch(execution, 1, tooMany, NOW).size());
        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.map(FreteStageRecord.MAXIMUM_PAGE_SIZE + 1, json.readTree("{}")));
    }

    @Test
    void keepsStableQuarantineReasonsOnly() {
        assertEquals(
                "EQUAL_FRESHNESS_CONFLICT",
                FreteStageRecord.quarantine(1, "EQUAL_FRESHNESS_CONFLICT").quarantineReasonCode());
        assertThrows(
                IllegalArgumentException.class,
                () -> FreteStageRecord.quarantine(1, "arrival-order"));
    }

    private FreteStageRecord valid(final int ordinal, final int id) throws Exception {
        return mapper.map(
                ordinal,
                json.readTree("{\"id\":" + id + ",\"cte_created_at\":\"2036-03-20T12:00:00Z\"}"));
    }
}
