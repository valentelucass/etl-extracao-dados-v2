package br.com.esl.etl.v2.plataforma.controle;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Porta do estado durável de execução; a implementação SQL é a autoridade de concorrência e do
 * relógio técnico persistido. Os instantes enviados em registerSource/startCycle/start/page/count/
 * transition são observações do caller para compatibilidade do comando, não timestamps
 * autoritativos de auditoria.
 */
public interface ControlPlane {

    void registerSource(ControlPlaneSource source);

    void startCycle(ControlPlaneCycle cycle);

    void startExecution(ControlPlaneStart start);

    void heartbeat(UUID executionId, Instant heartbeatAt, Duration leaseExtension);

    void recordPage(ControlPlanePage page);

    void recordCounts(ControlPlaneCounts counts);

    void transition(ControlPlaneTransition transition);

    void registerIncrementalFrontier(
            ExecutionPartitionKey incrementalPartition,
            Instant initialContiguousEnd,
            Instant registeredAt);

    ControlPlaneRecoveryResult recoverStaleExecutions();
}
