package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Registers an explicit zero-tolerance laboratory policy; only the existing DQ engine issues
 * permits.
 */
public final class JdbcAnalyticQuality {
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticQuality(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public DataQualityPolicyReference register(
            final UUID run, final Entity entity, final ExecutionMode mode) throws SQLException {
        if (entity == Entity.USUARIOS
                && mode != ExecutionMode.BACKFILL
                && mode != ExecutionMode.REPLAY) {
            throw new IllegalArgumentException("ANA_QUALITY_USERS_OBSERVATION_MODE");
        }
        if (mode != ExecutionMode.BACKFILL
                && mode != ExecutionMode.REPLAY
                && mode != ExecutionMode.BOOTSTRAP
                && mode != ExecutionMode.INCREMENTAL) {
            throw new IllegalArgumentException("ANA_QUALITY_OBSERVATION_MODE");
        }
        try (var input =
                JdbcAnalyticQuality.class.getResourceAsStream(
                        "/analytic-laboratory/quality-policy.sql")) {
            if (input == null) {
                throw new SQLException("ANA_QUALITY_RESOURCE_MISSING");
            }
            final byte[] bytes = input.readNBytes(8193);
            if (bytes.length > 8192) {
                throw new SQLException("ANA_QUALITY_RESOURCE_BOUND");
            }
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    new String(bytes, StandardCharsets.UTF_8))) {
                sql.setString(1, run.toString());
                sql.setString(2, entity.name().toLowerCase(java.util.Locale.ROOT));
                sql.setString(3, mode.name());
                sql.setQueryTimeout(10);
                try (var row = sql.executeQuery()) {
                    if (!row.next()) {
                        throw new SQLException("ANA_QUALITY_RECEIPT_MISSING");
                    }
                    return new DataQualityPolicyReference(row.getString(1), row.getString(2));
                }
            }
        } catch (final IOException failure) {
            throw new SQLException("ANA_QUALITY_RESOURCE", failure);
        }
    }

    public enum Entity {
        USUARIOS,
        COTACOES
    }
}
