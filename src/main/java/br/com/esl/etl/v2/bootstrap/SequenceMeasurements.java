package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Bounded observations of actual captures; no GC requests or production performance claims. */
public final class SequenceMeasurements implements AnalyticScenarioObserver {
    public record Sample(long millis, long heapBytes, long pages, long batches, String point) {}

    public record Snapshot(
            long pages,
            long bytes,
            long batches,
            long records,
            long maximumPageBytes,
            long maximumBatchFetchedBytes,
            long minimumHeapBytes,
            long maximumHeapBytes,
            long sampledEvents,
            List<Sample> samples) {
        public Snapshot {
            samples = List.copyOf(samples);
        }
    }

    private final long started = System.nanoTime();
    private final List<Sample> samples = new ArrayList<>(128);
    private final EnumMap<Input, AnalyticScenarioObserver> lanes = new EnumMap<>(Input.class);
    private long pages;
    private long bytes;
    private long batches;
    private long records;
    private long maximumPage;
    private long maximumBatchBytes;
    private long minHeap = Long.MAX_VALUE;
    private long maxHeap;
    private long events;

    public SequenceMeasurements(final AnalyticScenarioObserver delegate) {
        for (final var input : Input.values()) {
            lanes.put(input, new Lane(delegate.forInput(input)));
        }
        sample("START");
    }

    @Override
    public AnalyticScenarioObserver forInput(final Input input) {
        return lanes.get(input);
    }

    public void sample(final String point) {
        if (!java.util.Set.of("START", "PAGE", "BATCH", "CLOSED", "STAGE", "FINISH")
                .contains(point)) {
            throw new IllegalArgumentException("SEQUENCE_MEASUREMENT_POINT");
        }
        final var runtime = Runtime.getRuntime();
        final long heap = runtime.totalMemory() - runtime.freeMemory();
        minHeap = Math.min(minHeap, heap);
        maxHeap = Math.max(maxHeap, heap);
        events++;
        if (samples.size() == 128) {
            // Thin the trace across time; min/max still include every event observed above.
            for (int index = 1; index < 64; index++) {
                samples.set(index, samples.get(index * 2));
            }
            samples.subList(64, 128).clear();
        }
        samples.add(
                new Sample((System.nanoTime() - started) / 1_000_000, heap, pages, batches, point));
    }

    public Snapshot snapshot() {
        sample("FINISH");
        return new Snapshot(
                pages,
                bytes,
                batches,
                records,
                maximumPage,
                maximumBatchBytes,
                minHeap,
                maxHeap,
                events,
                samples);
    }

    private final class Lane implements AnalyticScenarioObserver {
        private final AnalyticScenarioObserver delegate;
        private long fetchedBytes;

        private Lane(final AnalyticScenarioObserver delegate) {
            this.delegate = delegate;
        }

        @Override
        public void beforeFetch() {
            delegate.beforeFetch();
        }

        private void fetched(final long count) {
            pages++;
            bytes += count;
            fetchedBytes += count;
            maximumPage = Math.max(maximumPage, count);
            sample("PAGE");
        }

        @Override
        public void pageFetched(final DataExportPageResponse page, final long count) {
            delegate.pageFetched(page, count);
            fetched(count);
        }

        @Override
        public void pageBytes(final long count) {
            delegate.pageBytes(count);
            fetched(count);
        }

        @Override
        public void batchStarted(final int count) {
            delegate.batchStarted(count);
            batches++;
            records += count;
            maximumBatchBytes = Math.max(maximumBatchBytes, fetchedBytes);
            fetchedBytes = 0;
            sample("BATCH");
        }

        @Override
        public void batchStaged(final int count) {
            delegate.batchStaged(count);
        }

        @Override
        public void captureClosed() {
            delegate.captureClosed();
            fetchedBytes = 0;
            sample("CLOSED");
        }

        @Override
        public void rasterBeforeFetch() {
            delegate.rasterBeforeFetch();
        }

        @Override
        public void rasterPageFetched(final byte[] body) {
            delegate.rasterPageFetched(body);
            fetched(body.length);
        }

        @Override
        public void rasterPageReleased(final byte[] body) {
            delegate.rasterPageReleased(body);
        }
    }
}
