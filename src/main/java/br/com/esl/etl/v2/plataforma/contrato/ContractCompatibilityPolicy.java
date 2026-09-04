package br.com.esl.etl.v2.plataforma.contrato;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Política versionada e vinculada a um release baseline exato. */
public record ContractCompatibilityPolicy(
        String policyVersion,
        ImmutableFingerprint baselineContract,
        List<ContractAllowance> allowances,
        List<ContractOpaquePath> opaquePaths,
        ImmutableFingerprint fingerprint) {

    public static final int MAXIMUM_ALLOWANCES = 128;
    public static final int MAXIMUM_OPAQUE_PATHS = 128;

    public ContractCompatibilityPolicy {
        policyVersion = ContractText.version(policyVersion);
        baselineContract =
                Objects.requireNonNull(baselineContract, "O baseline da política é obrigatório.");
        Objects.requireNonNull(allowances, "As permissões de contrato são obrigatórias.");
        if (allowances.size() > MAXIMUM_ALLOWANCES) {
            throw new IllegalArgumentException("A política excede o limite de permissões.");
        }
        final List<ContractAllowance> sorted = new ArrayList<>(allowances.size());
        final Set<ContractAllowance> unique = new HashSet<>();
        final Set<String> uniqueChangeIdentities = new HashSet<>();
        for (final ContractAllowance allowance : allowances) {
            final ContractAllowance required =
                    Objects.requireNonNull(allowance, "A política não pode conter permissão nula.");
            if (!unique.add(required)) {
                throw new IllegalArgumentException("A política contém permissão duplicada.");
            }
            final String changeIdentity =
                    required.component().name()
                            + ':'
                            + required.kind().name()
                            + ':'
                            + required.responseFieldScope().map(Enum::name).orElse("")
                            + ':'
                            + required.path().toLowerCase(java.util.Locale.ROOT);
            if (!uniqueChangeIdentities.add(changeIdentity)) {
                throw new IllegalArgumentException(
                        "A política contém permissões ambíguas para a mesma mudança.");
            }
            sorted.add(required);
        }
        sorted.sort(
                Comparator.comparing(ContractAllowance::component)
                        .thenComparing(
                                allowance ->
                                        allowance.responseFieldScope().map(Enum::name).orElse(""))
                        .thenComparing(ContractAllowance::path)
                        .thenComparing(ContractAllowance::kind)
                        .thenComparing(
                                allowance ->
                                        allowance
                                                .approvedResponseField()
                                                .map(ContractCanonicalizer::fieldSignature)
                                                .orElse(""))
                        .thenComparing(allowance -> allowance.changeSignature().version())
                        .thenComparing(allowance -> allowance.changeSignature().sha256()));
        allowances = List.copyOf(sorted);
        Objects.requireNonNull(opaquePaths, "Os paths opacos são obrigatórios.");
        if (opaquePaths.size() > MAXIMUM_OPAQUE_PATHS) {
            throw new IllegalArgumentException("A política excede o limite de paths opacos.");
        }
        final List<ContractOpaquePath> sortedOpaquePaths = new ArrayList<>(opaquePaths.size());
        final Set<String> uniqueOpaquePaths = new HashSet<>();
        for (final ContractOpaquePath opaquePath : opaquePaths) {
            final ContractOpaquePath required =
                    Objects.requireNonNull(
                            opaquePath, "A política não pode conter path opaco nulo.");
            final String identity =
                    required.scope().name()
                            + ':'
                            + required.path().toLowerCase(java.util.Locale.ROOT);
            if (!uniqueOpaquePaths.add(identity)) {
                throw new IllegalArgumentException(
                        "A política contém path opaco duplicado ou ambíguo.");
            }
            sortedOpaquePaths.add(required);
        }
        sortedOpaquePaths.sort(
                Comparator.comparing(ContractOpaquePath::scope)
                        .thenComparing(ContractOpaquePath::path));
        opaquePaths = List.copyOf(sortedOpaquePaths);
        final ImmutableFingerprint expected =
                ContractCanonicalizer.policy(
                        policyVersion, baselineContract, allowances, opaquePaths);
        fingerprint =
                Objects.requireNonNull(fingerprint, "O fingerprint da política é obrigatório.");
        if (!expected.equals(fingerprint)) {
            throw new IllegalArgumentException("O fingerprint da política é inconsistente.");
        }
    }

    public static ContractCompatibilityPolicy create(
            final String policyVersion,
            final ImmutableFingerprint baselineContract,
            final List<ContractAllowance> allowances) {
        return create(policyVersion, baselineContract, allowances, List.of());
    }

    public static ContractCompatibilityPolicy create(
            final String policyVersion,
            final ImmutableFingerprint baselineContract,
            final List<ContractAllowance> allowances,
            final List<ContractOpaquePath> opaquePaths) {
        validateCollectionLimits(allowances, opaquePaths);
        return new ContractCompatibilityPolicy(
                policyVersion,
                baselineContract,
                allowances,
                opaquePaths,
                ContractCanonicalizer.policy(
                        policyVersion, baselineContract, allowances, opaquePaths));
    }

    boolean allows(final ContractChange change) {
        return allowances.stream().anyMatch(allowance -> allowance.matches(change));
    }

    Optional<ContractResponse.Field> approvedFieldForPageObservation(
            final ContractChange.Kind kind, final ContractResponse.Field observed) {
        Objects.requireNonNull(kind, "A categoria da mudança é obrigatória.");
        Objects.requireNonNull(observed, "O field observado é obrigatório.");
        final String reportedPath =
                observed.scope() == ContractResponse.FieldScope.ENVELOPE
                        ? "/envelope" + observed.path()
                        : observed.path();
        return allowances.stream()
                .filter(allowance -> allowance.component() == ContractChange.Component.RESPONSE)
                .filter(allowance -> allowance.kind() == kind)
                .filter(
                        allowance ->
                                allowance.responseFieldScope().orElse(null) == observed.scope())
                .filter(allowance -> allowance.path().equals(reportedPath))
                .map(allowance -> allowance.approvedResponseField().orElseThrow())
                .filter(approved -> approvedShapeContainsPageObservation(approved, observed))
                .findFirst();
    }

    private static boolean approvedShapeContainsPageObservation(
            final ContractResponse.Field approved, final ContractResponse.Field observed) {
        if (approved.scope() != observed.scope()
                || !approved.path().equals(observed.path())
                || approved.presence() == ContractResponse.Presence.REQUIRED
                        && observed.presence() != ContractResponse.Presence.REQUIRED
                || observed.nullable() && !approved.nullable()) {
            return false;
        }
        if (observed.cardinality() == ContractResponse.Cardinality.UNOBSERVED) {
            return observed.nullable() && approved.nullable();
        }
        return (approved.cardinality() == observed.cardinality()
                        || approved.cardinality() == ContractResponse.Cardinality.MIXED)
                && approved.jsonTypes().containsAll(observed.jsonTypes());
    }

    private static void validateCollectionLimits(
            final List<ContractAllowance> allowances, final List<ContractOpaquePath> opaquePaths) {
        Objects.requireNonNull(allowances, "As permissões de contrato são obrigatórias.");
        Objects.requireNonNull(opaquePaths, "Os paths opacos são obrigatórios.");
        if (allowances.size() > MAXIMUM_ALLOWANCES) {
            throw new IllegalArgumentException("A política excede o limite de permissões.");
        }
        if (opaquePaths.size() > MAXIMUM_OPAQUE_PATHS) {
            throw new IllegalArgumentException("A política excede o limite de paths opacos.");
        }
    }

    @Override
    public String toString() {
        return "ContractCompatibilityPolicy[policyVersion="
                + policyVersion
                + ", allowanceCount="
                + allowances.size()
                + ", opaquePathCount="
                + opaquePaths.size()
                + "]";
    }
}
