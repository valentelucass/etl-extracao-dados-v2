package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.UUID;

/** Prova imutável emitida somente ao fechar uma validação completa da ocorrência. */
public final class ContractPromotionPermit {

    private final UUID executionId;
    private final ContractSourceKind sourceKind;
    private final String documentReference;
    private final String contractVersion;
    private final ImmutableFingerprint metadataFingerprint;
    private final ImmutableFingerprint responseFingerprint;
    private final ImmutableFingerprint contractFingerprint;
    private final ImmutableFingerprint runtimeConfigurationFingerprint;
    private final ImmutableFingerprint configurationFingerprint;
    private final SourceDataEffect dataEffect;
    private final boolean alertObserved;

    ContractPromotionPermit(
            final ContractExecutionBinding binding,
            final SourceDataEffect dataEffect,
            final boolean alertObserved) {
        executionId = binding.executionId();
        sourceKind = binding.sourceKind();
        documentReference = binding.documentReference();
        contractVersion = binding.contractVersion();
        metadataFingerprint = binding.metadataFingerprint();
        responseFingerprint = binding.responseFingerprint();
        contractFingerprint = binding.contractFingerprint();
        runtimeConfigurationFingerprint = binding.runtimeConfigurationFingerprint();
        configurationFingerprint = binding.configurationFingerprint();
        this.dataEffect =
                java.util.Objects.requireNonNull(dataEffect, "O efeito autorizado é obrigatório.");
        this.alertObserved = alertObserved;
    }

    public UUID executionId() {
        return executionId;
    }

    public ContractSourceKind sourceKind() {
        return sourceKind;
    }

    public String documentReference() {
        return documentReference;
    }

    public String contractVersion() {
        return contractVersion;
    }

    public ImmutableFingerprint metadataFingerprint() {
        return metadataFingerprint;
    }

    public ImmutableFingerprint responseFingerprint() {
        return responseFingerprint;
    }

    public ImmutableFingerprint contractFingerprint() {
        return contractFingerprint;
    }

    public ImmutableFingerprint runtimeConfigurationFingerprint() {
        return runtimeConfigurationFingerprint;
    }

    public ImmutableFingerprint configurationFingerprint() {
        return configurationFingerprint;
    }

    public SourceDataEffect dataEffect() {
        return dataEffect;
    }

    public boolean alertObserved() {
        return alertObserved;
    }

    @Override
    public String toString() {
        return "ContractPromotionPermit[dataEffect="
                + dataEffect
                + ", alertObserved="
                + alertObserved
                + "]";
    }
}
