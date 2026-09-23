package br.com.esl.etl.v2.contratos.medicao;

import java.util.Objects;

/** Gauge serial que mantém no máximo a referência da única página atualmente gerenciada. */
public final class ManagedPageGauge {

    private Object activePage;
    private boolean activePageConsumed;
    private long fetchedPages;
    private long consumedPages;
    private long records;
    private long bytes;
    private long inFlightPages;
    private long maxInFlightPages;
    private long acquisitions;
    private long releases;
    private long retainedPages;
    private long maxRetainedPages;

    /** Deve ser chamada pelo gateway antes de gerar ou delegar o próximo fetch. */
    public synchronized void beforeFetch() {
        if (activePage != null || inFlightPages != 0L) {
            throw new IllegalStateException("A página anterior ainda não foi liberada.");
        }
    }

    /** Registra a instância real retornada pelo gateway, inclusive uma terminal vazia. */
    public synchronized void pageFetched(final Object page, final long responseBytes) {
        beforeFetch();
        final Object requiredPage = Objects.requireNonNull(page, "A página buscada é obrigatória.");
        if (responseBytes < 0L) {
            throw new IllegalArgumentException("A quantidade de bytes não pode ser negativa.");
        }
        activePage = requiredPage;
        activePageConsumed = false;
        fetchedPages = Math.incrementExact(fetchedPages);
        acquisitions = Math.incrementExact(acquisitions);
        bytes = Math.addExact(bytes, responseBytes);
        inFlightPages = Math.incrementExact(inFlightPages);
        maxInFlightPages = Math.max(maxInFlightPages, inFlightPages);
    }

    /** Registra consumo exatamente uma vez e somente para a instância ativa. */
    public synchronized void pageConsumed(final Object page, final long pageRecords) {
        requireActiveIdentity(page);
        if (activePageConsumed) {
            throw new IllegalStateException("A página ativa já foi consumida.");
        }
        if (pageRecords <= 0L) {
            throw new IllegalArgumentException("Uma página consumida deve conter registros.");
        }
        consumedPages = Math.incrementExact(consumedPages);
        records = Math.addExact(records, pageRecords);
        activePageConsumed = true;
    }

    /** Libera a instância ativa; páginas terminais podem ser liberadas sem consumo. */
    public synchronized void pageReleased(final Object page) {
        requireActiveIdentity(page);
        releases = Math.incrementExact(releases);
        inFlightPages = Math.decrementExact(inFlightPages);
        activePage = null;
        activePageConsumed = false;
    }

    /** Marca retenção deliberada sem transferir a referência para o gauge. */
    public synchronized void pageRetained(final Object page) {
        requireActiveIdentity(page);
        retainedPages = Math.incrementExact(retainedPages);
        maxRetainedPages = Math.max(maxRetainedPages, retainedPages);
    }

    /** Concilia a liberação de uma referência mantida externamente pelo mutante. */
    public synchronized void retainedPageReleased(final Object page) {
        Objects.requireNonNull(page, "A página retida é obrigatória.");
        if (retainedPages == 0L) {
            throw new IllegalStateException("Não existe página retida para liberar.");
        }
        retainedPages = Math.decrementExact(retainedPages);
    }

    public synchronized MeasurementEvidence snapshot(final MeasurementPlan plan) {
        return new MeasurementEvidence(
                Objects.requireNonNull(plan, "O plano do snapshot é obrigatório."),
                fetchedPages,
                consumedPages,
                records,
                bytes,
                maxInFlightPages,
                inFlightPages,
                acquisitions,
                releases,
                maxRetainedPages,
                retainedPages);
    }

    private void requireActiveIdentity(final Object page) {
        final Object requiredPage = Objects.requireNonNull(page, "A página é obrigatória.");
        if (activePage == null) {
            throw new IllegalStateException("Não existe página ativa.");
        }
        if (activePage != requiredPage) {
            throw new IllegalArgumentException(
                    "O evento não pertence à instância de página ativa.");
        }
    }

    @Override
    public synchronized String toString() {
        return "ManagedPageGauge[fetchedPages="
                + fetchedPages
                + ", consumedPages="
                + consumedPages
                + ", inFlightPages="
                + inFlightPages
                + ", retainedPages="
                + retainedPages
                + "]";
    }
}
