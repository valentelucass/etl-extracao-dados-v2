package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageFetch;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfo;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Única composição autorizada para tráfego remoto de contrato.
 *
 * <p>Um único guard e um único teto compartilhado por entidade cobrem Data Export e GraphQL. A
 * classe não persiste dados nem escreve evidência por conta própria.
 */
public final class ContractRemoteExecution {

    private final ContractRunGuard runGuard;
    private final Map<DataExportTemplate, ContractRemoteCallBudget> callBudgets;
    private final ContractDataExportProbe dataExportProbe;
    private final ReadOnlyGraphQlParityHarness graphQlHarness;
    private final Optional<Contract4924DataExportProbe> auxiliary4924Probe;

    private ContractRemoteExecution(final ContractTestConfiguration configuration) {
        this.runGuard = new ContractRunGuard();
        this.callBudgets = createBudgets(configuration);
        this.dataExportProbe = new ContractDataExportProbe(configuration, runGuard, callBudgets);
        this.graphQlHarness =
                ReadOnlyGraphQlParityHarness.forContractExecution(configuration, runGuard);
        this.auxiliary4924Probe =
                configuration
                        .auxiliary4924()
                        .map(
                                auxiliary ->
                                        new Contract4924DataExportProbe(
                                                configuration.dataExportProperties(),
                                                auxiliary,
                                                runGuard));
    }

    public static ContractRemoteExecution open(final ContractTestConfiguration configuration) {
        ContractTestGate.requireEnabled();
        return new ContractRemoteExecution(
                Objects.requireNonNull(configuration, "A configuração de contrato é obrigatória."));
    }

    public DataExportTemplateInfo fetchInfo(final DataExportTemplate template) {
        return dataExportProbe.fetchInfo(template);
    }

    public DataExportPageFetch fetchDataExportPage(final DataExportPageRequest request) {
        return dataExportProbe.fetchPage(request);
    }

    public ContractGraphQlPage<ContractGraphQlColetaIdentity> fetchColetasPage(
            final ContractDateWindow window, final Optional<String> after, final int first) {
        return graphQlHarness.fetchColetasPage(
                window, after, first, budgetFor(DataExportTemplate.COLETAS));
    }

    public List<ContractGraphQlColetaIdentity> fetchAllColetas(
            final ContractDateWindow window, final int first) {
        return graphQlHarness.fetchAllColetas(window, first, budgetFor(DataExportTemplate.COLETAS));
    }

    public ContractGraphQlPage<ContractGraphQlFreteIdentity> fetchFretesPage(
            final ContractDateWindow window, final Optional<String> after, final int first) {
        return graphQlHarness.fetchFretesPage(
                window, after, first, budgetFor(DataExportTemplate.FRETES));
    }

    public List<ContractGraphQlFreteIdentity> fetchAllFretes(
            final ContractDateWindow window, final int first) {
        return graphQlHarness.fetchAllFretes(window, first, budgetFor(DataExportTemplate.FRETES));
    }

    public int callsMade(final DataExportTemplate template) {
        return budgetFor(template).callsMade();
    }

    public boolean isStopped() {
        return runGuard.isStopped();
    }

    /** A sonda 4924 continua opt-in, test-only e fora do enum de templates de produção. */
    public Optional<Contract4924DataExportProbe> auxiliary4924Probe() {
        return auxiliary4924Probe;
    }

    private ContractRemoteCallBudget budgetFor(final DataExportTemplate template) {
        final ContractRemoteCallBudget callBudget =
                callBudgets.get(
                        Objects.requireNonNull(template, "O template Data Export é obrigatório."));
        if (callBudget == null) {
            throw new IllegalArgumentException(
                    "O template Data Export não pertence à suíte de contrato.");
        }
        return callBudget;
    }

    private static Map<DataExportTemplate, ContractRemoteCallBudget> createBudgets(
            final ContractTestConfiguration configuration) {
        final Map<DataExportTemplate, ContractRemoteCallBudget> budgets =
                new EnumMap<>(DataExportTemplate.class);
        for (final DataExportTemplate template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            budgets.put(
                    template, new ContractRemoteCallBudget(configuration.maxCallsFor(template)));
        }
        return Map.copyOf(budgets);
    }
}
