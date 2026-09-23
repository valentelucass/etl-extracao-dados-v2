package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.SqlText;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Definição imutável de uma raiz do DAG, sem defaults de fonte, tenant, contrato ou lease. */
public final class RuntimeWorkloadDefinition {

    private final RuntimeWorkloadId id;
    private final String sourceKind;
    private final String sourceInstance;
    private final String tenantScope;
    private final String entity;
    private final ImmutableFingerprint contract;
    private final ImmutableFingerprint configuration;
    private final Duration leaseDuration;
    private final List<RuntimeWorkloadId> dependencies;

    public RuntimeWorkloadDefinition(
            final RuntimeWorkloadId id,
            final String sourceKind,
            final String sourceInstance,
            final String tenantScope,
            final String entity,
            final ImmutableFingerprint contract,
            final ImmutableFingerprint configuration,
            final Duration leaseDuration,
            final RuntimeWorkloadId... dependencies) {
        this.id = Objects.requireNonNull(id, "O workload é obrigatório.");
        this.sourceKind = required(sourceKind, 64, "O tipo da fonte é obrigatório.");
        this.sourceInstance = required(sourceInstance, 128, "A instância da fonte é obrigatória.");
        this.tenantScope = required(tenantScope, 128, "O escopo de tenant é obrigatório.");
        this.entity = required(entity, 128, "A entidade é obrigatória.");
        this.contract =
                Objects.requireNonNull(contract, "O fingerprint de contrato é obrigatório.");
        this.configuration =
                Objects.requireNonNull(
                        configuration, "O fingerprint de configuração é obrigatório.");
        this.leaseDuration = validLeaseDuration(leaseDuration);
        this.dependencies = sortedDistinctDependencies(dependencies);
        if (this.dependencies.contains(id)) {
            throw new IllegalArgumentException("Um workload não pode depender de si mesmo.");
        }
    }

    public RuntimeWorkloadId id() {
        return id;
    }

    public String sourceKind() {
        return sourceKind;
    }

    public String sourceInstance() {
        return sourceInstance;
    }

    public String tenantScope() {
        return tenantScope;
    }

    public String entity() {
        return entity;
    }

    public ImmutableFingerprint contract() {
        return contract;
    }

    public ImmutableFingerprint configuration() {
        return configuration;
    }

    public Duration leaseDuration() {
        return leaseDuration;
    }

    public void forEachDependency(final Consumer<RuntimeWorkloadId> consumer) {
        dependencies.forEach(Objects.requireNonNull(consumer, "O consumidor é obrigatório."));
    }

    private static List<RuntimeWorkloadId> sortedDistinctDependencies(
            final RuntimeWorkloadId... dependencies) {
        if (dependencies == null || dependencies.length > 64) {
            throw new IllegalArgumentException("As dependências do workload são inválidas.");
        }
        return Arrays.stream(dependencies)
                .map(
                        dependency ->
                                Objects.requireNonNull(dependency, "A dependência é obrigatória."))
                .sorted()
                .distinct()
                .toList();
    }

    private static Duration validLeaseDuration(final Duration value) {
        final Duration duration =
                Objects.requireNonNull(value, "A duração da lease é obrigatória.");
        if (duration.isNegative()
                || duration.isZero()
                || duration.compareTo(Duration.ofHours(24)) > 0
                || duration.toMillis() % 1_000 != 0) {
            throw new IllegalArgumentException(
                    "A duração da lease deve ser maior que zero e no máximo 24 horas.");
        }
        return duration;
    }

    private static String required(
            final String value, final int maximumLength, final String message) {
        if (value == null || value.length() > maximumLength) {
            throw new IllegalArgumentException(message);
        }
        final String normalized = SqlText.trimAsciiSpace(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
