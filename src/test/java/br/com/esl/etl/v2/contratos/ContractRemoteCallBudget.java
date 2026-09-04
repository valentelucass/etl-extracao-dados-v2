package br.com.esl.etl.v2.contratos;

/**
 * Limite explícito de chamadas remotas para impedir que a suíte extrapole a autorização recebida.
 */
public final class ContractRemoteCallBudget {

    private final int maximumCalls;
    private int callsMade;

    public ContractRemoteCallBudget(final int maximumCalls) {
        if (maximumCalls <= 0) {
            throw new IllegalArgumentException("O teto de chamadas deve ser positivo.");
        }
        this.maximumCalls = maximumCalls;
    }

    public synchronized void reserveCall() {
        if (callsMade >= maximumCalls) {
            throw new ContractRemoteCallLimitExceededException(maximumCalls);
        }
        callsMade++;
    }

    public synchronized int callsMade() {
        return callsMade;
    }

    public int maximumCalls() {
        return maximumCalls;
    }
}
