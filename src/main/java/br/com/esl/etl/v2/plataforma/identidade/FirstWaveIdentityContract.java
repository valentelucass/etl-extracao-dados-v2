package br.com.esl.etl.v2.plataforma.identidade;

import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Matriz imutável de identidade de uma entidade da primeira onda. */
public record FirstWaveIdentityContract(
        Entity entity,
        String sourceContractId,
        SourceContractRelease sourceContract,
        SourceKeyDefinition sourceKey,
        BusinessAliasDefinition businessAlias,
        RootCardinalityPolicy rootCardinality,
        ImmutableFingerprint fingerprint) {

    public static final String VERSION = "first-wave-identity-v1";
    public static final String LOGICAL_SOURCE_FAMILY = "ESL";
    public static final String REGISTRY_TUPLE = "source_instance|tenant_scope|entity|source_key";

    private static final Pattern CONTRACT_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,127}");

    public FirstWaveIdentityContract {
        entity = Objects.requireNonNull(entity, "A entidade de identidade é obrigatória.");
        if (sourceContractId == null || !CONTRACT_ID.matcher(sourceContractId).matches()) {
            throw new IllegalArgumentException("O ID do contrato de origem é inválido.");
        }
        sourceContract =
                Objects.requireNonNull(sourceContract, "O contrato de origem é obrigatório.");
        sourceKey = Objects.requireNonNull(sourceKey, "A source key é obrigatória.");
        businessAlias = Objects.requireNonNull(businessAlias, "O alias de negócio é obrigatório.");
        rootCardinality =
                Objects.requireNonNull(rootCardinality, "A cardinalidade da raiz é obrigatória.");
        validateSourceKey(sourceContract, sourceKey);
        validateBusinessAlias(sourceContract, sourceKey, businessAlias);
        final ImmutableFingerprint expected =
                fingerprint(
                        entity,
                        sourceContractId,
                        sourceContract,
                        sourceKey,
                        businessAlias,
                        rootCardinality);
        if (!expected.equals(Objects.requireNonNull(fingerprint, "O fingerprint é obrigatório."))) {
            throw new IllegalArgumentException("O fingerprint da matriz de identidade diverge.");
        }
    }

    public static FirstWaveIdentityContract create(
            final Entity entity,
            final String sourceContractId,
            final SourceContractRelease sourceContract,
            final SourceKeyDefinition sourceKey,
            final BusinessAliasDefinition businessAlias,
            final RootCardinalityPolicy rootCardinality) {
        return new FirstWaveIdentityContract(
                entity,
                sourceContractId,
                sourceContract,
                sourceKey,
                businessAlias,
                rootCardinality,
                fingerprint(
                        entity,
                        sourceContractId,
                        sourceContract,
                        sourceKey,
                        businessAlias,
                        rootCardinality));
    }

    public CanonicalIdStrategy canonicalIdStrategy() {
        return CanonicalIdStrategy.SQL_SURROGATE_BIGINT_IDENTITY;
    }

    public ScopePolicy scopePolicy() {
        return ScopePolicy.EXPLICIT_SOURCE_INSTANCE_AND_TENANT_REQUIRED;
    }

    public EvidenceScope evidenceScope() {
        return EvidenceScope.CLOSED_HISTORICAL_WINDOWS_AND_SYNTHETIC_FIXTURES;
    }

    @Override
    public String toString() {
        return "FirstWaveIdentityContract[entity="
                + entity
                + ", sourceContractId="
                + sourceContractId
                + ", fingerprintVersion="
                + fingerprint.version()
                + "]";
    }

    private static void validateSourceKey(
            final SourceContractRelease release, final SourceKeyDefinition sourceKey) {
        if (!release.response().keyPath().equals(sourceKey.path())) {
            throw new IllegalArgumentException(
                    "A source key não coincide com a chave do contrato de origem.");
        }
        final ContractResponse.Field field =
                release.response()
                        .find(sourceKey.path())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "A source key não existe no contrato de origem."));
        if (field.cardinality() != ContractResponse.Cardinality.SCALAR
                || field.presence() != ContractResponse.Presence.REQUIRED
                || field.nullable()
                || !sourceKey.wireTypes().accepts(field.jsonTypes())) {
            throw new IllegalArgumentException(
                    "A source key diverge do tipo obrigatório do contrato de origem.");
        }
    }

    private static void validateBusinessAlias(
            final SourceContractRelease release,
            final SourceKeyDefinition sourceKey,
            final BusinessAliasDefinition alias) {
        if (alias.policy() == BusinessAliasPolicy.ABSENT) {
            if (alias.path().isPresent() || alias.name().isPresent()) {
                throw new IllegalArgumentException(
                        "Um alias ausente não pode declarar path ou nome.");
            }
            return;
        }
        final String path = alias.path().orElseThrow();
        if (path.equals(sourceKey.path()) || alias.name().orElseThrow().equals(sourceKey.name())) {
            throw new IllegalArgumentException("Business alias não pode substituir a source key.");
        }
        final ContractResponse.Field field =
                release.response()
                        .find(path)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "O business alias não existe no contrato de origem."));
        if (field.cardinality() != ContractResponse.Cardinality.SCALAR
                || field.presence() != ContractResponse.Presence.REQUIRED
                || field.nullable()
                || !field.jsonTypes().equals(List.of(ContractResponse.JsonType.INTEGER))) {
            throw new IllegalArgumentException(
                    "O business alias diverge do contrato estrutural aprovado.");
        }
    }

    private static ImmutableFingerprint fingerprint(
            final Entity entity,
            final String sourceContractId,
            final SourceContractRelease sourceContract,
            final SourceKeyDefinition sourceKey,
            final BusinessAliasDefinition businessAlias,
            final RootCardinalityPolicy rootCardinality) {
        final FingerprintEncoder encoder = new FingerprintEncoder();
        encoder.write(VERSION);
        encoder.write(LOGICAL_SOURCE_FAMILY);
        encoder.write(REGISTRY_TUPLE);
        encoder.write(entity.name());
        encoder.write(entity.entityName());
        encoder.write(sourceContractId);
        encoder.write(sourceContract.sourceKind().name());
        encoder.write(sourceContract.documentReference());
        encoder.write(sourceContract.contractVersion());
        encoder.write(sourceContract.contractFingerprint().version());
        encoder.write(sourceContract.contractFingerprint().sha256());
        encoder.write(sourceKey.path());
        encoder.write(sourceKey.name());
        encoder.write(sourceKey.wireTypes().name());
        encoder.write(businessAlias.policy().name());
        encoder.write(businessAlias.path().orElse("ABSENT"));
        encoder.write(businessAlias.name().orElse("ABSENT"));
        encoder.write(businessAlias.cardinality().name());
        encoder.write(rootCardinality.name());
        encoder.write(CanonicalIdStrategy.SQL_SURROGATE_BIGINT_IDENTITY.name());
        encoder.write(ScopePolicy.EXPLICIT_SOURCE_INSTANCE_AND_TENANT_REQUIRED.name());
        encoder.write(EvidenceScope.CLOSED_HISTORICAL_WINDOWS_AND_SYNTHETIC_FIXTURES.name());
        return encoder.finish();
    }

    /** Entidades fechadas deste subbloco; o casing é parte do namespace BIN2 futuro. */
    public enum Entity {
        COLETAS("coletas"),
        FRETES("fretes"),
        USUARIOS("usuarios");

        private final String entityName;

        Entity(final String entityName) {
            this.entityName = entityName;
        }

        public String entityName() {
            return entityName;
        }
    }

    /** Definição da chave técnica observada na origem. */
    public record SourceKeyDefinition(String path, String name, WireTypePolicy wireTypes) {

        private static final Pattern TECHNICAL_NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");

        public SourceKeyDefinition {
            path = ContractResponse.requireKeyPath(path);
            if (name == null || !TECHNICAL_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("O nome da source key é inválido.");
            }
            wireTypes =
                    Objects.requireNonNull(wireTypes, "Os tipos da source key são obrigatórios.");
        }
    }

    /** Alias de negócio independente, sempre versionado quando existir. */
    public record BusinessAliasDefinition(
            Optional<String> path,
            Optional<String> name,
            BusinessAliasPolicy policy,
            AliasCardinalityPolicy cardinality) {

        private static final Pattern TECHNICAL_NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");

        public BusinessAliasDefinition {
            path = Objects.requireNonNull(path, "O path opcional do alias é obrigatório.");
            name = Objects.requireNonNull(name, "O nome opcional do alias é obrigatório.");
            policy = Objects.requireNonNull(policy, "A política de alias é obrigatória.");
            cardinality =
                    Objects.requireNonNull(cardinality, "A cardinalidade do alias é obrigatória.");
            path = path.map(ContractResponse::requireKeyPath);
            if (name.isPresent() && !TECHNICAL_NAME.matcher(name.orElseThrow()).matches()) {
                throw new IllegalArgumentException("O nome do business alias é inválido.");
            }
            if (path.isPresent() != name.isPresent()) {
                throw new IllegalArgumentException("Path e nome do alias devem coexistir.");
            }
            if ((policy == BusinessAliasPolicy.ABSENT)
                    != (cardinality == AliasCardinalityPolicy.NOT_APPLICABLE)) {
                throw new IllegalArgumentException("Política e cardinalidade de alias divergem.");
            }
        }

        public static BusinessAliasDefinition versioned(
                final String path, final String name, final AliasCardinalityPolicy cardinality) {
            return new BusinessAliasDefinition(
                    Optional.of(path),
                    Optional.of(name),
                    BusinessAliasPolicy.VERSIONED_NON_TECHNICAL,
                    cardinality);
        }

        public static BusinessAliasDefinition absent() {
            return new BusinessAliasDefinition(
                    Optional.empty(),
                    Optional.empty(),
                    BusinessAliasPolicy.ABSENT,
                    AliasCardinalityPolicy.NOT_APPLICABLE);
        }
    }

    public enum WireTypePolicy {
        INTEGER_ONLY,
        STRING_OR_INTEGER_TYPE_TAGGED_DISTINCT;

        boolean accepts(final List<ContractResponse.JsonType> actual) {
            return switch (this) {
                case INTEGER_ONLY -> actual.equals(List.of(ContractResponse.JsonType.INTEGER));
                case STRING_OR_INTEGER_TYPE_TAGGED_DISTINCT ->
                        actual.equals(
                                List.of(
                                        ContractResponse.JsonType.STRING,
                                        ContractResponse.JsonType.INTEGER));
            };
        }

        public boolean permitsInteger() {
            return true;
        }

        public boolean permitsString() {
            return this == STRING_OR_INTEGER_TYPE_TAGGED_DISTINCT;
        }
    }

    public enum BusinessAliasPolicy {
        VERSIONED_NON_TECHNICAL,
        ABSENT
    }

    public enum AliasCardinalityPolicy {
        OBSERVED_ONE_TO_ONE_NOT_GLOBAL,
        ZERO_TO_MANY_LOOKUP_AMBIGUITY_BLOCKS_RESOLUTION,
        NOT_APPLICABLE
    }

    public enum RootCardinalityPolicy {
        LOGICAL_ROOT_MAY_EXPAND_TO_REPEATED_PHYSICAL_ROWS,
        ONE_NODE_PER_OBSERVED_EDGE
    }

    public enum CanonicalIdStrategy {
        SQL_SURROGATE_BIGINT_IDENTITY
    }

    public enum ScopePolicy {
        EXPLICIT_SOURCE_INSTANCE_AND_TENANT_REQUIRED
    }

    public enum EvidenceScope {
        CLOSED_HISTORICAL_WINDOWS_AND_SYNTHETIC_FIXTURES
    }

    private static final class FingerprintEncoder {

        private final MessageDigest digest;

        private FingerprintEncoder() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (final NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 não está disponível.", exception);
            }
        }

        private void write(final String value) {
            final byte[] encoded =
                    Objects.requireNonNull(value, "O elemento canônico é obrigatório.")
                            .getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.length).array());
            digest.update(encoded);
        }

        private ImmutableFingerprint finish() {
            return new ImmutableFingerprint(VERSION, HexFormat.of().formatHex(digest.digest()));
        }
    }
}
