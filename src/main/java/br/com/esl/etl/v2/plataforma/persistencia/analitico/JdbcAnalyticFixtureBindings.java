package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Role;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Assignments are declarations of the closed fixture, using captured SQL provenance in batches. */
public final class JdbcAnalyticFixtureBindings {
    private static final LocalDate START = LocalDate.of(2036, 4, 1);
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticFixtureBindings(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public long bind(
            final UUID run,
            final int revision,
            final boolean correctedBranch,
            final boolean branchChanged,
            final boolean omitManifestFleet,
            final CancellationToken cancellation)
            throws SQLException {
        if (revision < 1
                || revision > 1000
                || branchChanged && (!correctedBranch || revision == 1)) {
            throw new IllegalArgumentException("ANA_FIXTURE_BINDING_REVISION");
        }
        long assigned = 0;
        final var repository = new JdbcAnalyticDimensions(session);
        for (final var entity : Entity.values()) {
            if (!supported(entity)) {
                continue;
            }
            String after = "";
            while (true) {
                cancellation.throwIfCancellationRequested();
                final var sources = sourcesPage(run, entity, after, 6);
                if (sources.isEmpty()) {
                    break;
                }
                final var batch = new ArrayList<AnalyticDimensionBinding>(54);
                for (final var source : sources) {
                    final boolean corrected =
                            correctedBranch && (entity == Entity.FRETE || entity == Entity.MAN);
                    for (final var role : Role.values()) {
                        if (!roleAllowed(entity, role)) {
                            continue;
                        }
                        if (omitManifestFleet
                                && entity == Entity.MAN
                                && (role == Role.TRACTOR
                                        || role == Role.TRAILER1
                                        || role == Role.TRAILER2
                                        || role == Role.DRIVER)) {
                            continue;
                        }
                        final String registry = registry(entity, role, corrected);
                        batch.add(
                                new AnalyticDimensionBinding(
                                        entity,
                                        source.key(),
                                        source.execution(),
                                        role,
                                        registry,
                                        revision,
                                        START,
                                        START.plusDays(3),
                                        true,
                                        branchChanged && corrected && role == Role.BRANCH
                                                ? "synthetic-branch-a"
                                                : null,
                                        "synthetic-composed-assignment-v1"));
                    }
                }
                if (!batch.isEmpty()) {
                    assigned += repository.bindBatch(run, batch, cancellation);
                }
                after = sources.get(sources.size() - 1).key();
            }
        }
        return assigned;
    }

    public List<Source> sourcesPage(
            final UUID run, final Entity entity, final String after, final int maximum)
            throws SQLException {
        Objects.requireNonNull(run);
        if (!supported(entity)
                || after == null
                || after.length() > 256
                || maximum < 1
                || maximum > 6) {
            throw new IllegalArgumentException("ANA_FIXTURE_SOURCE_PAGE");
        }
        try (var connection = session.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT TOP(?) source_key,MIN(CONVERT(VARCHAR(36),execution_id)) execution_id,"
                                        + "COUNT(DISTINCT CONVERT(VARCHAR(36),execution_id)) versions"
                                        + " FROM core.analytic_lab_source_current WHERE run_id=? AND entity=? AND usable=1"
                                        + " AND source_key>? GROUP BY source_key ORDER BY source_key")) {
            statement.setQueryTimeout(20);
            statement.setFetchSize(6);
            statement.setInt(1, maximum);
            statement.setString(2, run.toString());
            statement.setString(3, entity.name());
            statement.setNString(4, after);
            try (var rows = statement.executeQuery()) {
                final var result = new ArrayList<Source>(6);
                while (rows.next()) {
                    if (result.size() == maximum || rows.getInt(3) != 1) {
                        throw new SQLException("ANA_FIXTURE_SOURCE_NOT_UNIVOCAL");
                    }
                    result.add(new Source(rows.getString(1), UUID.fromString(rows.getString(2))));
                }
                return List.copyOf(result);
            }
        }
    }

    private static boolean supported(final Entity entity) {
        if (entity == null) {
            return false;
        }
        return switch (entity) {
            case FRETE, LOC, MAN, COL, CAP, FAT, INV, SIN, COT -> true;
            default -> false;
        };
    }

    private static boolean roleAllowed(final Entity entity, final Role role) {
        return switch (entity) {
            case FRETE ->
                    switch (role) {
                        case BRANCH,
                                        DEST_BRANCH,
                                        PERFORMANCE_BRANCH,
                                        CURRENT_BRANCH,
                                        PAYER,
                                        SENDER,
                                        RECIPIENT,
                                        TRACTOR,
                                        DRIVER ->
                                true;
                        default -> false;
                    };
            case MAN ->
                    switch (role) {
                        case BRANCH, UNLOADING_BRANCH, TRACTOR, TRAILER1, TRAILER2, DRIVER -> true;
                        default -> false;
                    };
            case LOC ->
                    role == Role.BRANCH || role == Role.CURRENT_BRANCH || role == Role.DEST_BRANCH;
            case CAP -> role == Role.BRANCH || role == Role.ACCOUNT;
            case FAT -> role == Role.BRANCH || role == Role.PAYER;
            case SIN -> role == Role.BRANCH || role == Role.TRACTOR;
            case COL, INV, COT -> role == Role.BRANCH;
            default -> false;
        };
    }

    private static String registry(final Entity entity, final Role role, final boolean corrected) {
        return switch (role) {
            case BRANCH ->
                    corrected || entity == Entity.SIN ? "synthetic-branch-b" : "synthetic-branch-a";
            case DEST_BRANCH, PERFORMANCE_BRANCH, CURRENT_BRANCH, UNLOADING_BRANCH ->
                    "synthetic-branch-b";
            case PAYER, SENDER -> "synthetic-client-a";
            case RECIPIENT -> "synthetic-client-b";
            case TRACTOR -> "synthetic-tractor-a";
            case TRAILER1 -> "synthetic-trailer-a";
            case TRAILER2 -> "synthetic-trailer-b";
            case DRIVER -> "synthetic-driver-a";
            case ACCOUNT -> "synthetic-account-a";
            default -> throw new IllegalArgumentException("ANA_FIXTURE_DIMENSION_ROLE");
        };
    }

    public List<FiscalSource> fiscalSourcesPage(
            final UUID expansion, final long after, final int maximum) throws SQLException {
        Objects.requireNonNull(expansion);
        if (after < 0 || maximum < 1 || maximum > 64) {
            throw new IllegalArgumentException("ANA_FIXTURE_FISCAL_PAGE");
        }
        try (var connection = session.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT TOP(?) component_id,execution_id FROM core.expansion_lab_current_input"
                                        + " WHERE run_id=? AND vertical='FAT' AND component_id>? ORDER BY component_id")) {
            statement.setQueryTimeout(20);
            statement.setFetchSize(16);
            statement.setInt(1, maximum);
            statement.setString(2, expansion.toString());
            statement.setLong(3, after);
            try (var rows = statement.executeQuery()) {
                final var result = new ArrayList<FiscalSource>(64);
                while (rows.next()) {
                    if (result.size() == maximum) {
                        throw new SQLException("ANA_FIXTURE_FISCAL_RESULT_BOUND");
                    }
                    result.add(
                            new FiscalSource(rows.getLong(1), UUID.fromString(rows.getString(2))));
                }
                return List.copyOf(result);
            }
        }
    }

    public record Source(String key, UUID execution) {}

    public record FiscalSource(long component, UUID execution) {}
}
