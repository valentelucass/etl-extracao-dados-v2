package br.com.esl.etl.v2.plataforma.persistencia.expansao;

import br.com.esl.etl.v2.modulos.fretes.domain.FreteStageBatch;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageBatch;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreightTerms;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;

/** Consumes the existing dependency staging; adds bounded exact typed temporal lineage. */
public final class JdbcExpansionDependencies {
    private final DataSource source;
    private final Clock clock;

    public JdbcExpansionDependencies(final DataSource source, final Clock clock) {
        this.source = java.util.Objects.requireNonNull(source);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public static String entity(final DataExportTemplate template) {
        return switch (template) {
            case FRETES -> "FRETE";
            case LOCALIZACAO_CARGAS -> "LOC";
            default -> throw new IllegalArgumentException("EXP_DEP_TEMPLATE_REQUIRED");
        };
    }

    public void financialTerms(
            final FreteStageBatch batch,
            final java.util.function.Function<String, ExpansionFreightTerms> bindings) {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "INSERT stg.expansion_lab_freight_terms(execution_id,batch_number,input_ordinal,"
                                        + "source_key,revision,billing_reference_date,classification,courtesy,eligible,"
                                        + "fallback_volumes,payer_token,currency,unit,active,evidence) VALUES(?,?,?,?,?,?,?,"
                                        + "?,?,?,?,?,?,?,?)")) {
            sql.setQueryTimeout(10);
            int count = 0;
            for (int index = 0; index < batch.size(); index++) {
                final var row = batch.recordAt(index);
                if (row.quarantineReasonCode() != null) {
                    continue;
                }
                final var term = bindings.apply(row.sourceKey().storageValue());
                if (term == null) {
                    continue;
                }
                if (!term.sourceKey().equals(row.sourceKey().storageValue())) {
                    throw new IllegalArgumentException("EXP_TERMS_SOURCE_BINDING");
                }
                sql.setString(1, batch.executionId().toString());
                sql.setInt(2, batch.batchNumber());
                sql.setInt(3, row.inputOrdinal());
                sql.setString(4, term.sourceKey());
                sql.setInt(5, term.revision());
                sql.setObject(6, term.billingReferenceDate());
                sql.setString(7, term.classification());
                sql.setObject(8, term.courtesy(), Types.BIT);
                sql.setObject(9, term.eligible(), Types.BIT);
                sql.setObject(10, term.fallbackVolumes(), Types.INTEGER);
                sql.setString(11, term.payerToken());
                sql.setString(12, term.currency());
                sql.setString(13, term.unit());
                sql.setBoolean(14, term.active());
                sql.setString(15, term.evidence());
                sql.addBatch();
                count++;
            }
            if (count > 0) {
                sql.executeBatch();
            }
        } catch (final SQLException failure) {
            throw new IllegalStateException("EXP_TERMS_BATCH", failure);
        }
    }

    public void begin(
            final UUID run,
            final UUID execution,
            final DataExportTemplate template,
            final LocalDate date,
            final String fingerprint)
            throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "INSERT ctl.expansion_lab_dependency_capture(execution_id,run_id,entity,"
                                        + "template_id,partition_date,contract_fingerprint,recorded_at) VALUES(?,?,?,?,?,?,"
                                        + "?)")) {
            sql.setQueryTimeout(10);
            sql.setString(1, execution.toString());
            sql.setString(2, run.toString());
            sql.setString(3, entity(template));
            sql.setInt(4, template.templateId());
            sql.setObject(5, date);
            sql.setString(6, fingerprint);
            sql.setTimestamp(7, Timestamp.from(clock.instant()));
            sql.executeUpdate();
        }
    }

    public Receipt sealAndApply(final UUID run, final UUID execution) throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC core.usp_apply_expansion_dependencies ?,?,?")) {
            sql.setQueryTimeout(20);
            sql.setString(1, run.toString());
            sql.setString(2, execution.toString());
            sql.setTimestamp(3, Timestamp.from(clock.instant()));
            try (var rows = sql.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("EXP_DEP_RECEIPT_MISSING");
                }
                return new Receipt(
                        rows.getLong(1),
                        rows.getLong(2),
                        rows.getLong(3),
                        rows.getLong(4),
                        rows.getLong(5),
                        rows.getLong(6),
                        rows.getLong(7));
            }
        }
    }

    public void exactTimes(final FreteStageBatch batch) {
        times(
                batch.executionId(),
                batch.batchNumber(),
                batch.size(),
                (sql, index) -> {
                    final var record = batch.recordAt(index);
                    append(
                            sql,
                            record.inputOrdinal(),
                            record.freshnessAtUtc(),
                            record.serviceAtUtc());
                });
    }

    public void exactTimes(final LocalizacaoCargaStageBatch batch) {
        times(
                batch.executionId(),
                batch.batchNumber(),
                batch.size(),
                (sql, index) -> {
                    final var record = batch.recordAt(index);
                    append(
                            sql,
                            record.inputOrdinal(),
                            record.serviceAtUtc(),
                            record.serviceAtUtc());
                });
    }

    private void times(
            final UUID execution, final int batch, final int size, final TemporalBatch records) {
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("EXP_DEP_BATCH_BOUND");
        }
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "INSERT stg.expansion_lab_dependency_time(execution_id,batch_number,input_ordinal,"
                                        + "fresh_second,fresh_nano,service_second,service_nano) VALUES(?,?,?,?,?,?,?)")) {
            sql.setQueryTimeout(10);
            for (int i = 0; i < size; i++) {
                sql.setString(1, execution.toString());
                sql.setInt(2, batch);
                records.append(sql, i);
                sql.addBatch();
            }
            sql.executeBatch();
        } catch (final SQLException failure) {
            throw new IllegalStateException("EXP_DEP_TIME_BATCH", failure);
        }
    }

    private static void append(
            final PreparedStatement sql,
            final int ordinal,
            final Instant fresh,
            final Instant service)
            throws SQLException {
        sql.setInt(3, ordinal);
        instant(sql, 4, fresh);
        instant(sql, 6, service);
    }

    private static void instant(final PreparedStatement sql, final int offset, final Instant time)
            throws SQLException {
        if (time == null) {
            sql.setNull(offset, Types.BIGINT);
            sql.setNull(offset + 1, Types.INTEGER);
        } else {
            sql.setLong(offset, time.getEpochSecond());
            sql.setInt(offset + 1, time.getNano());
        }
    }

    @FunctionalInterface
    private interface TemporalBatch {
        void append(PreparedStatement sql, int index) throws SQLException;
    }

    public record Receipt(
            long observed,
            long inserts,
            long updates,
            long noops,
            long stale,
            long quarantine,
            long duplicates) {
        public Receipt {
            if (observed != inserts + updates + noops + stale + quarantine + duplicates) {
                throw new IllegalArgumentException("EXP_DEP_EQUATION");
            }
        }
    }
}
