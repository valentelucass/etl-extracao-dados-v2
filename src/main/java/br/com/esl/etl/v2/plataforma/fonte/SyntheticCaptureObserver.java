package br.com.esl.etl.v2.plataforma.fonte;

/** Bounded transport/staging telemetry. Implementations must never retain payloads. */
public interface SyntheticCaptureObserver {
    SyntheticCaptureObserver NONE = new SyntheticCaptureObserver() {};

    default void beforeFetch() {}

    default void pageBytes(long bytes) {}

    default void batchStarted(int records) {}

    default void batchStaged(int records) {}

    default void captureClosed() {}
}
