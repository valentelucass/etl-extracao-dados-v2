package br.com.esl.etl.v2.plataforma.persistencia.sombra;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionResult;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import javax.sql.DataSource;

/**
 * Adaptador JDBC da auditoria de travessia Data Export.
 *
 * <p>Ele persiste somente metadados sanitizados de execução e página. Payload, URL, token, ID de
 * negócio, hash e cabeçalhos não fazem parte dos parâmetros SQL.
 */
public final class JdbcDataExportExtractionAudit implements DataExportExtractionAudit {

    private static final String START_EXECUTION =
            "{call ctl.usp_audit_execution_started(?, ?, ?, ?, ?, ?, ?)}";
    private static final String RECORD_PAGE = "{call ctl.usp_audit_page_read(?, ?, ?, ?, ?, ?)}";
    private static final String COMPLETE_EXECUTION =
            "{call ctl.usp_audit_execution_completed(?, ?, ?, ?, ?, ?)}";
    private static final String FAIL_EXECUTION =
            "{call ctl.usp_audit_execution_failed(?, ?, ?, ?, ?)}";

    private final DataSource dataSource;

    public JdbcDataExportExtractionAudit(final DataSource dataSource) {
        this.dataSource =
                Objects.requireNonNull(dataSource, "O DataSource de sombra é obrigatório.");
    }

    @Override
    public void executionStarted(final ExecutionStarted event) {
        Objects.requireNonNull(event, "O evento de início é obrigatório.");
        invoke(
                "o início da execução",
                START_EXECUTION,
                statement -> {
                    statement.setString(1, event.executionId().toString());
                    statement.setInt(2, event.template().templateId());
                    statement.setDate(3, Date.valueOf(event.businessDateWindow().startInclusive()));
                    statement.setDate(4, Date.valueOf(event.businessDateWindow().endInclusive()));
                    if (event.updatedAtWindow().isPresent()) {
                        bindInstant(
                                statement,
                                5,
                                event.updatedAtWindow().orElseThrow().startInclusive());
                        bindInstant(
                                statement, 6, event.updatedAtWindow().orElseThrow().endInclusive());
                    } else {
                        statement.setNull(5, Types.TIMESTAMP);
                        statement.setNull(6, Types.TIMESTAMP);
                    }
                    bindInstant(statement, 7, event.startedAt());
                });
    }

    @Override
    public void pageRead(final PageRead event) {
        Objects.requireNonNull(event, "O evento de página é obrigatório.");
        invoke(
                "a página da execução",
                RECORD_PAGE,
                statement -> {
                    statement.setString(1, event.executionId().toString());
                    statement.setInt(2, event.page());
                    statement.setInt(3, event.requestedPageSize());
                    statement.setInt(4, event.recordCount());
                    statement.setInt(5, event.distinctEntityCount());
                    bindInstant(statement, 6, event.readAt());
                });
    }

    @Override
    public void executionCompleted(final DataExportExtractionResult result) {
        Objects.requireNonNull(result, "O resultado da execução é obrigatório.");
        invoke(
                "a conclusão da execução",
                COMPLETE_EXECUTION,
                statement -> {
                    statement.setString(1, result.executionId().toString());
                    statement.setInt(2, result.pagesFetched());
                    statement.setLong(3, result.recordsDelivered());
                    statement.setInt(4, result.terminalPage());
                    bindInstant(statement, 5, result.completedAt());
                    statement.setString(6, result.traversalVerification().name());
                });
    }

    @Override
    public void executionFailed(final ExecutionFailed event) {
        Objects.requireNonNull(event, "O evento de falha é obrigatório.");
        invoke(
                "a falha da execução",
                FAIL_EXECUTION,
                statement -> {
                    statement.setString(1, event.executionId().toString());
                    statement.setInt(2, event.pagesFetched());
                    statement.setLong(3, event.recordsDelivered());
                    bindInstant(statement, 4, event.failedAt());
                    statement.setString(5, event.failureCategory().name());
                });
    }

    private void invoke(
            final String operation,
            final String statementSql,
            final CallableStatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
                CallableStatement statement = connection.prepareCall(statementSql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new ShadowAuditPersistenceException(operation, exception);
        }
    }

    private static void bindInstant(
            final CallableStatement statement, final int parameter, final Instant instant)
            throws SQLException {
        statement.setTimestamp(parameter, Timestamp.from(instant), utcCalendar());
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    @FunctionalInterface
    private interface CallableStatementBinder {

        void bind(CallableStatement statement) throws SQLException;
    }
}
