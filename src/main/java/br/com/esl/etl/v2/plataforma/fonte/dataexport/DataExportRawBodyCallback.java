package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Instant;

/** Closed RAM-only callback. No public arbitrary callback, headers, URI or credential surface. */
sealed interface DataExportRawBodyCallback permits ObservedExpansionHttpTransport.RawCapture {
    void checkpoint();

    void stopSource();

    void received(byte[] completeSubscriberBody, Instant receivedAt);
}
