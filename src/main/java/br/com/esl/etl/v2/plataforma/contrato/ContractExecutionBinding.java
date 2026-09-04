package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.Objects;
import java.util.UUID;

/** Manifesto sanitizado que vincula release, política e configuração a uma ocorrência. */
public final class ContractExecutionBinding {

    private final UUID executionId;
    private final ContractSourceKind sourceKind;
    private final String documentReference;
    private final String contractVersion;
    private final ImmutableFingerprint metadataFingerprint;
    private final ImmutableFingerprint responseFingerprint;
    private final ImmutableFingerprint contractFingerprint;
    private final ImmutableFingerprint policyFingerprint;
    private final ImmutableFingerprint runtimeConfigurationFingerprint;
    private final ContractObservationLimits observationLimits;
    private final ImmutableFingerprint configurationFingerprint;

    private ContractExecutionBinding(
            final UUID executionId,
            final SourceContractRelease release,
            final ContractCompatibilityPolicy policy,
            final ImmutableFingerprint runtimeConfigurationFingerprint,
            final ContractObservationLimits observationLimits) {
        this.executionId = executionId;
        sourceKind = release.sourceKind();
        documentReference = release.documentReference();
        contractVersion = release.contractVersion();
        metadataFingerprint = release.metadataFingerprint();
        responseFingerprint = release.responseFingerprint();
        contractFingerprint = release.contractFingerprint();
        policyFingerprint = policy.fingerprint();
        this.runtimeConfigurationFingerprint = runtimeConfigurationFingerprint;
        this.observationLimits = observationLimits;
        configurationFingerprint =
                ContractCanonicalizer.boundConfiguration(
                        release, policy, runtimeConfigurationFingerprint, observationLimits);
    }

    public static ContractExecutionBinding create(
            final UUID executionId,
            final SourceContractRelease release,
            final ContractCompatibilityPolicy policy,
            final ImmutableFingerprint runtimeConfiguration) {
        return create(
                executionId,
                release,
                policy,
                runtimeConfiguration,
                ContractObservationLimits.runtimeDefaults());
    }

    public static ContractExecutionBinding create(
            final UUID executionId,
            final SourceContractRelease release,
            final ContractCompatibilityPolicy policy,
            final ImmutableFingerprint runtimeConfiguration,
            final ContractObservationLimits observationLimits) {
        Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        Objects.requireNonNull(release, "O release de contrato é obrigatório.");
        Objects.requireNonNull(policy, "A política de contrato é obrigatória.");
        Objects.requireNonNull(
                runtimeConfiguration, "O fingerprint de configuração runtime é obrigatório.");
        Objects.requireNonNull(observationLimits, "Os limites de observação são obrigatórios.");
        ContractText.version(runtimeConfiguration.version());
        if (!release.contractFingerprint().equals(policy.baselineContract())) {
            throw new ContractDriftException(ContractDriftException.Reason.BASELINE_MISMATCH, 0, 0);
        }
        return new ContractExecutionBinding(
                executionId, release, policy, runtimeConfiguration, observationLimits);
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

    public ImmutableFingerprint policyFingerprint() {
        return policyFingerprint;
    }

    public ImmutableFingerprint runtimeConfigurationFingerprint() {
        return runtimeConfigurationFingerprint;
    }

    public ContractObservationLimits observationLimits() {
        return observationLimits;
    }

    public ImmutableFingerprint configurationFingerprint() {
        return configurationFingerprint;
    }

    public void verify(final ControlPlaneStart start) {
        Objects.requireNonNull(start, "O início da ocorrência é obrigatório.");
        if (!executionId.equals(start.executionId())
                || !contractFingerprint.equals(start.contract())
                || !configurationFingerprint.equals(start.configuration())) {
            throw new ContractDriftException(
                    ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, 0, 0);
        }
    }

    public void verifySource(
            final ContractSourceKind expectedSourceKind, final String expectedDocumentReference) {
        if (sourceKind != Objects.requireNonNull(expectedSourceKind, "A origem é obrigatória.")
                || !documentReference.equals(
                        ContractText.documentReference(expectedDocumentReference))) {
            throw new ContractDriftException(
                    ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, 0, 0);
        }
    }

    boolean matches(final SourceContractRelease release, final ContractCompatibilityPolicy policy) {
        return sourceKind == release.sourceKind()
                && documentReference.equals(release.documentReference())
                && contractVersion.equals(release.contractVersion())
                && metadataFingerprint.equals(release.metadataFingerprint())
                && responseFingerprint.equals(release.responseFingerprint())
                && contractFingerprint.equals(release.contractFingerprint())
                && policyFingerprint.equals(policy.fingerprint());
    }

    @Override
    public String toString() {
        return "ContractExecutionBinding[contractVersion="
                + contractVersion
                + ", metadataFingerprintVersion="
                + metadataFingerprint.version()
                + ", responseFingerprintVersion="
                + responseFingerprint.version()
                + ", policyFingerprintVersion="
                + policyFingerprint.version()
                + ", runtimeConfigurationVersion="
                + runtimeConfigurationFingerprint.version()
                + "]";
    }
}
