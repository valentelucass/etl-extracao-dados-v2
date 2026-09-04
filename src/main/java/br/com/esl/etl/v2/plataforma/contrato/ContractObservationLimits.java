package br.com.esl.etl.v2.plataforma.contrato;

/** Tetos próprios do profiler, adicionais ao limite de bytes da resposta HTTP. */
public record ContractObservationLimits(int maximumDepth, int maximumPaths, int maximumNodes) {

    public static final int ABSOLUTE_MAXIMUM_DEPTH = 32;
    public static final int ABSOLUTE_MAXIMUM_PATHS = ContractResponse.MAXIMUM_FIELDS;
    public static final int ABSOLUTE_MAXIMUM_NODES = 100_000;

    public ContractObservationLimits {
        if (maximumDepth < 1
                || maximumDepth > ABSOLUTE_MAXIMUM_DEPTH
                || maximumPaths < 1
                || maximumPaths > ABSOLUTE_MAXIMUM_PATHS
                || maximumNodes < 1
                || maximumNodes > ABSOLUTE_MAXIMUM_NODES) {
            throw new IllegalArgumentException(
                    "Os limites de observação de contrato são inválidos.");
        }
    }

    public static ContractObservationLimits runtimeDefaults() {
        return new ContractObservationLimits(12, 2048, 50_000);
    }
}
