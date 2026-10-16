package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.plataforma.configuracao.C78DevPeerProof;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTlsScope;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Fixed, one-shot metadata route, not a DataSource or a capture permission. */
public final class C78DevCatalogInspector {
    @FunctionalInterface
    interface Opener {
        Connection open(C78LocalSqlTarget target, SecretProvider secrets) throws SQLException;
    }

    public record Evidence(C78DevCatalogPlan.Query query, List<String> rows) {
        public Evidence {
            Objects.requireNonNull(query);
            Objects.requireNonNull(rows);
            if (rows.size() > C78DevCatalogPlan.MAX_ROWS_PER_QUERY) {
                throw new IllegalArgumentException("C78_CATALOG_EVIDENCE_LIMIT");
            }
            rows = List.copyOf(rows);
            if (rows.size() > C78DevCatalogPlan.MAX_ROWS_PER_QUERY
                    || rows.stream().mapToLong(String::length).sum()
                            > C78DevCatalogPlan.queryCharacters(query)) {
                throw new IllegalArgumentException("C78_CATALOG_EVIDENCE_LIMIT");
            }
            for (final String row : rows) {
                if (row.length()
                                > query.columns() * (C78DevCatalogPlan.cellCharacters(query) + 1)
                                        - 1
                        || !row.matches("[A-Za-z0-9_.$@ ():/|\\-]*")) {
                    throw new IllegalArgumentException("C78_CATALOG_EVIDENCE_SHAPE");
                }
            }
        }
    }

    /**
     * Bounded schema metadata only. Presence/digests need physical contract review before capture.
     */
    public record Report(C78DevCatalogPlan plan, List<Evidence> evidence, int selectsAttempted) {
        public Report {
            Objects.requireNonNull(plan);
            Objects.requireNonNull(evidence);
            if (evidence.size() != C78DevCatalogPlan.MAX_SELECTS) {
                throw new IllegalArgumentException("C78_CATALOG_REPORT_LIMIT");
            }
            evidence = List.copyOf(evidence);
            if (evidence.size() != C78DevCatalogPlan.MAX_SELECTS
                    || selectsAttempted != C78DevCatalogPlan.MAX_SELECTS
                    || evidence.stream()
                                    .flatMap(e -> e.rows().stream())
                                    .mapToInt(String::length)
                                    .sum()
                            > plan.reportCharacters()) {
                throw new IllegalArgumentException("C78_CATALOG_REPORT_LIMIT");
            }
            for (int index = 0; index < evidence.size(); index++) {
                if (evidence.get(index).query() != plan.queries().get(index)) {
                    throw new IllegalArgumentException("C78_CATALOG_REPORT_ORDER");
                }
            }
        }
    }

    private final Opener opener;
    private final LongSupplier clock;
    private final AtomicBoolean consumed = new AtomicBoolean();

    public C78DevCatalogInspector(final C78DevPeerProof proof) {
        Objects.requireNonNull(proof);
        opener = (target, secrets) -> C78LocalSaJdbcConnector.openDev(target, secrets, proof);
        clock = System::nanoTime;
    }

    // Isolated JDBC boundary seam for unit tests; no production alternate opener is public.
    C78DevCatalogInspector(final Opener opener, final LongSupplier clock) {
        this.opener = Objects.requireNonNull(opener);
        this.clock = Objects.requireNonNull(clock);
    }

    public Report inspect(
            final C78DevCatalogPlan plan,
            final SecretProvider secrets,
            final C78DevCatalogAdmission admission)
            throws SQLException {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(secrets);
        Objects.requireNonNull(admission);
        C78LocalTlsScope.validate(plan.target());
        if (!consumed.compareAndSet(false, true)) {
            throw refused("OCCURRENCE_ALREADY_CONSUMED");
        }
        final long started = clock.getAsLong();
        try {
            admission.beforeOpen(plan);
        } catch (final SQLException | RuntimeException refused) {
            throw refused("ADMISSION_REFUSED");
        }
        Connection connection = null;
        int attempted = 0;
        boolean closed = true;
        SQLException failure = null;
        String connectorReason = null;
        Report report = null;
        try {
            remainingSeconds(started);
            closed = false;
            connection = Objects.requireNonNull(opener.open(plan.target(), secrets));
            final var evidence = new ArrayList<Evidence>(C78DevCatalogPlan.MAX_SELECTS);
            int reportChars = 0;
            String pre = null;
            for (final var query : plan.queries()) {
                final int seconds = remainingSeconds(started);
                final var rows = new ArrayList<String>();
                int queryChars = 0;
                try (final var statement = connection.createStatement()) {
                    statement.setQueryTimeout(seconds);
                    statement.setFetchSize(32);
                    attempted++;
                    try (final var results = statement.executeQuery(plan.sql(query))) {
                        while (results.next()) {
                            remainingSeconds(started);
                            if (rows.size() == C78DevCatalogPlan.MAX_ROWS_PER_QUERY) {
                                throw refused("ROW_LIMIT");
                            }
                            final String row = readRow(results, query);
                            queryChars += row.length();
                            reportChars += row.length();
                            if (queryChars > C78DevCatalogPlan.queryCharacters(query)
                                    || reportChars > plan.reportCharacters()) {
                                throw refused("MATERIAL_LIMIT");
                            }
                            rows.add(row);
                        }
                    }
                }
                if (query == C78DevCatalogPlan.Query.CONTEXT_PRE) {
                    pre = requireContext(rows);
                } else if (query == C78DevCatalogPlan.Query.CONTEXT_POST) {
                    if (!Objects.equals(pre, requireContext(rows))) {
                        throw refused("PREPOST_MISMATCH");
                    }
                }
                evidence.add(new Evidence(query, rows));
            }
            report = new Report(plan, evidence, attempted);
            remainingSeconds(started);
        } catch (final SQLException | RuntimeException caught) {
            // Driver/provider messages and nested causes may contain credentials or private data.
            if (connection == null
                    && caught instanceof C78LocalSaJdbcConnector.ConnectionFailure connector) {
                closed = !connector.cleanupUncertain();
                connectorReason = connectorReason(connector.pinReason());
                failure = refused(connectorReason);
            } else {
                failure =
                        caught instanceof CatalogFailure catalog
                                ? catalog
                                : refused("EXECUTION_FAILED");
            }
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                    closed = connection.isClosed();
                } catch (final SQLException | RuntimeException uncertain) {
                    closed = false;
                }
            }
            if (!closed) {
                failure =
                        refused(
                                connectorReason == null
                                        ? "CLOSE_UNCERTAIN"
                                        : connectorReason + "_CLOSE_UNCERTAIN");
            }
            try {
                admission.afterClose(plan, closed, attempted);
            } catch (final SQLException | RuntimeException receiptFailure) {
                failure = refused("RECEIPT_UNCERTAIN");
            }
        }
        if (failure != null) {
            throw failure;
        }
        remainingSeconds(started);
        return Objects.requireNonNull(report);
    }

    private int remainingSeconds(final long started) throws SQLException {
        final long elapsed = clock.getAsLong() - started;
        final long remaining = TimeUnit.SECONDS.toNanos(C78DevCatalogPlan.UNIT_SECONDS) - elapsed;
        // Leave five seconds for driver cancellation/owned close; parent owns the hard deadline.
        if (elapsed < 0 || remaining < TimeUnit.SECONDS.toNanos(6)) {
            throw refused("DEADLINE");
        }
        return (int)
                Math.min(
                        C78DevCatalogPlan.QUERY_SECONDS,
                        TimeUnit.NANOSECONDS.toSeconds(remaining) - 5);
    }

    private static String readRow(final ResultSet results, final C78DevCatalogPlan.Query query)
            throws SQLException {
        final var row = new StringBuilder();
        for (int column = 1; column <= query.columns(); column++) {
            final String value = results.getString(column);
            if (value == null
                    || value.length() > C78DevCatalogPlan.cellCharacters(query)
                    || !value.matches("[A-Za-z0-9_.$@ ():/-]*")) {
                throw refused("METADATA_SHAPE");
            }
            if (column > 1) {
                row.append('|');
            }
            row.append(value);
        }
        return row.toString();
    }

    private static String requireContext(final List<String> rows) throws SQLException {
        if (rows.size() != 1
                || !rows.get(0)
                        .matches(
                                "1\\|16\\.0\\.1000\\.6\\|1\\|SQL\\|[1-9][0-9]*\\|[1-9][0-9]*\\|0")) {
            throw refused("TARGET_VERSION_AUTH_CONTEXT");
        }
        return rows.get(0);
    }

    private static CatalogFailure refused(final String reason) {
        return new CatalogFailure(reason);
    }

    // Closed vocabulary reviewed against the unchanged connector/scope/trust-manager sources.
    // Never propagate the original exception message, nested causes or an unknown pinReason.
    static String connectorReason(final String reason) {
        if (reason == null) {
            return "CONNECTOR_REFUSED";
        }
        return switch (reason) {
            case "SCOPE_CREATION_REFUSED" -> "CONNECTOR_SCOPE_CREATION_REFUSED";
            case "NOT_OBSERVED" -> "CONNECTOR_NOT_OBSERVED";
            case "PIN_HANDSHAKE_PROOF_MISSING" -> "CONNECTOR_PIN_HANDSHAKE_PROOF_MISSING";
            case "PIN_VERIFIED_DRIVER_OR_AUTH_FAILED" ->
                    "CONNECTOR_PIN_VERIFIED_DRIVER_OR_AUTH_FAILED";
            case "CERTIFICATE_OR_PEER_REFUSED" -> "CONNECTOR_CERTIFICATE_OR_PEER_REFUSED";
            case "TLS_SOCKET_REQUIRED" -> "CONNECTOR_TLS_SOCKET_REQUIRED";
            case "TLS_ALGORITHMS_REFUSED" -> "CONNECTOR_TLS_ALGORITHMS_REFUSED";
            case "SINGLE_LEAF_REQUIRED" -> "CONNECTOR_SINGLE_LEAF_REQUIRED";
            case "DER_BOUND" -> "CONNECTOR_DER_BOUND";
            case "LEAF_CHANGED" -> "CONNECTOR_LEAF_CHANGED";
            case "LEAF_EXPIRED" -> "CONNECTOR_LEAF_EXPIRED";
            case "LEAF_NOT_YET_VALID" -> "CONNECTOR_LEAF_NOT_YET_VALID";
            case "SELF_SIGNATURE_REFUSED" -> "CONNECTOR_SELF_SIGNATURE_REFUSED";
            case "SOCKET_PROOF_REQUIRED" -> "CONNECTOR_SOCKET_PROOF_REQUIRED";
            case "DEV_PEER_PROOF_REFUSED" -> "CONNECTOR_DEV_PEER_PROOF_REFUSED";
            case "DEV_PEER_PROOF_TIMEOUT" -> "CONNECTOR_DEV_PEER_PROOF_TIMEOUT";
            case "DEV_PEER_PROOF_REENTRANT" -> "CONNECTOR_DEV_PEER_PROOF_REENTRANT";
            case "DEV_PEER_BINDING_CHANGED" -> "CONNECTOR_DEV_PEER_BINDING_CHANGED";
            default -> "CONNECTOR_REFUSED";
        };
    }

    public static final class CatalogFailure extends SQLException {
        private static final long serialVersionUID = 1L;
        private final String reasonCode;

        private CatalogFailure(final String reason) {
            super("C78_CATALOG_" + reviewedReason(reason));
            reasonCode = reviewedReason(reason);
        }

        public String reasonCode() {
            return reasonCode;
        }

        private static String reviewedReason(final String reason) {
            try {
                return Reason.valueOf(reason).name();
            } catch (final IllegalArgumentException | NullPointerException unreviewed) {
                return "EXECUTION_FAILED";
            }
        }
    }

    private enum Reason {
        OCCURRENCE_ALREADY_CONSUMED,
        ADMISSION_REFUSED,
        ROW_LIMIT,
        MATERIAL_LIMIT,
        PREPOST_MISMATCH,
        EXECUTION_FAILED,
        CLOSE_UNCERTAIN,
        RECEIPT_UNCERTAIN,
        DEADLINE,
        METADATA_SHAPE,
        TARGET_VERSION_AUTH_CONTEXT,
        CONNECTOR_REFUSED,
        CONNECTOR_SCOPE_CREATION_REFUSED,
        CONNECTOR_NOT_OBSERVED,
        CONNECTOR_PIN_HANDSHAKE_PROOF_MISSING,
        CONNECTOR_PIN_VERIFIED_DRIVER_OR_AUTH_FAILED,
        CONNECTOR_CERTIFICATE_OR_PEER_REFUSED,
        CONNECTOR_TLS_SOCKET_REQUIRED,
        CONNECTOR_TLS_ALGORITHMS_REFUSED,
        CONNECTOR_SINGLE_LEAF_REQUIRED,
        CONNECTOR_DER_BOUND,
        CONNECTOR_LEAF_CHANGED,
        CONNECTOR_LEAF_EXPIRED,
        CONNECTOR_LEAF_NOT_YET_VALID,
        CONNECTOR_SELF_SIGNATURE_REFUSED,
        CONNECTOR_SOCKET_PROOF_REQUIRED,
        CONNECTOR_DEV_PEER_PROOF_REFUSED,
        CONNECTOR_DEV_PEER_PROOF_TIMEOUT,
        CONNECTOR_DEV_PEER_PROOF_REENTRANT,
        CONNECTOR_DEV_PEER_BINDING_CHANGED,
        CONNECTOR_REFUSED_CLOSE_UNCERTAIN,
        CONNECTOR_SCOPE_CREATION_REFUSED_CLOSE_UNCERTAIN,
        CONNECTOR_NOT_OBSERVED_CLOSE_UNCERTAIN,
        CONNECTOR_PIN_HANDSHAKE_PROOF_MISSING_CLOSE_UNCERTAIN,
        CONNECTOR_PIN_VERIFIED_DRIVER_OR_AUTH_FAILED_CLOSE_UNCERTAIN,
        CONNECTOR_CERTIFICATE_OR_PEER_REFUSED_CLOSE_UNCERTAIN,
        CONNECTOR_TLS_SOCKET_REQUIRED_CLOSE_UNCERTAIN,
        CONNECTOR_TLS_ALGORITHMS_REFUSED_CLOSE_UNCERTAIN,
        CONNECTOR_SINGLE_LEAF_REQUIRED_CLOSE_UNCERTAIN,
        CONNECTOR_DER_BOUND_CLOSE_UNCERTAIN,
        CONNECTOR_LEAF_CHANGED_CLOSE_UNCERTAIN,
        CONNECTOR_LEAF_EXPIRED_CLOSE_UNCERTAIN,
        CONNECTOR_LEAF_NOT_YET_VALID_CLOSE_UNCERTAIN,
        CONNECTOR_SELF_SIGNATURE_REFUSED_CLOSE_UNCERTAIN,
        CONNECTOR_SOCKET_PROOF_REQUIRED_CLOSE_UNCERTAIN,
        CONNECTOR_DEV_PEER_PROOF_REFUSED_CLOSE_UNCERTAIN,
        CONNECTOR_DEV_PEER_PROOF_TIMEOUT_CLOSE_UNCERTAIN,
        CONNECTOR_DEV_PEER_PROOF_REENTRANT_CLOSE_UNCERTAIN,
        CONNECTOR_DEV_PEER_BINDING_CHANGED_CLOSE_UNCERTAIN
    }
}
