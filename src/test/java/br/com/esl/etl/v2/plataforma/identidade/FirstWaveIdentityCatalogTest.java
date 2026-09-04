package br.com.esl.etl.v2.plataforma.identidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FirstWaveIdentityCatalogTest {

    private static final Path MANIFEST =
            Path.of("docs/catalogos/identidade-primeira-onda/manifesto.json");
    private static final long MAXIMUM_MANIFEST_BYTES = 512L * 1024L;
    private static final ObjectMapper MAPPER =
            new ObjectMapper(
                    JsonFactory.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .build());

    @Test
    void catalogBindsTheThreeExactV2025aReleasesWithoutPromotingBusinessKeys() {
        final FirstWaveIdentityContract coletas =
                FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.COLETAS);
        final FirstWaveIdentityContract fretes =
                FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.FRETES);
        final FirstWaveIdentityContract usuarios =
                FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.USUARIOS);

        assertContract(
                coletas,
                "dataexport-6908",
                DataExportFirstWaveContractCatalog.release(DataExportTemplate.COLETAS),
                "/id",
                "id");
        assertEquals(Optional.of("/sequence_code"), coletas.businessAlias().path());
        assertEquals(Optional.of("sequence_code"), coletas.businessAlias().name());
        assertEquals(
                FirstWaveIdentityContract.AliasCardinalityPolicy.OBSERVED_ONE_TO_ONE_NOT_GLOBAL,
                coletas.businessAlias().cardinality());

        assertContract(
                fretes,
                "dataexport-6389",
                DataExportFirstWaveContractCatalog.release(DataExportTemplate.FRETES),
                "/id",
                "id");
        assertEquals(Optional.of("/corporation_sequence_number"), fretes.businessAlias().path());
        assertEquals(
                FirstWaveIdentityContract.AliasCardinalityPolicy
                        .ZERO_TO_MANY_LOOKUP_AMBIGUITY_BLOCKS_RESOLUTION,
                fretes.businessAlias().cardinality());

        assertContract(
                usuarios,
                "graphql-individual",
                GraphQlFirstWaveContractCatalog.release(GraphQlReadOperation.USERS_SNAPSHOT),
                "/node/id",
                "user_id");
        assertEquals(
                FirstWaveIdentityContract.BusinessAliasPolicy.ABSENT,
                usuarios.businessAlias().policy());
        assertEquals(Optional.empty(), usuarios.businessAlias().path());
        assertEquals(Optional.empty(), usuarios.businessAlias().name());
        assertFalse(usuarios.sourceKey().wireTypes().equals(coletas.sourceKey().wireTypes()));
        assertTrue(usuarios.sourceKey().wireTypes().permitsString());
        assertTrue(usuarios.sourceKey().wireTypes().permitsInteger());
        assertFalse(coletas.sourceKey().wireTypes().permitsString());
        assertTrue(coletas.sourceKey().wireTypes().permitsInteger());
        assertNotEquals(coletas.fingerprint(), fretes.fingerprint());
        assertNotEquals(fretes.fingerprint(), usuarios.fingerprint());
    }

    @Test
    void namespaceCanonicalAndEvidencePoliciesAreExplicitAndUniform() {
        for (final FirstWaveIdentityContract.Entity entity :
                FirstWaveIdentityContract.Entity.values()) {
            final FirstWaveIdentityContract contract = FirstWaveIdentityCatalog.contract(entity);
            assertEquals(
                    FirstWaveIdentityContract.CanonicalIdStrategy.SQL_SURROGATE_BIGINT_IDENTITY,
                    contract.canonicalIdStrategy());
            assertEquals(
                    FirstWaveIdentityContract.ScopePolicy
                            .EXPLICIT_SOURCE_INSTANCE_AND_TENANT_REQUIRED,
                    contract.scopePolicy());
            assertEquals(
                    FirstWaveIdentityContract.EvidenceScope
                            .CLOSED_HISTORICAL_WINDOWS_AND_SYNTHETIC_FIXTURES,
                    contract.evidenceScope());
            assertEquals(FirstWaveIdentityContract.VERSION, contract.fingerprint().version());
            assertEquals(64, contract.fingerprint().sha256().length());
            assertFalse(contract.toString().contains(contract.fingerprint().sha256()));
        }
        assertEquals("ESL", FirstWaveIdentityContract.LOGICAL_SOURCE_FAMILY);
        assertEquals(
                "source_instance|tenant_scope|entity|source_key",
                FirstWaveIdentityContract.REGISTRY_TUPLE);
        assertEquals("coletas", FirstWaveIdentityContract.Entity.COLETAS.entityName());
        assertEquals("fretes", FirstWaveIdentityContract.Entity.FRETES.entityName());
        assertEquals("usuarios", FirstWaveIdentityContract.Entity.USUARIOS.entityName());
        assertThrows(NullPointerException.class, () -> FirstWaveIdentityCatalog.contract(null));
    }

    @Test
    void versionedManifestMatchesTheExecutableCatalogAndV2025aFingerprints() throws IOException {
        assertTrue(Files.size(MANIFEST) <= MAXIMUM_MANIFEST_BYTES);
        final JsonNode manifest = MAPPER.readTree(Files.readAllBytes(MANIFEST));
        assertEquals(3, manifest.path("entities").size());
        for (final FirstWaveIdentityContract.Entity entity :
                FirstWaveIdentityContract.Entity.values()) {
            final FirstWaveIdentityContract contract = FirstWaveIdentityCatalog.contract(entity);
            final JsonNode matrix = matrix(manifest, entity.entityName());
            assertEquals(contract.sourceContractId(), matrix.path("contractId").asText());
            assertEquals(
                    contract.sourceContract().sourceKind().name(),
                    matrix.path("sourceKind").asText());
            assertEquals(
                    contract.sourceContract().documentReference(),
                    matrix.path("documentReference").asText());
            assertEquals(
                    contract.sourceContract().contractVersion(),
                    matrix.path("contractVersion").asText());
            assertEquals(
                    contract.sourceContract().contractFingerprint().sha256(),
                    matrix.path("sourceContractFingerprint").asText());
            assertEquals(
                    contract.fingerprint().sha256(), matrix.path("identityFingerprint").asText());
            assertEquals(contract.sourceKey().path(), matrix.at("/sourceKey/path").asText());
            assertEquals(contract.sourceKey().name(), matrix.at("/sourceKey/name").asText());
        }
    }

    @Test
    void constructorRejectsDriftFromTheStructuralSourceContract() {
        final SourceContractRelease coletas =
                DataExportFirstWaveContractCatalog.release(DataExportTemplate.COLETAS);
        final FirstWaveIdentityContract valid =
                FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.COLETAS);
        final ImmutableFingerprint wrong =
                new ImmutableFingerprint(FirstWaveIdentityContract.VERSION, "0".repeat(64));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FirstWaveIdentityContract(
                                valid.entity(),
                                valid.sourceContractId(),
                                valid.sourceContract(),
                                valid.sourceKey(),
                                valid.businessAlias(),
                                valid.rootCardinality(),
                                wrong));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FirstWaveIdentityContract.create(
                                FirstWaveIdentityContract.Entity.COLETAS,
                                "INVALID",
                                coletas,
                                valid.sourceKey(),
                                valid.businessAlias(),
                                valid.rootCardinality()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FirstWaveIdentityContract.create(
                                FirstWaveIdentityContract.Entity.COLETAS,
                                "dataexport-6908",
                                coletas,
                                new FirstWaveIdentityContract.SourceKeyDefinition(
                                        "/sequence_code",
                                        "id",
                                        FirstWaveIdentityContract.WireTypePolicy.INTEGER_ONLY),
                                valid.businessAlias(),
                                valid.rootCardinality()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FirstWaveIdentityContract.SourceKeyDefinition(
                                "/id",
                                "ID inválido",
                                FirstWaveIdentityContract.WireTypePolicy.INTEGER_ONLY));
        assertThrows(
                NullPointerException.class,
                () -> new FirstWaveIdentityContract.SourceKeyDefinition("/id", "id", null));
    }

    @Test
    void aliasDefinitionRejectsPartialOrTechnicalIdentityAliases() {
        final FirstWaveIdentityContract coletas =
                FirstWaveIdentityCatalog.contract(FirstWaveIdentityContract.Entity.COLETAS);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FirstWaveIdentityContract.BusinessAliasDefinition(
                                Optional.of("/sequence_code"),
                                Optional.empty(),
                                FirstWaveIdentityContract.BusinessAliasPolicy
                                        .VERSIONED_NON_TECHNICAL,
                                FirstWaveIdentityContract.AliasCardinalityPolicy
                                        .OBSERVED_ONE_TO_ONE_NOT_GLOBAL));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FirstWaveIdentityContract.BusinessAliasDefinition(
                                Optional.empty(),
                                Optional.empty(),
                                FirstWaveIdentityContract.BusinessAliasPolicy.ABSENT,
                                FirstWaveIdentityContract.AliasCardinalityPolicy
                                        .OBSERVED_ONE_TO_ONE_NOT_GLOBAL));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FirstWaveIdentityContract.BusinessAliasDefinition(
                                Optional.of("/sequence_code"),
                                Optional.of("INVALID"),
                                FirstWaveIdentityContract.BusinessAliasPolicy
                                        .VERSIONED_NON_TECHNICAL,
                                FirstWaveIdentityContract.AliasCardinalityPolicy
                                        .OBSERVED_ONE_TO_ONE_NOT_GLOBAL));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FirstWaveIdentityContract.create(
                                coletas.entity(),
                                coletas.sourceContractId(),
                                coletas.sourceContract(),
                                coletas.sourceKey(),
                                FirstWaveIdentityContract.BusinessAliasDefinition.versioned(
                                        "/id",
                                        "id_alias",
                                        FirstWaveIdentityContract.AliasCardinalityPolicy
                                                .OBSERVED_ONE_TO_ONE_NOT_GLOBAL),
                                coletas.rootCardinality()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FirstWaveIdentityContract.create(
                                coletas.entity(),
                                coletas.sourceContractId(),
                                coletas.sourceContract(),
                                coletas.sourceKey(),
                                FirstWaveIdentityContract.BusinessAliasDefinition.versioned(
                                        "/sequence_code",
                                        "id",
                                        FirstWaveIdentityContract.AliasCardinalityPolicy
                                                .OBSERVED_ONE_TO_ONE_NOT_GLOBAL),
                                coletas.rootCardinality()));
    }

    private static void assertContract(
            final FirstWaveIdentityContract actual,
            final String contractId,
            final SourceContractRelease release,
            final String sourceKeyPath,
            final String sourceKeyName) {
        assertEquals(contractId, actual.sourceContractId());
        assertEquals(release, actual.sourceContract());
        assertEquals(sourceKeyPath, actual.sourceKey().path());
        assertEquals(sourceKeyName, actual.sourceKey().name());
        final ContractResponse.Field sourceKey =
                release.response().find(sourceKeyPath).orElseThrow();
        assertEquals(ContractResponse.Cardinality.SCALAR, sourceKey.cardinality());
        assertEquals(ContractResponse.Presence.REQUIRED, sourceKey.presence());
        assertFalse(sourceKey.nullable());
        assertTrue(
                FirstWaveIdentityContract.BusinessAliasPolicy.VERSIONED_NON_TECHNICAL
                                == actual.businessAlias().policy()
                        || FirstWaveIdentityContract.BusinessAliasPolicy.ABSENT
                                == actual.businessAlias().policy());
    }

    private static JsonNode matrix(final JsonNode manifest, final String entity) {
        for (final JsonNode matrix : manifest.path("entities")) {
            if (entity.equals(matrix.path("entity").asText())) {
                return matrix;
            }
        }
        throw new AssertionError("Matriz de identidade ausente: " + entity);
    }
}
