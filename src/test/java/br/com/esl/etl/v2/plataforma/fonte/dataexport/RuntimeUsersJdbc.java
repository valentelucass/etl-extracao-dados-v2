package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/** Bounded JDBC protocol, scripted aggregate receipts; never computes current/history. */
public final class RuntimeUsersJdbc {
    private final RuntimeSyntheticJdbc jdbc;

    public RuntimeUsersJdbc(final Instant now) {
        jdbc = new RuntimeSyntheticJdbc(now);
    }

    public DataSource dataSource() {
        return jdbc.dataSource();
    }

    public List<Call> calls() {
        return List.copyOf(jdbc.calls);
    }

    public List<String> operations() {
        return List.copyOf(jdbc.operations);
    }

    public long appliedOccurrences() {
        return jdbc.attempts.values().stream().mapToInt(a -> a.applies).sum();
    }

    public int openConnections() {
        return jdbc.openConnections;
    }

    public int heartbeats() {
        return jdbc.heartbeatCount;
    }

    public void afterBatch(final Runnable action) {
        jdbc.afterBatch = action;
    }

    public void lose(final String kind) {
        switch (kind) {
            case "apply" -> jdbc.loseApplyResponse = true;
            case "prepare" -> jdbc.losePrepareResponse = true;
            case "seal" -> jdbc.recovery.loseSealResponse = true;
            case "transition" -> jdbc.loseTransitionResponse = true;
            case "start" -> jdbc.loseStartResponse = true;
            case "quality" -> jdbc.failQuality = true;
            case "page" -> jdbc.failOperation = "ctl.usp_control_plane_record_page";
            case "partial" -> jdbc.failBatch = 2;
            case "lease" -> jdbc.loseLease = true;
            case "read" -> jdbc.recovery.failRead = true;
            case "receipt" -> jdbc.omitPublication = true;
            case "foreign" -> jdbc.wrongPublication = true;
            case "typed-noop" -> jdbc.recovery.usersTypedNoop = true;
            default -> throw new IllegalArgumentException("UNKNOWN_SYNTHETIC_FAILURE");
        }
    }

    public void tamper(final String kind) {
        jdbc.attempts
                .values()
                .forEach(
                        a -> {
                            switch (kind) {
                                case "typed" -> a.typedReceiptHash = null;
                                case "execution" ->
                                        a.publication.put(
                                                "execution_id",
                                                "00000000-0000-0000-0000-000000005999");
                                case "count-null" -> {
                                    a.publication.put("updated_rows", null);
                                    a.typedReceiptHash =
                                            RuntimeRecoverySyntheticState.receiptHash(
                                                    a.publication);
                                }
                                case "seal" -> a.seal = null;
                                default ->
                                        throw new IllegalArgumentException(
                                                "UNKNOWN_SYNTHETIC_TAMPER");
                            }
                        });
    }

    public record Call(String operation, Map<Integer, Object> parameters) {}
}
