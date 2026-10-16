package br.com.esl.etl.v2.plataforma.configuracao;

/** Exact, immutable target for the separately approved C78 local shadow client profile. */
public record C78LocalSqlTarget(String jdbcUrl, String approvalReference) {
    public static final String PROFILE = "C78_LOCAL_SHADOW_SQL_PIN_V1";
    public static final String APPROVAL_REFERENCE = "HERMES_5d489ad1eea2_C78_LOCAL";
    public static final String URL =
            "jdbc:sqlserver://localhost:1433;databaseName=ETL_SISTEMA_V2_SHADOW"
                    + ";integratedSecurity=false;encrypt=true;trustServerCertificate=false";

    public C78LocalSqlTarget {
        if (!URL.equals(jdbcUrl) || !APPROVAL_REFERENCE.equals(approvalReference)) {
            throw new IllegalArgumentException(
                    "C78_LOCAL_SHADOW_EXACT_TARGET_AND_APPROVAL_REQUIRED");
        }
    }

    public static C78LocalSqlTarget approved(final String approvalReference) {
        return new C78LocalSqlTarget(URL, approvalReference);
    }
}
