package br.com.esl.etl.v2.plataforma.configuracao;

/** Exact, immutable target for the separately approved persisted test profile. */
public record TestRetainedSqlTarget(String jdbcUrl, String approvalReference) {
    public static final String PROFILE = "TEST_RETAINED_LOCAL_SQL_PIN_V1";
    public static final String APPROVAL_REFERENCE = "USER_20261005_E3_TEST_RETAINED";
    public static final String URL =
            "jdbc:sqlserver://localhost:1433;databaseName=ETL_SISTEMA_V2_SHADOW"
                    + ";integratedSecurity=false;encrypt=true;trustServerCertificate=false";

    public TestRetainedSqlTarget {
        if (!URL.equals(jdbcUrl) || !APPROVAL_REFERENCE.equals(approvalReference)) {
            throw new IllegalArgumentException("TEST_RETAINED_EXACT_TARGET_AND_APPROVAL_REQUIRED");
        }
    }

    public static TestRetainedSqlTarget approved(final String approvalReference) {
        return new TestRetainedSqlTarget(URL, approvalReference);
    }
}
