package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** A test-only, rollback-bound policy for the synthetic Coletas 6908 shadow trial. */
public final class Coletas6908SyntheticPolicy {
    private static final String RESOURCE = "/coletas6908/synthetic-quality-policy.sql";

    private Coletas6908SyntheticPolicy() {}

    /** Seed before building the operational request, inside the trial's existing transaction. */
    public static DataQualityPolicyReference seed(
            final ColetaShadowRollbackTrial trial,
            final String sourceInstance,
            final String tenantScope)
            throws SQLException {
        Objects.requireNonNull(trial, "trial");
        final DataQualityPolicyReference reference =
                seed(trial.controlPlaneDataSource(), sourceInstance, tenantScope);
        trial.checkpoint();
        return reference;
    }

    static DataQualityPolicyReference seed(
            final DataSource controlPlane, final String sourceInstance, final String tenantScope)
            throws SQLException {
        Objects.requireNonNull(controlPlane, "controlPlane");
        final String source = binding(sourceInstance);
        final String tenant = binding(tenantScope);
        final String version = "coletas6908-synthetic-" + UUID.randomUUID();
        final String expectedScope = scopeFingerprint(source, tenant);
        try (var connection = controlPlane.getConnection();
                var statement = connection.prepareStatement(script())) {
            statement.setQueryTimeout(10);
            statement.setString(1, version);
            statement.setString(2, source);
            statement.setString(3, tenant);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()
                        || !version.equals(rows.getString(1))
                        || !expectedScope.equals(rows.getString(3))
                        || rows.getLong(4) != 1
                        || rows.getLong(5) != 4) {
                    throw new SQLException("COL_6908_POLICY_READBACK_MISMATCH");
                }
                final DataQualityPolicyReference reference;
                try {
                    reference = new DataQualityPolicyReference(version, rows.getString(2));
                } catch (final IllegalArgumentException | NullPointerException invalid) {
                    throw new SQLException("COL_6908_POLICY_FINGERPRINT_INVALID", invalid);
                }
                if (rows.next()) {
                    throw new SQLException("COL_6908_POLICY_READBACK_MULTIPLE");
                }
                return reference;
            }
        }
    }

    static String scopeFingerprint(final String source, final String tenant) {
        final String material =
                "dq-scope-v1|"
                        + field("LOCAL_SHADOW")
                        + '|'
                        + field(source)
                        + '|'
                        + field(tenant)
                        + '|'
                        + field("coletas")
                        + '|'
                        + field("BACKFILL");
        try {
            final byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(material.getBytes(StandardCharsets.UTF_16LE));
            return HexFormat.of().formatHex(hash);
        } catch (final NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("COL_6908_POLICY_SHA256_UNAVAILABLE", unavailable);
        }
    }

    private static String field(final String value) {
        return value.length() * 2 + ":" + value;
    }

    private static String binding(final String value) {
        if (value == null
                || value.isBlank()
                || value.length() > 128
                || !value.trim().equals(value)) {
            throw new IllegalArgumentException("COL_6908_POLICY_BINDING_INVALID");
        }
        return value;
    }

    static String script() throws SQLException {
        try (var input = Coletas6908SyntheticPolicy.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new SQLException("COL_6908_POLICY_SCRIPT_MISSING");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException failure) {
            throw new SQLException("COL_6908_POLICY_SCRIPT_UNREADABLE", failure);
        }
    }
}
