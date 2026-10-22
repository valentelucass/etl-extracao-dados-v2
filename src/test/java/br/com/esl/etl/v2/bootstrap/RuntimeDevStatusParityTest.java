package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStatus;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

/** C54-PARITY-STATUS-01 component negatives; no supplier, physical session or Main. */
class RuntimeDevStatusParityTest {
    @Test
    void boundedCountersDoNotRetainUnknownInputOrAcceptAnEmptySample() {
        final var counter = new Coletas6908SampleExtraction.StatusParityCounter();
        assertEquals("NOT_VERIFIED_INSUFFICIENT_STATUS", counter.snapshot().outcome());
        counter.observe(
                "COMPONENT_PRIVATE_STATUS", ColetaStatus.resolve("COMPONENT_PRIVATE_STATUS", null));
        counter.observe(null, ColetaStatus.resolve(null, null));
        assertEquals(2, counter.snapshot().observedRows());
        assertEquals(2, counter.snapshot().unknownRows());
        assertEquals(0, counter.snapshot().comparedRows());
        assertEquals("NOT_VERIFIED_INSUFFICIENT_STATUS", counter.snapshot().outcome());
        assertFalse(counter.snapshot().toString().contains("COMPONENT_PRIVATE_STATUS"));
        assertNull(Coletas6908SampleExtraction.v1StatusProjection("COMPONENT_PRIVATE_STATUS"));
    }

    @Test
    void twelveSameRowComparisonsAreBoundedAndTheThirteenthDoesNotIncrement() {
        final var counter = new Coletas6908SampleExtraction.StatusParityCounter();
        for (int i = 0; i < 12; i++) {
            counter.observe(" DoNe ", ColetaStatus.resolve(" DoNe ", null));
        }
        assertEquals(
                new Coletas6908SampleExtraction.StatusParity(12, 12, 0, 0), counter.snapshot());
        assertEquals("PARTIAL_STATUS_CONFORMANT", counter.snapshot().outcome());
        assertThrows(
                IllegalStateException.class,
                () -> counter.observe("done", ColetaStatus.resolve("done", null)));
        assertEquals(12, counter.snapshot().observedRows());
    }

    @Test
    void sameRowReferenceDetectsADifferentMappedLabelOrTerminal() {
        final var counter = new Coletas6908SampleExtraction.StatusParityCounter();
        counter.observe(
                "done",
                new ColetaStatus.Resolved("done", "done", "COMPONENT_DIFFERENT", false, null, 0));
        assertEquals(1, counter.snapshot().divergentRows());
        assertEquals("PARTIAL_STATUS_DIVERGENCE", counter.snapshot().outcome());
        assertThrows(
                IllegalArgumentException.class,
                () -> new Coletas6908SampleExtraction.StatusParity(13, 13, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Coletas6908SampleExtraction.StatusParity(1, 1, 2, 0));
    }

    @Test
    void existingRealConsumerCollectsOnlySameMappedRamRowsAndKeepsPartialOutcome()
            throws Exception {
        final var config = RuntimeDevSampleExecutionTest.configuration();
        final var fixture = new RuntimeBootstrapTestFixture(RuntimeDevSampleExecutionTest.NOW);
        final var result =
                RuntimeDevSampleExecution.execute(
                        true,
                        config,
                        RuntimeDevSampleExecutionTest.request(config, 1, 1),
                        fixture.dataSource(),
                        RuntimeDevSampleExecutionTest.oneEntity(fixture, false, 12),
                        batch -> {},
                        () -> {});
        final var parity = result.statusParity().orElseThrow();
        assertEquals(12, parity.observedRows());
        assertEquals(result.expectation().orElseThrow().physicalRows(), parity.observedRows());
        assertTrue(result.partialPageBoundaryObserved());
        assertFalse(result.category() == RuntimeExitCategory.SUCCESS);
        assertFalse(
                result.toString()
                        .contains(result.expectation().orElseThrow().executionId().toString()));
    }

    @Test
    void failureBeforeStagingAndCancellationCannotPublishPartialParity() throws Exception {
        final var config = RuntimeDevSampleExecutionTest.configuration();
        final var failure = new RuntimeBootstrapTestFixture(RuntimeDevSampleExecutionTest.NOW);
        final var failed =
                RuntimeDevSampleExecution.execute(
                        true,
                        config,
                        RuntimeDevSampleExecutionTest.request(config, 1, 1),
                        failure.dataSource(),
                        RuntimeDevSampleExecutionTest.oneEntity(failure, false),
                        batch -> {
                            throw new IllegalStateException("COMPONENT_PRIVATE_FAILURE");
                        },
                        () -> {});
        assertTrue(failed.statusParity().isEmpty());
        final var cancelled = new RuntimeBootstrapTestFixture(RuntimeDevSampleExecutionTest.NOW);
        final var result =
                RuntimeDevSampleExecution.execute(
                        true,
                        config,
                        RuntimeDevSampleExecutionTest.request(config, 1, 1),
                        cancelled.dataSource(),
                        RuntimeDevSampleExecutionTest.oneEntity(cancelled, true),
                        batch -> {},
                        () -> {});
        assertTrue(result.statusParity().isEmpty());
    }

    @Test
    void receiptRequiresOwnerReadbackAndCleanupForTheSameObservedRows() {
        final var parity = new Coletas6908SampleExtraction.StatusParity(1, 1, 0, 0);
        final var receipt = new LinkedHashMap<String, Object>();
        RuntimeDevEngineExecution.publishStatusParity(
                receipt,
                parity,
                new RuntimeDevEngineBankBinding.SampleReadback(
                        false, true, true, true, true, 1, 1, 1, 1));
        assertTrue(receipt.isEmpty());
        RuntimeDevEngineExecution.publishStatusParity(
                receipt,
                parity,
                new RuntimeDevEngineBankBinding.SampleReadback(
                        true, false, true, true, true, 1, 1, 1, 1));
        assertTrue(receipt.isEmpty());
        RuntimeDevEngineExecution.publishStatusParity(
                receipt,
                parity,
                new RuntimeDevEngineBankBinding.SampleReadback(
                        true, true, true, true, true, 2, 1, 1, 1));
        assertTrue(receipt.isEmpty());
        RuntimeDevEngineExecution.publishStatusParity(
                receipt,
                parity,
                new RuntimeDevEngineBankBinding.SampleReadback(
                        true, true, true, true, true, 1, 1, 1, 1));
        assertEquals("PARTIAL_STATUS_CONFORMANT", receipt.get("sampleV1StatusParity"));
        assertEquals(false, receipt.get("v1FullFunctionalParityVerified"));
        assertEquals(7, receipt.size());
    }
}
