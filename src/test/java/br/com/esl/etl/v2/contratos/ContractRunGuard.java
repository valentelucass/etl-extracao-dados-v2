package br.com.esl.etl.v2.contratos;

import java.util.Objects;

/** Estado compartilhado que aplica orçamento e interrompe a suíte após o primeiro rate limit. */
public final class ContractRunGuard {

    private boolean stopped;

    public synchronized void reserveCall(final ContractRemoteCallBudget callBudget) {
        Objects.requireNonNull(callBudget, "O orçamento de chamadas é obrigatório.");
        if (stopped) {
            throw new ContractRunStoppedException();
        }
        try {
            callBudget.reserveCall();
        } catch (final ContractRemoteCallLimitExceededException exception) {
            stopped = true;
            throw exception;
        }
    }

    public synchronized void stopForRateLimit() {
        stopped = true;
    }

    public synchronized boolean isStopped() {
        return stopped;
    }
}
