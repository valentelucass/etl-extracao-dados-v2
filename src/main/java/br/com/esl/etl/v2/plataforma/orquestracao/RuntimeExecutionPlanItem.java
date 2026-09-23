package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import java.util.Objects;

/** Uma ocorrência topologicamente ordenada, pronta para registro no control plane. */
public record RuntimeExecutionPlanItem(
        RuntimeWorkloadDefinition definition,
        RuntimeExecutionRequest request,
        ExecutionPartitionKey partition) {

    public RuntimeExecutionPlanItem {
        definition = Objects.requireNonNull(definition, "A definição do workload é obrigatória.");
        request = Objects.requireNonNull(request, "O pedido de execução é obrigatório.");
        partition = Objects.requireNonNull(partition, "A partição é obrigatória.");
        if (!definition.id().equals(request.workloadId())) {
            throw new IllegalArgumentException("A execução não corresponde ao workload definido.");
        }
    }
}
