package br.com.esl.etl.v2.plataforma.persistencia.controle;

import java.sql.SQLException;

/** Neutral port. The Main composition must supply a concrete Root-reviewed physical admission. */
public interface C78DevCatalogAdmission {
    /**
     * Verify the separate 8f effect dispatch, current bytes/target/TLS/native-image prerequisites
     * and CREATE_NEW occurrence/reservation before opening JDBC. C78 trust approval alone is
     * insufficient. A parent controller must enforce the complete 60-second hard deadline.
     */
    void beforeOpen(C78DevCatalogPlan plan) throws SQLException;

    /** Persist own closure/outcome receipt; this callback grants neither retry nor capture. */
    void afterClose(C78DevCatalogPlan plan, boolean connectionClosed, int selectsAttempted)
            throws SQLException;
}
