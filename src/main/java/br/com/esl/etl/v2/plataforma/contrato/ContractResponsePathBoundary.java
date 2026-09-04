package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Fronteira fechada dos paths que o runtime pode transformar em shape de contrato.
 *
 * <p>Paths do baseline são aceitos. Paths aditivos só entram depois de existir uma allowlist exata
 * produzida a partir de fixture sanitizada; assim, chaves de mapas dinâmicos não viram artefato,
 * diff ou alerta por acidente. Um objeto só é opaco quando a política o declara explicitamente.
 */
public final class ContractResponsePathBoundary {

    private final boolean runtimeBound;
    private final Set<PathIdentity> allowedPaths;
    private final Set<PathIdentity> opaqueContainers;
    private final ImmutableFingerprint fingerprint;

    private ContractResponsePathBoundary(
            final boolean runtimeBound,
            final Set<PathIdentity> allowedPaths,
            final Set<PathIdentity> opaqueContainers,
            final ImmutableFingerprint fingerprint) {
        this.runtimeBound = runtimeBound;
        this.allowedPaths = Set.copyOf(allowedPaths);
        this.opaqueContainers = Set.copyOf(opaqueContainers);
        this.fingerprint = Objects.requireNonNull(fingerprint, "O fingerprint é obrigatório.");
    }

    public static ContractResponsePathBoundary forRuntime(
            final SourceContractRelease release, final ContractCompatibilityPolicy policy) {
        Objects.requireNonNull(release, "O release de contrato é obrigatório.");
        Objects.requireNonNull(policy, "A política de contrato é obrigatória.");
        if (!release.contractFingerprint().equals(policy.baselineContract())) {
            throw new ContractDriftException(ContractDriftException.Reason.BASELINE_MISMATCH, 0, 0);
        }
        final Set<PathIdentity> paths = new HashSet<>();
        for (final ContractResponse.Field field : release.response().fields()) {
            paths.add(path(field.scope(), field.path()));
        }
        for (final ContractAllowance allowance : policy.allowances()) {
            if (allowance.component() == ContractChange.Component.RESPONSE
                    && (allowance.kind() == ContractChange.Kind.OPTIONAL_FIELD_ADDED
                            || allowance.kind()
                                    == ContractChange.Kind.PREVIOUSLY_UNOBSERVED_SHAPE_OBSERVED)) {
                paths.add(allowedAddition(allowance));
            }
        }
        final Set<PathIdentity> opaqueContainers = validateOpaqueContainers(release, policy, paths);
        return new ContractResponsePathBoundary(
                true,
                paths,
                opaqueContainers,
                ContractCanonicalizer.responsePathBoundary(release, policy));
    }

    /**
     * Perfil permissivo exclusivo para autoria offline com fixtures já sanitizadas. Nunca é aceito
     * por uma configuração de observação runtime.
     */
    static ContractResponsePathBoundary syntheticFixtures() {
        return new ContractResponsePathBoundary(
                false, Set.of(), Set.of(), ContractCanonicalizer.syntheticResponsePathBoundary());
    }

    public ImmutableFingerprint fingerprint() {
        return fingerprint;
    }

    public boolean runtimeBound() {
        return runtimeBound;
    }

    void requireAllowed(final ContractResponse.FieldScope scope, final String path) {
        if (!runtimeBound || allowedPaths.contains(path(scope, path))) {
            return;
        }
        throw new ContractDriftException(ContractDriftException.Reason.BREAKING_CHANGE, 1, 0);
    }

    boolean shouldTraverse(final ContractResponse.FieldScope scope, final String path) {
        return !runtimeBound || !opaqueContainers.contains(path(scope, path));
    }

    @Override
    public String toString() {
        return "ContractResponsePathBoundary[runtimeBound="
                + runtimeBound
                + ", allowedPathCount="
                + allowedPaths.size()
                + ", opaquePathCount="
                + opaqueContainers.size()
                + "]";
    }

    private static PathIdentity allowedAddition(final ContractAllowance allowance) {
        final ContractResponse.FieldScope scope =
                allowance
                        .responseFieldScope()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Uma adição de resposta exige escopo explícito."));
        final String reportedPath = allowance.path();
        final String envelopePrefix = "/envelope";
        if (scope == ContractResponse.FieldScope.ENVELOPE) {
            if (!reportedPath.startsWith(envelopePrefix + '/')) {
                throw new IllegalArgumentException(
                        "Um path de envelope permitido possui formato inválido.");
            }
            return path(scope, reportedPath.substring(envelopePrefix.length()));
        }
        return path(scope, reportedPath);
    }

    private static PathIdentity path(final ContractResponse.FieldScope scope, final String path) {
        final ContractResponse.FieldScope requiredScope =
                Objects.requireNonNull(scope, "O escopo do path é obrigatório.");
        final String normalized = ContractText.pointer(path);
        if (ContractText.pointerSegments(normalized).length
                > ContractObservationLimits.ABSOLUTE_MAXIMUM_DEPTH) {
            throw new IllegalArgumentException("O path excede a profundidade contratual.");
        }
        return new PathIdentity(requiredScope, normalized);
    }

    private static Set<PathIdentity> validateOpaqueContainers(
            final SourceContractRelease release,
            final ContractCompatibilityPolicy policy,
            final Set<PathIdentity> allowedPaths) {
        final Set<PathIdentity> opaque = new HashSet<>();
        for (final ContractOpaquePath configured : policy.opaquePaths()) {
            final PathIdentity identity = path(configured.scope(), configured.path());
            final ContractResponse.Field baselineField =
                    release.response()
                            .find(configured.scope(), configured.path())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Um path opaco exige container no baseline."));
            if (baselineField.cardinality() != ContractResponse.Cardinality.OBJECT
                    || !baselineField
                            .jsonTypes()
                            .equals(java.util.List.of(ContractResponse.JsonType.OBJECT))) {
                throw new IllegalArgumentException("Um path opaco deve ser um objeto fechado.");
            }
            final String descendantPrefix = configured.path() + '/';
            if (allowedPaths.stream()
                    .anyMatch(
                            allowed ->
                                    allowed.scope() == configured.scope()
                                            && allowed.path().startsWith(descendantPrefix))) {
                throw new IllegalArgumentException(
                        "Um path opaco não pode possuir descendente contratual.");
            }
            opaque.add(identity);
        }
        return Set.copyOf(opaque);
    }

    private record PathIdentity(ContractResponse.FieldScope scope, String path) {}
}
