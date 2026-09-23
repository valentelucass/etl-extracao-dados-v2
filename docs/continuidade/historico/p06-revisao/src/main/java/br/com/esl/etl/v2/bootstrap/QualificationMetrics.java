package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** Eleven fixed counters follow actual lazy transports and staging; no payload is retained. */
public final class QualificationMetrics implements AnalyticScenarioObserver {
    public record Snapshot(
            long pages,
            long bytes,
            long records,
            long batches,
            int largestBatch,
            int inFlight,
            long heapBefore,
            long heapAfter,
            long elapsedMillis,
            List<InputSnapshot> inputs) {
        public Snapshot {
            if (inputs == null || inputs.size() != 11) {
                throw new IllegalArgumentException("QUAL_METRICS_INPUT_BOUND");
            }
            inputs = List.copyOf(inputs);
        }
    }

    public record InputSnapshot(
            Input input,
            long pages,
            long bytes,
            long records,
            long batches,
            int largestBatch,
            long largestPageBytes,
            int inFlight,
            long retainedPageBytes,
            long elapsedMillis,
            long rowLimit,
            long byteLimit,
            long pageLimit) {}

    private final QualificationConfiguration configuration;
    private final Runnable barrier;
    private final long start = System.nanoTime();
    private final long heapBefore = heap();
    private long pages;
    private long bytes;
    private long records;
    private long batches;
    private int largestBatch;
    private int inFlight;
    private boolean barrierReached;
    private Lane activeBatch;
    private final EnumMap<Input, Lane> inputs = new EnumMap<>(Input.class);

    public QualificationMetrics(
            final QualificationConfiguration configuration, final Runnable barrier) {
        this.configuration = Objects.requireNonNull(configuration);
        this.barrier = Objects.requireNonNull(barrier);
        for (final var input : Input.values()) {
            inputs.put(input, new Lane(input));
        }
    }

    @Override
    public AnalyticScenarioObserver forInput(final Input input) {
        return inputs.get(Objects.requireNonNull(input));
    }

    private void checkTime() {
        if (pages >= 10000
                || System.nanoTime() - start > configuration.caseSeconds() * 1_000_000_000L) {
            throw new IllegalStateException("QUAL_CAPTURE_LIMIT");
        }
    }

    private final class Lane implements AnalyticScenarioObserver {
        private final Input input;
        private long pageCount;
        private long byteCount;
        private long rowCount;
        private long batchCount;
        private int batchMaximum;
        private int pending;
        private long pageMaximum;
        private long retained;
        private long captureStarted;
        private long elapsedNanos;

        private Lane(final Input input) {
            this.input = input;
        }

        @Override
        public void beforeFetch() {
            checkTime();
            if (activeBatch != null || pageCount >= 10000) {
                throw new IllegalStateException("QUAL_CAPTURE_INFLIGHT");
            }
            if (captureStarted == 0) {
                captureStarted = System.nanoTime();
            }
            retained = 0;
        }

        @Override
        public void pageFetched(final DataExportPageResponse page, final long count) {
            pageBytes(count);
        }

        @Override
        public void pageBytes(final long count) {
            if (count < 0
                    || count > configuration.maximumBytes()
                    || byteCount + count > configuration.maximumBytes()
                    || bytes + count > configuration.maximumBytes()) {
                throw new IllegalStateException("QUAL_CAPTURE_BYTES");
            }
            checkTime();
            pageCount++;
            pages++;
            byteCount += count;
            bytes += count;
            retained = count;
            pageMaximum = Math.max(pageMaximum, count);
            if (!barrierReached) {
                barrierReached = true;
                barrier.run();
            }
        }

        @Override
        public void batchStarted(final int count) {
            checkTime();
            if (count < 0 || count > 1000 || activeBatch != null) {
                throw new IllegalStateException("QUAL_CAPTURE_INFLIGHT");
            }
            if (rowCount + count > configuration.maximumRows() || records + count > 100000) {
                throw new IllegalStateException("QUAL_CAPTURE_ROWS");
            }
            activeBatch = this;
            pending = count;
            inFlight = count;
            rowCount += count;
            records += count;
            batchCount++;
            batches++;
            batchMaximum = Math.max(batchMaximum, count);
            largestBatch = Math.max(largestBatch, count);
        }

        @Override
        public void batchStaged(final int count) {
            if (activeBatch != this || count != pending) {
                throw new IllegalStateException("QUAL_CAPTURE_BATCH_EQUATION");
            }
            activeBatch = null;
            pending = 0;
            inFlight = 0;
        }

        @Override
        public void captureClosed() {
            if (activeBatch == this) {
                activeBatch = null;
                inFlight = 0;
            }
            pending = 0;
            retained = 0;
            if (captureStarted != 0) {
                elapsedNanos += System.nanoTime() - captureStarted;
                captureStarted = 0;
            }
        }

        @Override
        public void rasterBeforeFetch() {
            beforeFetch();
        }

        @Override
        public void rasterPageFetched(final byte[] body) {
            pageBytes(body.length);
        }

        @Override
        public void rasterPageReleased(final byte[] body) {
            retained = 0;
        }

        private InputSnapshot snapshot() {
            return new InputSnapshot(
                    input,
                    pageCount,
                    byteCount,
                    rowCount,
                    batchCount,
                    batchMaximum,
                    pageMaximum,
                    pending,
                    retained,
                    (elapsedNanos + (captureStarted == 0 ? 0 : System.nanoTime() - captureStarted))
                            / 1_000_000,
                    configuration.maximumRows(),
                    configuration.maximumBytes(),
                    10000);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(
                pages,
                bytes,
                records,
                batches,
                largestBatch,
                inFlight,
                heapBefore,
                heap(),
                (System.nanoTime() - start) / 1_000_000,
                inputs.values().stream().map(Lane::snapshot).toList());
    }

    private static long heap() {
        final var runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
