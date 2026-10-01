package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QualificationMetricsContractTest {
    @Test
    void accountsForSyntheticCaptureAndReleasesRetainedPagesWithoutSql() throws Exception {
        final var barriers = new AtomicInteger();
        final var metrics = new QualificationMetrics(configuration(), barriers::incrementAndGet);
        final var first = metrics.forInput(AnalyticScenarioObserver.Input.CAP);
        final var second = metrics.forInput(AnalyticScenarioObserver.Input.FRE);
        first.beforeFetch();
        first.pageBytes(42);
        first.batchStarted(2);
        first.batchStaged(2);
        first.captureClosed();
        second.rasterBeforeFetch();
        final byte[] body = new byte[16];
        second.rasterPageFetched(body);
        second.rasterPageReleased(body);
        second.batchStarted(1);
        second.captureClosed();

        final var snapshot = metrics.snapshot();
        assertEquals(1, barriers.get());
        assertEquals(2, snapshot.pages());
        assertEquals(58, snapshot.bytes());
        assertEquals(3, snapshot.records());
        assertEquals(2, snapshot.batches());
        assertEquals(2, snapshot.largestBatch());
        assertEquals(0, snapshot.inFlight());
        assertEquals(11, snapshot.inputs().size());
        assertTrue(snapshot.inputs().stream().allMatch(input -> input.retainedPageBytes() == 0));
    }

    @Test
    void rejectsQuotaOverflowAndCrossLaneBatchCompletion() throws Exception {
        final var config = configuration();
        final var metrics = new QualificationMetrics(config, () -> {});
        final var first = metrics.forInput(AnalyticScenarioObserver.Input.CAP);
        final var second = metrics.forInput(AnalyticScenarioObserver.Input.FRE);
        assertThrows(IllegalStateException.class, () -> first.pageBytes(-1));
        assertThrows(
                IllegalStateException.class,
                () -> first.pageBytes((long) config.maximumBytes() + 1));
        assertThrows(
                IllegalStateException.class, () -> first.batchStarted(config.maximumRows() + 1));
        first.beforeFetch();
        first.pageBytes(8);
        first.batchStarted(2);
        assertThrows(IllegalStateException.class, second::beforeFetch);
        assertThrows(IllegalStateException.class, () -> second.batchStaged(2));
        assertThrows(IllegalStateException.class, () -> first.batchStaged(1));
        first.batchStaged(2);
        first.captureClosed();
        assertEquals(0, metrics.snapshot().inFlight());
    }

    private static QualificationConfiguration configuration() throws Exception {
        return QualificationConfiguration.read(
                Path.of("src/main/resources/qualification-laboratory/config.synthetic.json"));
    }
}
