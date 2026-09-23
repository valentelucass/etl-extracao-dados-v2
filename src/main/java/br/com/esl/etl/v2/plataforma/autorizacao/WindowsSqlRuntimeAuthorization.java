package br.com.esl.etl.v2.plataforma.autorizacao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Supplier;

/** Closed Windows/SQL adapter. Its security connection never resolves workload credentials. */
public final class WindowsSqlRuntimeAuthorization {
    @FunctionalInterface
    interface Connections {
        Connection open() throws SQLException;
    }

    private final RuntimeAuthorityConfiguration configuration;
    private final Connections connections;

    WindowsSqlRuntimeAuthorization(
            final RuntimeAuthorityConfiguration configuration, final Connections connections) {
        this.configuration = Objects.requireNonNull(configuration);
        this.connections = Objects.requireNonNull(connections);
    }

    public static WindowsSqlRuntimeAuthorization fromAdministeredArtifact() {
        try {
            if (!System.getProperty("os.name", "").startsWith("Windows")) {
                throw new IllegalStateException("WINDOWS_AUTHENTICATION_REQUIRED");
            }
            AdministeredArtifactVerifier.verify();
            final var configuration = RuntimeAuthorityConfiguration.load();
            return new WindowsSqlRuntimeAuthorization(
                    configuration, AdministeredSqlConnection::open);
        } catch (final java.io.IOException | RuntimeException failure) {
            throw new DurableAuthorizationException(
                    DurableAuthorizationException.Reason.UNCONFIGURED, failure);
        }
    }

    public DurableRuntimeCapability authorize(final RuntimeAuthorizationScope scope) {
        return call(Objects.requireNonNull(scope), null).capability();
    }

    public <T> T consumeAndExecute(
            final DurableRuntimeCapability capability,
            final RuntimeAuthorizationScope actual,
            final Supplier<T> work) {
        Objects.requireNonNull(capability);
        Objects.requireNonNull(actual);
        Objects.requireNonNull(work);
        if (!capability.scope.material().equals(actual.material())) {
            throw new DurableAuthorizationException(
                    DurableAuthorizationException.Reason.SCOPE_CHANGED, null);
        }
        final Receipt consumed = call(actual, capability);
        if (consumed.fence() < 1
                || !consumed.capability().receipt.equals(capability.receipt)
                || !consumed.capability().authorizedAt.equals(capability.authorizedAt)
                || !consumed.capability().validUntil.equals(capability.validUntil)
                || !consumed.capability().auditReference.equals(capability.auditReference)
                || consumed.capability().mappingVersion != capability.mappingVersion
                || consumed.capability().scopeVersion != capability.scopeVersion) {
            throw new DurableAuthorizationException(
                    DurableAuthorizationException.Reason.INVALID_RECEIPT, null);
        }
        return work.get();
    }

    private Receipt call(
            final RuntimeAuthorizationScope scope, final DurableRuntimeCapability capability) {
        try (var connection = connections.open()) {
            preflight(connection);
            try (var query =
                    connection.prepareCall(
                            "{call ctl.usp_runtime_authorization(?,?,?,?,?,?,?,?)}")) {
                query.setQueryTimeout(10);
                query.setFetchSize(2);
                query.setString(1, capability == null ? "AUTHORIZE" : "CONSUME");
                query.setString(2, scope.invocationId().toString());
                query.setString(3, configuration.authority.toString());
                query.setString(4, scope.action().name());
                query.setString(5, scope.executionId().toString());
                query.setNString(6, scope.material());
                query.setString(7, configuration.policy);
                if (capability == null) {
                    query.setNull(8, java.sql.Types.VARCHAR);
                } else {
                    query.setString(8, capability.receipt.toString());
                }
                try (var row = query.executeQuery()) {
                    if (!row.next() || row.getMetaData().getColumnCount() != 11) {
                        throw new SQLException("AUTHORITY_RECEIPT_SHAPE");
                    }
                    final String decision = row.getString("decision");
                    if (!"ALLOW".equals(decision)) {
                        final String deniedReason = row.getString("reason");
                        if (!"DENY".equals(decision) || row.next()) {
                            throw new SQLException("AUTHORITY_DECISION_INVALID");
                        }
                        throw new DurableAuthorizationException(denialReason(deniedReason), null);
                    }
                    final UUID receipt = UUID.fromString(row.getString("receipt_id"));
                    final UUID audit = UUID.fromString(row.getString("audit_reference"));
                    final long mapping = row.getLong("mapping_version");
                    final long version = row.getLong("scope_version");
                    final String policy = row.getString("policy_fingerprint");
                    final String hash = row.getString("scope_hash");
                    final Instant authorized = instant(row, "authorized_at_utc"),
                            until = instant(row, "valid_until_utc");
                    final long fence = row.getLong("fence");
                    if (mapping < 1
                            || version < 1
                            || !configuration.policy.equals(policy)
                            || !scope.hash().equals(hash)
                            || !authorized.isBefore(until)
                            || until.isAfter(authorized.plusSeconds(60))
                            || !"AUTHORIZED".equals(row.getString("reason"))
                            || row.next()) {
                        throw new SQLException("AUTHORITY_RECEIPT_INVALID");
                    }
                    // Drain completion/commit acknowledgement before returning any capability.
                    if (query.getMoreResults() || query.getUpdateCount() != -1) {
                        throw new SQLException("AUTHORITY_EXTRA_RESULT");
                    }
                    return new Receipt(
                            new DurableRuntimeCapability(
                                    scope, receipt, audit, mapping, version, authorized, until),
                            fence);
                }
            }
        } catch (final SQLException failure) {
            throw new DurableAuthorizationException(
                    failure.getErrorCode() == 52415
                            ? DurableAuthorizationException.Reason.ALREADY_CONSUMED
                            : failure.getErrorCode() == 52414
                                    ? DurableAuthorizationException.Reason.DENIED
                                    : failure.getErrorCode() == 52413
                                            ? DurableAuthorizationException.Reason.SCOPE_CHANGED
                                            : DurableAuthorizationException.Reason
                                                    .AUTHORITY_UNAVAILABLE,
                    failure);
        } catch (final IllegalArgumentException | NullPointerException failure) {
            throw new DurableAuthorizationException(
                    DurableAuthorizationException.Reason.INVALID_RECEIPT, failure);
        }
    }

    private static DurableAuthorizationException.Reason denialReason(final String reason) {
        if (reason == null) {
            return DurableAuthorizationException.Reason.INVALID_RECEIPT;
        }
        return switch (reason) {
            case "AUTHORITY_UNCONFIGURED" -> DurableAuthorizationException.Reason.UNCONFIGURED;
            case "AUTHENTICATION_INVALID" ->
                    DurableAuthorizationException.Reason.AUTHENTICATION_INVALID;
            case "CONTEXT_CHANGED" -> DurableAuthorizationException.Reason.CONTEXT_CHANGED;
            case "PRIVILEGE_EXCESSIVE" -> DurableAuthorizationException.Reason.PRIVILEGE_EXCESSIVE;
            case "IDENTITY_UNMAPPED" -> DurableAuthorizationException.Reason.IDENTITY_UNMAPPED;
            case "IDENTITY_DISABLED_OR_UNVERIFIABLE" ->
                    DurableAuthorizationException.Reason.IDENTITY_DISABLED_OR_UNVERIFIABLE;
            case "SCOPE_REJECTED" -> DurableAuthorizationException.Reason.SCOPE_REJECTED;
            default -> DurableAuthorizationException.Reason.DENIED;
        };
    }

    private void preflight(final Connection connection) throws SQLException {
        try (var query =
                connection.prepareStatement(
                        "SELECT CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')),DB_NAME(),CONNECTIONPROPERTY('auth_scheme'),"
                                + "CASE WHEN ORIGINAL_LOGIN()=SUSER_SNAME()"
                                + " AND SUSER_SID(ORIGINAL_LOGIN())=SUSER_SID() THEN 1 ELSE 0 END")) {
            query.setQueryTimeout(10);
            query.setFetchSize(2);
            try (var row = query.executeQuery()) {
                if (!row.next()
                        || !configuration.server.equals(row.getString(1))
                        || !configuration.database.equals(row.getString(2))
                        || !java.util.Set.of("NTLM", "KERBEROS").contains(row.getString(3))
                        || row.getInt(4) != 1
                        || row.next()) {
                    throw new SQLException("AUTHORITY_TARGET_OR_CONTEXT_REJECTED");
                }
            }
        }
    }

    private static Instant instant(final ResultSet row, final String column) throws SQLException {
        final var value =
                row.getTimestamp(column, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        if (value == null) {
            throw new SQLException("AUTHORITY_SQL_TIME_REQUIRED");
        }
        return value.toInstant();
    }

    private record Receipt(DurableRuntimeCapability capability, long fence) {}
}
