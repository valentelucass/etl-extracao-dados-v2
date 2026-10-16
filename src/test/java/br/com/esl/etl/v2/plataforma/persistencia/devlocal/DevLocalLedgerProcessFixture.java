package br.com.esl.etl.v2.plataforma.persistencia.devlocal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

/** A separate JVM exercises the OS lock and abrupt termination without shutdown hooks. */
public final class DevLocalLedgerProcessFixture {
    private DevLocalLedgerProcessFixture() {}

    public static void main(final String[] args) throws Exception {
        if (args[0].equals("lock")) {
            try (var ledger = DevLocalLedger.open(Path.of(args[1]))) {
                if (!UUID.fromString(ledger.workspaceId())
                        .toString()
                        .equals(ledger.workspaceId())) {
                    throw new AssertionError("WORKSPACE_IDENTITY_INVALID");
                }
                throw new AssertionError("SECOND_PROCESS_ACQUIRED_LOCK");
            } catch (final DevLocalLedger.Failure failure) {
                if (!failure.code().equals("WORKSPACE_LOCKED")) {
                    throw failure;
                }
                System.out.println("SECOND_PROCESS_LOCK_REFUSED");
            }
            return;
        }
        if (args[0].equals("crash")) {
            final var ledger = DevLocalLedger.open(Path.of(args[1]));
            ledger.start(
                    "crash-run", DevLocalLedger.sha256("fixture".getBytes(StandardCharsets.UTF_8)));
            ledger.event("crash-run", "BEFORE_CRASH", "SYNTHETIC_CRASH_EVIDENCE");
            ledger.output(
                    "crash-run",
                    "result.txt",
                    "SYNTHETIC_RETAINED".getBytes(StandardCharsets.UTF_8));
            Runtime.getRuntime().halt(23);
            return;
        }
        throw new IllegalArgumentException("FIXTURE_MODE_INVALID");
    }
}
