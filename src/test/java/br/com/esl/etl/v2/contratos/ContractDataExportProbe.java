package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpAttempt;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageFetch;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfo;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportUnavailableException;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.HttpDataExportGateway;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.HttpDataExportTemplateInfoGateway;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/** Data Export de contrato: gate, teto real por tentativa e falhas sanitizadas sem causa remota. */
public final class ContractDataExportProbe {

    private final HttpDataExportGateway pageGateway;
    private final HttpDataExportTemplateInfoGateway infoGateway;
    private final Map<DataExportTemplate, ContractRemoteCallBudget> callBudgets;
    private final ContractRunGuard runGuard;
    private final EnumSet<DataExportTemplate> observedInfoTemplates =
            EnumSet.noneOf(DataExportTemplate.class);

    public ContractDataExportProbe(
            final ContractTestConfiguration configuration, final ContractRunGuard runGuard) {
        this(configuration, runGuard, createBudgets(configuration));
    }

    ContractDataExportProbe(
            final ContractTestConfiguration configuration,
            final ContractRunGuard runGuard,
            final Map<DataExportTemplate, ContractRemoteCallBudget> callBudgets) {
        Objects.requireNonNull(configuration, "A configuração de contrato é obrigatória.");
        this.runGuard = Objects.requireNonNull(runGuard, "O guard da execução é obrigatório.");
        this.callBudgets = validatedBudgets(callBudgets);
        this.pageGateway =
                new HttpDataExportGateway(
                        configuration.dataExportProperties(), this::beforeAttempt);
        this.infoGateway =
                new HttpDataExportTemplateInfoGateway(
                        configuration.dataExportProperties(), this::beforeAttempt);
    }

    public DataExportTemplateInfo fetchInfo(final DataExportTemplate template) {
        Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        ContractTestGate.requireEnabled();
        reserveSingleInfo(template);
        try {
            return infoGateway.fetchInfo(template);
        } catch (final DataExportUnavailableException exception) {
            throw sanitizeUnavailable(exception);
        } catch (final ContractRunStoppedException
                | ContractRemoteCallLimitExceededException exception) {
            throw exception;
        } catch (final IllegalStateException exception) {
            throw new ContractRemoteCallException("A sonda Data Export de metadados falhou.");
        }
    }

    public DataExportPageFetch fetchPage(final DataExportPageRequest request) {
        Objects.requireNonNull(request, "A requisição Data Export é obrigatória.");
        ContractTestGate.requireEnabled();
        try {
            return pageGateway.fetchWithDiagnostics(request);
        } catch (final DataExportUnavailableException exception) {
            throw sanitizeUnavailable(exception);
        } catch (final ContractRunStoppedException
                | ContractRemoteCallLimitExceededException exception) {
            throw exception;
        } catch (final IllegalStateException exception) {
            throw new ContractRemoteCallException("A sonda Data Export de página falhou.");
        }
    }

    public int callsMade(final DataExportTemplate template) {
        return budgetFor(template).callsMade();
    }

    private void beforeAttempt(final DataExportHttpAttempt attempt) {
        ContractTestGate.requireEnabled();
        runGuard.reserveCall(budgetFor(attempt.templateId()));
    }

    private RuntimeException sanitizeUnavailable(final DataExportUnavailableException exception) {
        if (exception.httpStatus().orElse(-1) == 429) {
            runGuard.stopForRateLimit();
            return new ContractRateLimitExceededException(exception.retryAfter());
        }
        return new ContractRemoteCallException("A sonda Data Export ficou indisponível.");
    }

    private ContractRemoteCallBudget budgetFor(final int templateId) {
        return budgetFor(templateFor(templateId));
    }

    private ContractRemoteCallBudget budgetFor(final DataExportTemplate template) {
        final ContractRemoteCallBudget budget =
                callBudgets.get(Objects.requireNonNull(template, "O template é obrigatório."));
        if (budget == null) {
            throw new IllegalArgumentException(
                    "O template Data Export não pertence à suíte de contrato.");
        }
        return budget;
    }

    private DataExportTemplate templateFor(final int templateId) {
        for (final DataExportTemplate template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            if (template.templateId() == templateId) {
                return template;
            }
        }
        throw new IllegalArgumentException(
                "O template Data Export não pertence à suíte de contrato.");
    }

    private synchronized void reserveSingleInfo(final DataExportTemplate template) {
        if (!observedInfoTemplates.add(template)) {
            throw new ContractRemoteCallException(
                    "A sonda de contrato permite somente um /info por template.");
        }
    }

    private static Map<DataExportTemplate, ContractRemoteCallBudget> createBudgets(
            final ContractTestConfiguration configuration) {
        Objects.requireNonNull(configuration, "A configuração de contrato é obrigatória.");
        final Map<DataExportTemplate, ContractRemoteCallBudget> budgets =
                new EnumMap<>(DataExportTemplate.class);
        for (final DataExportTemplate template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            budgets.put(
                    template, new ContractRemoteCallBudget(configuration.maxCallsFor(template)));
        }
        return budgets;
    }

    private static Map<DataExportTemplate, ContractRemoteCallBudget> validatedBudgets(
            final Map<DataExportTemplate, ContractRemoteCallBudget> budgets) {
        Objects.requireNonNull(budgets, "Os orçamentos de chamadas são obrigatórios.");
        final Map<DataExportTemplate, ContractRemoteCallBudget> result =
                new EnumMap<>(DataExportTemplate.class);
        for (final DataExportTemplate template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            final ContractRemoteCallBudget budget = budgets.get(template);
            if (budget == null) {
                throw new IllegalArgumentException("Falta orçamento para o template Data Export.");
            }
            result.put(template, budget);
        }
        return Map.copyOf(result);
    }
}
