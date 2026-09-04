package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Configuração opcional e exclusivamente de teste da sonda financeira auxiliar 4924.
 *
 * <p>Ela não cria um módulo de domínio, uma tabela ou um fluxo recorrente. Quando qualquer chave
 * {@code CONTRACT_4924_*} é informada, todas as chaves obrigatórias precisam estar presentes antes
 * de uma conexão HTTP poder ser aberta.
 */
public final class Contract4924Configuration {

    public static final int TEMPLATE_ID = 4924;

    private static final Pattern TECHNICAL_NAME = Pattern.compile("[a-z][a-z0-9_]{0,127}");

    private final String root;
    private final String businessFilter;
    private final ContractDateWindow closedWindow;
    private final String orderBy;
    private final DataExportTransport approvedTransport;
    private final int maximumCalls;

    private Contract4924Configuration(
            final String root,
            final String businessFilter,
            final ContractDateWindow closedWindow,
            final String orderBy,
            final DataExportTransport approvedTransport,
            final int maximumCalls) {
        this.root = technicalName(root, "CONTRACT_4924_ROOT");
        this.businessFilter = technicalName(businessFilter, "CONTRACT_4924_BUSINESS_FILTER");
        this.closedWindow =
                Objects.requireNonNull(closedWindow, "A janela fechada 4924 é obrigatória.");
        this.orderBy = technicalName(orderBy, "CONTRACT_4924_ORDER_BY");
        this.approvedTransport =
                Objects.requireNonNull(approvedTransport, "O transporte 4924 é obrigatório.");
        if (maximumCalls <= 0) {
            throw new IllegalArgumentException("CONTRACT_4924_MAX_CALLS deve ser positivo.");
        }
        this.maximumCalls = maximumCalls;
    }

    /** Retorna vazio somente quando nenhuma chave da sonda auxiliar foi configurada. */
    static Optional<Contract4924Configuration> optionalFrom(final Map<String, String> environment) {
        Objects.requireNonNull(environment, "As variáveis de ambiente são obrigatórias.");
        final boolean hasAny4924Configuration =
                environment.keySet().stream()
                        .anyMatch(
                                key ->
                                        key.startsWith("CONTRACT_4924_")
                                                && hasConfiguredValue(environment, key));
        if (!hasAny4924Configuration) {
            return Optional.empty();
        }
        if (isExplicitlyDisabled(environment)) {
            if (hasConfiguredAuxiliaryKeyOtherThanEnabled(environment)) {
                throw new IllegalStateException(
                        "CONTRACT_4924_ENABLED=false não pode acompanhar configuração auxiliar.");
            }
            return Optional.empty();
        }
        requireEnabled(environment);
        final int configuredTemplateId =
                positiveInteger(
                        required(environment, "CONTRACT_4924_TEMPLATE_ID"),
                        "CONTRACT_4924_TEMPLATE_ID");
        if (configuredTemplateId != TEMPLATE_ID) {
            throw new IllegalArgumentException("CONTRACT_4924_TEMPLATE_ID deve ser 4924.");
        }
        return Optional.of(
                new Contract4924Configuration(
                        required(environment, "CONTRACT_4924_ROOT"),
                        required(environment, "CONTRACT_4924_BUSINESS_FILTER"),
                        ContractDateWindow.parse(
                                required(environment, "CONTRACT_4924_CLOSED_WINDOW"),
                                "CONTRACT_4924_CLOSED_WINDOW"),
                        required(environment, "CONTRACT_4924_ORDER_BY"),
                        transport(environment),
                        positiveInteger(
                                required(environment, "CONTRACT_4924_MAX_CALLS"),
                                "CONTRACT_4924_MAX_CALLS")));
    }

    public String root() {
        return root;
    }

    public String businessFilter() {
        return businessFilter;
    }

    public ContractDateWindow closedWindow() {
        return closedWindow;
    }

    public String orderBy() {
        return orderBy;
    }

    public DataExportTransport approvedTransport() {
        return approvedTransport;
    }

    public int maximumCalls() {
        return maximumCalls;
    }

    @Override
    public String toString() {
        return "Contract4924Configuration[redacted]";
    }

    private static void requireEnabled(final Map<String, String> environment) {
        final String enabled = required(environment, "CONTRACT_4924_ENABLED");
        if (!Boolean.parseBoolean(enabled)) {
            throw new IllegalStateException(
                    "CONTRACT_4924_ENABLED deve ser true quando a sonda auxiliar é configurada.");
        }
    }

    private static boolean isExplicitlyDisabled(final Map<String, String> environment) {
        final String enabled = environment.get("CONTRACT_4924_ENABLED");
        return enabled != null && "false".equalsIgnoreCase(enabled.trim());
    }

    private static boolean hasConfiguredAuxiliaryKeyOtherThanEnabled(
            final Map<String, String> environment) {
        return environment.keySet().stream()
                .filter(key -> key.startsWith("CONTRACT_4924_"))
                .filter(key -> !"CONTRACT_4924_ENABLED".equals(key))
                .anyMatch(key -> hasConfiguredValue(environment, key));
    }

    private static boolean hasConfiguredValue(
            final Map<String, String> environment, final String key) {
        final String value = environment.get(key);
        return value != null && !value.isBlank();
    }

    private static DataExportTransport transport(final Map<String, String> environment) {
        try {
            return DataExportTransport.parse(required(environment, "CONTRACT_4924_TRANSPORT"));
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException("CONTRACT_4924_TRANSPORT é inválido.");
        }
    }

    private static int positiveInteger(final String value, final String key) {
        try {
            final int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(key + " deve ser positivo.");
            }
            return parsed;
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException(key + " é numérico inválido.");
        }
    }

    private static String technicalName(final String value, final String key) {
        if (value == null || !TECHNICAL_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(key + " deve conter somente nome técnico.");
        }
        return value;
    }

    private static String required(final Map<String, String> environment, final String key) {
        final String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Configuração obrigatória ausente: " + key + ".");
        }
        return value.trim();
    }
}
