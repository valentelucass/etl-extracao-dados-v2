package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalCaptureSource;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Binds one independently executed capture to the explicit synthetic snapshot and run. */
public record CollectionSweepInput(
        UUID run, LocalDate date, String snapshotFingerprint, RelationalCaptureSource source) {
    public CollectionSweepInput {
        Objects.requireNonNull(run);
        Objects.requireNonNull(date);
        Objects.requireNonNull(source);
        if (snapshotFingerprint == null || !snapshotFingerprint.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("ANA_SWEEP_INPUT_FINGERPRINT");
        }
    }

    public void validate(final SyntheticCollectionSnapshot snapshot) {
        if (!run.equals(snapshot.run())
                || !date.equals(snapshot.date())
                || !snapshotFingerprint.equals(snapshot.fingerprint())) {
            throw new IllegalArgumentException("ANA_SWEEP_INPUT_SCOPE");
        }
    }
}
