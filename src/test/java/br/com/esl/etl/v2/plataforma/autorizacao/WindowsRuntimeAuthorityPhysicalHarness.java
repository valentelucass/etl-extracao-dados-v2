package br.com.esl.etl.v2.plataforma.autorizacao;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePlanningRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadDefinition;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadId;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;

/** Test classpath only. Uses the real authority adapter and SQL, with no positive verifier. */
public final class WindowsRuntimeAuthorityPhysicalHarness {
    private WindowsRuntimeAuthorityPhysicalHarness() {}

    public static void verifyDurableDenial(final DataSource source, final UUID invocation)
            throws Exception {
        final var values = new Properties();
        try (var connection = source.getConnection();
                var query = connection.createStatement();
                var row =
                        query.executeQuery(
                                "SELECT CONVERT(NVARCHAR(128),SERVERPROPERTY('ServerName')),DB_NAME()")) {
            if (!row.next()) {
                throw new IllegalStateException("AUTHORITY_PHYSICAL_PREFLIGHT");
            }
            values.setProperty("server", row.getString(1));
            values.setProperty("database", row.getString(2));
        }
        values.setProperty("authorityId", "00000000-0000-0000-0000-000000005300");
        values.setProperty(
                "policyFingerprint", RuntimeAuthorizationPolicy.standard().fingerprint().sha256());
        final var id = new RuntimeWorkloadId("coletas");
        final var fingerprint = new ImmutableFingerprint("synthetic-b53", "a".repeat(64));
        final Instant start = Instant.parse("2036-01-01T00:00:00Z");
        final var definition =
                new RuntimeWorkloadDefinition(
                        id,
                        "DATA_EXPORT",
                        "SYNTHETIC_B53_AUTH",
                        "SYNTHETIC_B53_AUTH",
                        "coletas",
                        fingerprint,
                        fingerprint,
                        Duration.ofSeconds(30));
        final var request =
                new RuntimeExecutionRequest(
                        invocation,
                        id,
                        ExecutionMode.BACKFILL,
                        RuntimeWindowStrategy.INTERVAL,
                        start,
                        start.plusSeconds(3600),
                        invocation.toString());
        final var plan =
                RuntimeWorkloadRegistry.of(definition)
                        .plan(
                                new RuntimePlanningRequest(
                                        invocation, "LOCAL_SHADOW", fingerprint, start, request));
        final var scope = RuntimeAuthorizationScope.from(invocation, RuntimeAction.RUN, plan);
        final var authority =
                new WindowsSqlRuntimeAuthorization(
                        new RuntimeAuthorityConfiguration(values), source::getConnection);
        try {
            authority.authorize(scope);
            throw new AssertionError("UNPROVISIONED_IDENTITY_AUTHORIZED");
        } catch (final DurableAuthorizationException expected) {
            if (expected.reason() != DurableAuthorizationException.Reason.UNCONFIGURED) {
                throw expected;
            }
        }
        try (var connection = source.getConnection();
                var query =
                        connection.prepareStatement(
                                "SELECT (SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_decision WHERE invocation_id=?"
                                        + " AND decision='DENY' AND reason='AUTHORITY_UNCONFIGURED'),"
                                        + "(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_consumption WHERE invocation_id=?)")) {
            query.setString(1, invocation.toString());
            query.setString(2, invocation.toString());
            try (var row = query.executeQuery()) {
                if (!row.next() || row.getLong(1) != 1 || row.getLong(2) != 0 || row.next()) {
                    throw new AssertionError("DURABLE_DENIAL_NOT_IDEMPOTENT");
                }
            }
        }
        System.out.println(
                "WINDOWS_JDBC_DENY_PASS durable_decision=1 consumed=0 configured_identity=0 business_composed=0");
    }
}
