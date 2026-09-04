package br.com.esl.etl.v2.plataforma.persistencia.sombra;

import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit;
import java.util.Objects;
import javax.sql.DataSource;

/** Compõe a auditoria JDBC apenas quando a configuração opt-in foi aprovada. */
public final class ShadowAuditFactory {

    private ShadowAuditFactory() {}

    public static DataExportExtractionAudit create(
            final ShadowStorageProperties properties, final DataSource approvedDataSource) {
        Objects.requireNonNull(properties, "As propriedades de sombra são obrigatórias.");
        if (!properties.auditEnabled()) {
            return DataExportExtractionAudit.noop();
        }
        return new JdbcDataExportExtractionAudit(
                Objects.requireNonNull(
                        approvedDataSource, "O DataSource aprovado de sombra é obrigatório."));
    }
}
