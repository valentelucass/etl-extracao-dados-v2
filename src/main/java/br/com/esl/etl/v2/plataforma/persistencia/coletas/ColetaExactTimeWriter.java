package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaFreshnessOrigin;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageDisposition;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

/** Adds exact evidence in the same transaction as the existing typed staging. */
final class ColetaExactTimeWriter {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ColetaExactTimeWriter() {}

    static void stage(
            final Connection connection,
            final ColetaStageBatch batch,
            final CancellationToken cancellation)
            throws SQLException {
        try (var statement =
                connection.prepareCall(
                        "{call stg.usp_stage_coleta_exact_time(?,?,?,?,?,?,?,?,?)}")) {
            statement.setQueryTimeout(20);
            for (int index = 0; index < batch.size(); index++) {
                final var record = batch.recordAt(index);
                if (record.disposition() == ColetaStageDisposition.QUARANTINE) {
                    continue;
                }
                cancellation.throwIfCancellationRequested();
                statement.setString(1, batch.executionId().toString());
                statement.setInt(2, batch.batchNumber());
                statement.setInt(3, record.inputOrdinal());
                if (record.freshnessAtUtc() == null) {
                    statement.setNull(4, Types.BIGINT);
                    statement.setNull(5, Types.INTEGER);
                } else {
                    statement.setLong(4, record.freshnessAtUtc().getEpochSecond());
                    statement.setInt(5, record.freshnessAtUtc().getNano());
                }
                final var raw = JSON.readTree(record.payloadJson()).get("status_updated_at");
                final String presence = raw == null ? "ABSENT" : raw.isNull() ? "NULL" : "VALUE";
                final boolean nativeTime =
                        record.freshnessOrigin() == ColetaFreshnessOrigin.STATUS_UPDATED_AT;
                final String parse =
                        nativeTime ? "VALID" : presence.equals("VALUE") ? "INVALID" : presence;
                statement.setNString(6, presence);
                if (raw == null || raw.isNull()) {
                    statement.setNull(7, Types.NVARCHAR);
                } else {
                    statement.setNString(7, raw.toString());
                }
                statement.setNString(8, parse);
                statement.setNString(9, nativeTime ? "NONE" : "NATIVE_" + parse);
                statement.addBatch();
            }
            cancellation.throwIfCancellationRequested();
            statement.executeBatch();
        } catch (final JsonProcessingException failure) {
            throw new IllegalArgumentException("COL_EXACT_INVALID_PRESERVED_PAYLOAD", failure);
        }
    }
}
