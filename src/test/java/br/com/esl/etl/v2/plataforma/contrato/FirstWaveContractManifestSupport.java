package br.com.esl.etl.v2.plataforma.contrato;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Asserções compartilhadas da baseline V2-025a; nunca lê rede, ambiente ou credencial. */
public final class FirstWaveContractManifestSupport {

    private static final Path MANIFEST =
            Path.of("docs/catalogos/contratos-primeira-onda/manifesto.json");
    private static final long MAXIMUM_BYTES = 2L * 1024L * 1024L;
    private static final ObjectMapper MAPPER =
            new ObjectMapper(
                    JsonFactory.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .build());

    private FirstWaveContractManifestSupport() {}

    public static JsonNode contract(final String contractId) throws IOException {
        if (Files.size(MANIFEST) > MAXIMUM_BYTES) {
            throw new IOException("O manifesto V2-025a excede o limite de bytes.");
        }
        final JsonNode root = MAPPER.readTree(Files.readAllBytes(MANIFEST));
        for (final JsonNode contract : root.path("contracts")) {
            if (contractId.equals(contract.path("contractId").asText())) {
                return contract;
            }
        }
        throw new IOException("Contrato V2-025a ausente: " + contractId);
    }

    public static void assertRelease(final JsonNode contract, final SourceContractRelease release) {
        assertEquals(contract.path("sourceKind").asText(), release.sourceKind().name());
        assertEquals(contract.path("documentReference").asText(), release.documentReference());
        assertEquals(contract.path("contractVersion").asText(), release.contractVersion());
        assertFingerprint(
                contract.path("fingerprints").path("metadata"), release.metadataFingerprint());
        assertFingerprint(
                contract.path("fingerprints").path("response"), release.responseFingerprint());
        assertFingerprint(
                contract.path("fingerprints").path("release"), release.contractFingerprint());
    }

    public static void assertSemantics(
            final JsonNode contract, final ImmutableFingerprint fingerprint) {
        assertEquals(
                "source-contract-semantics-v1",
                fingerprint.version(),
                "Versão inesperada do fingerprint semântico.");
        assertEquals(
                contract.path("fingerprints").path("semantics").asText(), fingerprint.sha256());
    }

    public static void assertAspect(
            final JsonNode contract,
            final String aspect,
            final ContractClassification classification) {
        assertEquals(
                classification.name(),
                contract.path("aspects").path(aspect).path("classification").asText());
    }

    public static void assertFailClosedCapabilities(final JsonNode contract) {
        assertEquals(
                "NOT_BLOCKED_BY_COMPLETENESS_CONTRACT_AND_ENTITY_GATES_REQUIRED",
                contract.path("capabilities").path("shadowUpsert").asText());
        assertEquals(
                SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF.name(),
                contract.path("capabilities").path("sweepOrDeactivation").asText());
        assertEquals(
                SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF.name(),
                contract.path("capabilities").path("cutover").asText());
    }

    private static void assertFingerprint(
            final JsonNode expectedHash, final ImmutableFingerprint fingerprint) {
        assertEquals(expectedHash.asText(), fingerprint.sha256());
    }
}
