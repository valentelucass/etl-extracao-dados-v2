package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryMaterial;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/** Modelo de persistência de resumos. Não interpreta T-SQL nem comprova locks físicos. */
final class RuntimeRecoverySyntheticState {
    final RuntimeSyntheticJdbc jdbc;
    final Map<String, ImmutableFingerprint> cycles = new HashMap<>();
    boolean failRead;
    boolean failSeal;
    boolean loseSealResponse;
    boolean obsoletePolicy;
    boolean delayResume;
    boolean coletaTypedNoop;
    boolean usersTypedNoop;
    boolean networkUnsupported;
    boolean missingRow;
    int loginTimeout = 5;
    int queryTimeout;
    int networkTimeout;
    int statementCloses;
    String timestampZone;
    volatile int cancelCalls;
    Runnable beforeResume = () -> {};
    Runnable afterRead = () -> {};

    RuntimeRecoverySyntheticState(final RuntimeSyntheticJdbc jdbc) {
        this.jdbc = jdbc;
    }

    Map<String, Object> execute(final Map<Integer, Object> p) throws SQLException {
        final String op = p.get(2).toString();
        final var attempt = jdbc.attempts.get(p.get(1).toString());
        if (op.equals("READ") && failRead) {
            throw new SQLException("synthetic read unavailable");
        }
        if (op.equals("SEAL")) {
            if (failSeal
                    || attempt == null
                    || !identity(attempt).equals(p.get(3))
                    || !attempt.terminal
                    || attempt.rows == 0
                    || attempt.rows != attempt.auditedRows
                    || attempt.pages < (attempt.start.get(6).equals("usuarios") ? 1 : 2)
                    || (attempt.start.get(6).equals("usuarios")
                            ? attempt.state != ExecutionState.PROMOTED || !attempt.qualityPassed
                            : attempt.state != ExecutionState.EXTRACTING)
                    || jdbc.loseLease) {
                throw new SQLException("seal gates");
            }
            final String sealHash = sealHash(attempt, p);
            if (attempt.sealHash != null && !attempt.sealHash.equals(sealHash)) {
                throw new SQLException("seal conflict");
            }
            attempt.seal = new HashMap<>(p);
            attempt.sealHash = sealHash;
            if (loseSealResponse) {
                loseSealResponse = false;
                throw new SQLException("lost seal response");
            }
            return Map.of();
        }
        if (op.equals("RESUME")) {
            beforeResume.run();
            if (delayResume) {
                final long until = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                while (cancelCalls == 0 && System.nanoTime() < until) {
                    try {
                        Thread.sleep(10);
                    } catch (final InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("interrupted", exception);
                    }
                }
                throw new java.sql.SQLTimeoutException("cancelled synthetic SQL");
            }
            final var current = snapshot(p, attempt);
            if (!current.get("reason").equals("ELIGIBLE")
                    || !current.get("revision").equals(p.get(7))) {
                throw new SQLException("revalidation refused", "S0001", 52305);
            }
            final String id = p.get(1).toString();
            if (attempt.state == ExecutionState.EXTRACTING) {
                invoke(
                        "ctl.usp_control_plane_transition_execution",
                        Map.of(
                                1,
                                id,
                                2,
                                "EXTRACTING",
                                3,
                                "EXTRACTED",
                                4,
                                "TRAVERSAL_AUDITED",
                                5,
                                Timestamp.from(jdbc.now)));
            }
            if (attempt.state == ExecutionState.EXTRACTED) {
                invoke(
                        "ctl.usp_control_plane_transition_execution",
                        Map.of(
                                1,
                                id,
                                2,
                                "EXTRACTED",
                                3,
                                "STAGED",
                                4,
                                "STAGING_COMPLETED",
                                5,
                                Timestamp.from(jdbc.now)));
            }
            final Map<Integer, Object> permitParameters =
                    Map.of(
                            1,
                            id,
                            2,
                            attempt.start.get(11),
                            3,
                            attempt.start.get(12),
                            4,
                            attempt.start.get(13),
                            5,
                            attempt.start.get(14));
            if (attempt.state == ExecutionState.STAGED) {
                invoke(
                        p.get(8).equals("fretes")
                                ? "core.usp_prepare_frete_candidate_set"
                                : "core.usp_prepare_staged_execution",
                        permitParameters);
            }
            invoke(
                    "recon.usp_evaluate_execution_data_quality",
                    Map.of(1, id, 2, p.get(5), 3, p.get(6)));
            invoke(
                    p.get(8).equals("usuarios")
                            ? "core.usp_apply_reconcile_publish_usuarios"
                            : p.get(8).equals("fretes")
                                    ? "core.usp_apply_reconcile_publish_fretes"
                                    : "core.usp_apply_reconcile_publish_coletas",
                    permitParameters);
            return Map.of();
        }
        final var result = snapshot(p, attempt);
        afterRead.run();
        return missingRow ? null : result;
    }

    private void invoke(final String operation, final Map<Integer, Object> p) throws SQLException {
        jdbc.operations.add(operation);
        if (operation.equals(jdbc.failOperation)) {
            throw new SQLException("synthetic unavailable");
        }
        jdbc.execute(operation, p);
    }

    private Map<String, Object> snapshot(
            final Map<Integer, Object> p, final RuntimeSyntheticJdbc.Attempt a) {
        final Map<String, Object> result = new HashMap<>();
        String reason = "NOT_FOUND";
        String quality = "ABSENT";
        boolean verified = false;
        boolean lease = false;
        String revision = "";
        if (a != null) {
            verified =
                    a.seal != null
                            && a.sealHash.equals(sealHash(a, p))
                            && a.terminal
                            && a.pages >= (a.start.get(6).equals("usuarios") ? 1 : 2)
                            && a.rows == a.auditedRows;
            lease = !a.state.isTerminal() && !jdbc.loseLease;
            quality = a.quality == null ? "ABSENT" : a.quality.get("evaluation_state").toString();
            if (obsoletePolicy && a.state != ExecutionState.PUBLISHED) {
                quality = "OBSOLETE";
            }
            revision =
                    hash(
                            identity(a)
                                    + "|"
                                    + a.state
                                    + "|"
                                    + a.sealHash
                                    + "|"
                                    + a.preparedRows
                                    + "|"
                                    + quality
                                    + "|"
                                    + lease);
            if (!identity(a).equals(p.get(3))) {
                reason = "INCONSISTENT";
            } else if (a.state == ExecutionState.PUBLISHED) {
                reason =
                        (verified || a.seal == null && !a.start.get(6).equals("usuarios"))
                                        && a.publication != null
                                        && (!(a.start.get(6).equals("coletas")
                                                        || a.start.get(6).equals("usuarios"))
                                                || a.typedReceiptHash != null
                                                        && a.typedReceiptHash.equals(
                                                                receiptHash(a.publication)))
                                        && quality.equals("PASSED")
                                        && a.quality.get("policy_version").equals(p.get(5))
                                        && a.quality.get("policy_fingerprint").equals(p.get(6))
                                        && a.preparedRows
                                                == ((Number) a.publication.get("candidate_rows"))
                                                        .longValue()
                                        && a.quality.get("passed_checks").equals(4)
                                ? "PUBLISHED"
                                : "INCONSISTENT";
            } else if (a.publication != null) {
                reason = "INCONSISTENT";
            } else if (a.state.isTerminal()) {
                reason = "TERMINAL";
            } else if (a.seal != null && !verified) {
                reason = "INCONSISTENT";
            } else if (!lease) {
                reason = "LEASE_LOST";
            } else if (!verified) {
                reason =
                        a.state == ExecutionState.EXTRACTING
                                ? a.rows == 0 ? "IN_PROGRESS" : "PARTIAL_EXTRACTION"
                                : "EVIDENCE_MISSING";
            } else if (quality.equals("FAILED")) {
                reason = "DQ_FAILED";
            } else if (quality.equals("OBSOLETE")) {
                reason = "DQ_OBSOLETE";
            } else if (a.state == ExecutionState.PROMOTED && a.preparedRows == 0) {
                reason = "INCONSISTENT";
            } else if (a.state == ExecutionState.RECONCILED || a.state == ExecutionState.PLANNED) {
                reason = "INCONSISTENT";
            } else {
                reason = "ELIGIBLE";
            }
            if (reason.equals("PUBLISHED")) {
                result.putAll(a.publication);
            }
            result.put("execution_state", a.state.name());
        }
        result.put("reason", reason);
        result.put("lease_valid", lease);
        result.put("contract_verified", verified);
        result.put("candidate_rows", a == null ? 0L : a.preparedRows);
        result.put("quality", quality);
        result.put("revision", revision);
        return result;
    }

    private String identity(final RuntimeSyntheticJdbc.Attempt a) {
        final var s = a.start;
        final var partition =
                new ExecutionPartitionKey(
                        s.get(3).toString(),
                        s.get(4).toString(),
                        s.get(5).toString(),
                        s.get(6).toString(),
                        ExecutionMode.valueOf(s.get(7).toString()),
                        ((Timestamp) s.get(8)).toInstant(),
                        ((Timestamp) s.get(9)).toInstant());
        final var start =
                new ControlPlaneStart(
                        UUID.fromString(s.get(1).toString()),
                        UUID.fromString(s.get(2).toString()),
                        partition,
                        s.get(10).toString(),
                        new ImmutableFingerprint(s.get(11).toString(), s.get(12).toString()),
                        new ImmutableFingerprint(s.get(13).toString(), s.get(14).toString()),
                        s.get(15).toString(),
                        Optional.ofNullable(s.get(16)).map(Object::toString).map(UUID::fromString),
                        Duration.ofSeconds(((Number) s.get(17)).longValue()),
                        ((Timestamp) s.get(18)).toInstant());
        return RuntimeRecoveryMaterial.identity(start, cycles.get(s.get(2).toString()));
    }

    private String sealHash(final RuntimeSyntheticJdbc.Attempt a, final Map<Integer, Object> p) {
        return hash(
                identity(a)
                        + "|"
                        + p.get(4)
                        + "|"
                        + p.get(5)
                        + "|"
                        + p.get(6)
                        + "|"
                        + a.auditedRows
                        + "|"
                        + a.pages);
    }

    private static String hash(final String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_16LE)));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String receiptHash(final Map<String, Object> receipt) {
        return hash(new java.util.TreeMap<>(receipt).toString());
    }

    /** Formato sintético limitado de scalars; nenhum objeto Java/guard/permit é serializado. */
    void save(final Path path) throws IOException {
        final Properties data = new Properties();
        data.setProperty("now", jdbc.now.toString());
        data.setProperty("attempts", Integer.toString(jdbc.attempts.size()));
        int index = 0;
        for (final var entry : jdbc.attempts.entrySet()) {
            final String prefix = "a" + index++ + ".";
            final var a = entry.getValue();
            for (int n = 1; n <= 18; n++) {
                put(data, prefix + "start." + n, a.start.get(n));
            }
            final var plan = cycles.get(a.start.get(2).toString());
            data.setProperty(prefix + "plan.version", plan.version());
            data.setProperty(prefix + "plan.sha", plan.sha256());
            data.setProperty(prefix + "state", a.state.name());
            data.setProperty(prefix + "rows", Long.toString(a.rows));
            data.setProperty(prefix + "audited", Long.toString(a.auditedRows));
            data.setProperty(prefix + "pages", Integer.toString(a.pages));
            data.setProperty(prefix + "terminal", Boolean.toString(a.terminal));
            data.setProperty(prefix + "applies", Integer.toString(a.applies));
            data.setProperty(prefix + "prepared", Long.toString(a.preparedRows));
            put(data, prefix + "sealHash", a.sealHash);
            put(data, prefix + "typedReceiptHash", a.typedReceiptHash);
            if (a.genericPublication != null) {
                a.genericPublication.forEach(
                        (k, v) -> put(data, prefix + "genericPublication." + k, v));
            }
            if (a.seal != null) {
                a.seal.forEach((k, v) -> put(data, prefix + "seal." + k, v));
            }
            if (a.publication != null) {
                a.publication.forEach((k, v) -> put(data, prefix + "publication." + k, v));
            }
            if (a.quality != null) {
                a.quality.forEach((k, v) -> put(data, prefix + "quality." + k, v));
            }
        }
        try (final var out = Files.newOutputStream(path)) {
            data.store(out, "synthetic summaries only");
        }
    }

    static RuntimeSyntheticJdbc load(final Path path) throws IOException {
        if (Files.size(path) > 131072) {
            throw new IOException("synthetic state exceeds bound");
        }
        final Properties data = new Properties();
        try (final var in = Files.newInputStream(path)) {
            data.load(in);
        }
        final var jdbc = new RuntimeSyntheticJdbc(Instant.parse(data.getProperty("now")));
        final int count = Integer.parseInt(data.getProperty("attempts"));
        if (count < 0 || count > 64) {
            throw new IOException("synthetic attempt bound");
        }
        for (int i = 0; i < count; i++) {
            final String p = "a" + i + ".";
            final Map<Integer, Object> start = new HashMap<>();
            for (int n = 1; n <= 18; n++) {
                start.put(n, get(data, p + "start." + n));
            }
            final var a = new RuntimeSyntheticJdbc.Attempt(start);
            a.state = ExecutionState.valueOf(data.getProperty(p + "state"));
            a.rows = Long.parseLong(data.getProperty(p + "rows"));
            a.auditedRows = Long.parseLong(data.getProperty(p + "audited"));
            a.pages = Integer.parseInt(data.getProperty(p + "pages"));
            a.terminal = Boolean.parseBoolean(data.getProperty(p + "terminal"));
            a.applies = Integer.parseInt(data.getProperty(p + "applies"));
            a.preparedRows = Long.parseLong(data.getProperty(p + "prepared"));
            a.sealHash = (String) get(data, p + "sealHash");
            if (a.sealHash != null) {
                a.seal = new HashMap<>();
                for (int n = 1; n <= 8; n++) {
                    a.seal.put(n, get(data, p + "seal." + n));
                }
            }
            a.publication = readMap(data, p + "publication.");
            a.genericPublication = readMap(data, p + "genericPublication.");
            a.typedReceiptHash = (String) get(data, p + "typedReceiptHash");
            a.quality = readMap(data, p + "quality.");
            a.qualityPassed =
                    a.quality != null && a.quality.get("evaluation_state").equals("PASSED");
            jdbc.attempts.put(start.get(1).toString(), a);
            jdbc.recovery.cycles.put(
                    start.get(2).toString(),
                    new ImmutableFingerprint(
                            data.getProperty(p + "plan.version"),
                            data.getProperty(p + "plan.sha")));
        }
        return jdbc;
    }

    private static Map<String, Object> readMap(final Properties data, final String prefix) {
        final Map<String, Object> result = new HashMap<>();
        for (final String key : data.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                result.put(key.substring(prefix.length()), get(data, key));
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static void put(final Properties data, final String key, final Object value) {
        if (value == null) {
            data.setProperty(key, "null");
        } else {
            data.setProperty(
                    key,
                    (value instanceof Timestamp
                                    ? "time:"
                                    : value instanceof Integer
                                            ? "int:"
                                            : value instanceof Long ? "long:" : "str:")
                            + value);
        }
    }

    private static Object get(final Properties data, final String key) {
        final String value = data.getProperty(key, "null");
        if (value.equals("null")) {
            return null;
        }
        return switch (value.substring(0, value.indexOf(':'))) {
            case "time" -> Timestamp.valueOf(value.substring(5));
            case "int" -> Integer.valueOf(value.substring(4));
            case "long" -> Long.valueOf(value.substring(5));
            default -> value.substring(4);
        };
    }
}
