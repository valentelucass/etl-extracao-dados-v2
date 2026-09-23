package br.com.esl.etl.v2.plataforma.persistencia.expansao;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** SQL authority for the laboratory run, exact capture receipt and application reconciliation. */
public final class JdbcExpansionLaboratory {
    public static final String SOURCE = "SYNTHETIC_EXPANSION_LAB";
    public static final String TENANT = "SYNTHETIC_TENANT";
    public static final String VERSION = "expansion-synthetic-v1";
    private final DataSource source;
    private final Clock clock;

    public JdbcExpansionLaboratory(final DataSource source, final Clock clock) {
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
    }

    public void start(final UUID run, final ExpansionPolicy policy) throws SQLException {
        start(
                run,
                policy,
                new br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope(SOURCE, TENANT));
    }

    public void start(
            final UUID run,
            final ExpansionPolicy policy,
            final br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope)
            throws SQLException {
        try (var connection = source.getConnection();
                var statement =
                        connection.prepareStatement(
                                "INSERT ctl.expansion_lab_run(run_id,source_instance,tenant_scope,contract_version,"
                                        + "window_start,window_end_exclusive,business_date,page_size,maximum_pages,maximum_rows,"
                                        + "fiscal_policy,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setQueryTimeout(10);
            statement.setString(1, run.toString());
            statement.setString(2, scope.source());
            statement.setString(3, scope.tenant());
            statement.setString(4, VERSION);
            statement.setObject(5, policy.start());
            statement.setObject(6, policy.endExclusive());
            statement.setObject(7, policy.businessDate());
            statement.setInt(8, policy.pageSize());
            statement.setInt(9, policy.maximumPages());
            statement.setInt(10, policy.maximumRows());
            statement.setString(11, policy.fiscalPolicy().name());
            statement.setTimestamp(
                    12,
                    Timestamp.from(clock.instant()),
                    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            statement.executeUpdate();
        }
        registerContracts(run);
    }

    private void registerContracts(final UUID run) throws SQLException {
        try (var connection = source.getConnection();
                var statement =
                        connection.prepareStatement(
                                "INSERT ctl.expansion_lab_contract(run_id,entity,contract_version,"
                                        + "contract_fingerprint) VALUES(?,?,?,?)")) {
            statement.setQueryTimeout(10);
            for (final var template :
                    java.util.List.of(
                            DataExportTemplate.CONTAS_A_PAGAR,
                            DataExportTemplate.FATURAS_POR_CLIENTE,
                            DataExportTemplate.INVENTARIO,
                            DataExportTemplate.SINISTROS,
                            DataExportTemplate.FRETES,
                            DataExportTemplate.LOCALIZACAO_CARGAS)) {
                final var release =
                        template.syntheticOccurrenceCapture()
                                ? br.com.esl.etl.v2.plataforma.fonte.dataexport
                                        .ExpansionSyntheticSource.release(template)
                                : br.com.esl.etl.v2.plataforma.fonte.dataexport
                                        .ExpansionDependencySource.release(template);
                statement.setString(1, run.toString());
                statement.setString(
                        2,
                        template.syntheticOccurrenceCapture()
                                ? vertical(template)
                                : JdbcExpansionDependencies.entity(template));
                statement.setString(
                        3,
                        template.syntheticOccurrenceCapture()
                                ? VERSION
                                : "expansion-dependency-v1");
                statement.setString(4, release.contractFingerprint().sha256());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope(final UUID run)
            throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT source_instance,tenant_scope FROM ctl.expansion_lab_run WHERE run_id=?")) {
            sql.setString(1, run.toString());
            sql.setQueryTimeout(10);
            try (var row = sql.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("EXP_RUN_MISSING");
                }
                return new br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope(
                        row.getString(1), row.getString(2));
            }
        }
    }

    public ExpansionPolicy policy(final UUID run) throws SQLException {
        try (var connection = source.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT window_start,window_end_exclusive,business_date,page_size,maximum_pages,"
                                        + "maximum_rows,"
                                        + "fiscal_policy FROM ctl.expansion_lab_run WHERE run_id=?")) {
            statement.setQueryTimeout(10);
            statement.setString(1, run.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("EXP_RUN_MISSING");
                }
                return new ExpansionPolicy(
                        rows.getDate(1).toLocalDate(),
                        rows.getDate(2).toLocalDate(),
                        rows.getDate(3).toLocalDate(),
                        rows.getInt(4),
                        rows.getInt(5),
                        rows.getInt(6),
                        br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules
                                .FiscalPolicy.valueOf(rows.getString(7)));
            }
        }
    }

    public void beginCapture(
            final UUID run,
            final UUID execution,
            final DataExportTemplate template,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final String fingerprint)
            throws SQLException {
        try (var connection = source.getConnection();
                var statement =
                        connection.prepareStatement(
                                "INSERT ctl.expansion_lab_capture(execution_id,run_id,template_id,vertical,"
                                        + "contract_fingerprint,mode,"
                                        + "partition_start,partition_end_exclusive,replay_of,state,created_at) VALUES(?,?,?,"
                                        + "?,?,?,?,?,?,'CAPTURING',?)")) {
            statement.setQueryTimeout(10);
            statement.setString(1, execution.toString());
            statement.setString(2, run.toString());
            statement.setInt(3, template.templateId());
            statement.setString(4, vertical(template));
            statement.setString(5, fingerprint);
            statement.setString(6, mode.name());
            statement.setObject(7, date);
            statement.setObject(8, date.plusDays(1));
            statement.setString(9, replayOf == null ? null : replayOf.toString());
            statement.setTimestamp(
                    10,
                    Timestamp.from(clock.instant()),
                    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            statement.executeUpdate();
        }
    }

    public void seal(final UUID run, final UUID execution) throws SQLException {
        try (var connection = source.getConnection();
                var statement =
                        connection.prepareCall(
                                "{call ctl.usp_seal_expansion_lab_capture(?,?,?)}")) {
            statement.setQueryTimeout(20);
            statement.setString(1, run.toString());
            statement.setString(2, execution.toString());
            statement.setTimestamp(
                    3,
                    Timestamp.from(clock.instant()),
                    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            statement.execute();
        }
    }

    public ApplyReceipt apply(final UUID run, final UUID execution) throws SQLException {
        try (var connection = source.getConnection();
                var statement =
                        connection.prepareCall(
                                "{call core.usp_apply_expansion_laboratory(?,?,?)}")) {
            statement.setQueryTimeout(30);
            statement.setString(1, run.toString());
            statement.setString(2, execution.toString());
            statement.setTimestamp(
                    3,
                    Timestamp.from(clock.instant()),
                    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("EXP_APPLY_RECEIPT_MISSING");
                }
                return new ApplyReceipt(
                        rows.getLong(1),
                        rows.getLong(2),
                        rows.getLong(3),
                        rows.getLong(4),
                        rows.getLong(5),
                        rows.getLong(6),
                        rows.getLong(7),
                        rows.getLong(8));
            }
        }
    }

    public static String vertical(final DataExportTemplate template) {
        return switch (template) {
            case CONTAS_A_PAGAR -> "CAP";
            case FATURAS_POR_CLIENTE -> "FAT";
            case INVENTARIO -> "INV";
            case SINISTROS -> "SIN";
            default -> throw new IllegalArgumentException("EXP_TEMPLATE_REQUIRED");
        };
    }

    public record ApplyReceipt(
            long observations,
            long inserts,
            long updates,
            long noops,
            long stale,
            long quarantine,
            long unbound,
            long duplicates) {
        public ApplyReceipt {
            if (observations
                    != inserts + updates + noops + stale + quarantine + unbound + duplicates) {
                throw new IllegalArgumentException("EXP_RECONCILIATION");
            }
        }
    }
}
