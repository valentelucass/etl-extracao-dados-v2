package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractValidator;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DataExportObservedFretesContractCatalogTest {
    @Test
    void packagesExactlyTheIndependentlyObservedStructureWithoutReplacingTheLegacyCatalog()
            throws Exception {
        final var release = DataExportObservedFretesContractCatalog.release();
        try (var input =
                getClass()
                        .getResourceAsStream(
                                "/contracts/dataexport/fretes-6389-observed-20261024.json")) {
            assertEquals(
                    Files.readString(
                            Path.of(
                                    "docs/catalogos/contratos-primeira-onda/observados/"
                                            + "fretes-6389-20261024/release.json")),
                    new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        assertSame(release, DataExportObservedFretesContractCatalog.release());
        assertEquals("$", release.response().recordRoot());
        assertEquals("/id", release.response().keyPath());
        assertEquals(130, release.metadata().elements().size());
        assertEquals(
                114,
                release.metadata().elements().stream()
                        .filter(
                                element ->
                                        element.kind() == ContractMetadata.ElementKind.DATA_FIELD)
                        .count());
        assertEquals(
                16,
                release.metadata().elements().stream()
                        .filter(
                                element ->
                                        element.kind() == ContractMetadata.ElementKind.DATA_FILTER)
                        .count());
        assertEquals(115, release.response().fields().size());
        assertEquals(
                15,
                release.response().fields().stream()
                        .filter(
                                field ->
                                        field.cardinality()
                                                == ContractResponse.Cardinality.UNOBSERVED)
                        .count());
        assertTrue(
                release.metadata().elements().stream()
                        .filter(
                                element ->
                                        element.kind() == ContractMetadata.ElementKind.DATA_FIELD)
                        .allMatch(
                                element ->
                                        element.declaredType()
                                                == ContractMetadata.DeclaredType.UNDECLARED));
        assertTrue(release.response().find("/service_at").isPresent());
        assertFalse(release.response().find("/servico_em").isPresent());
        assertNotEquals(
                DataExportFirstWaveContractCatalog.release(DataExportTemplate.FRETES)
                        .contractFingerprint(),
                release.contractFingerprint());
    }

    @Test
    void doesNotInferAnUnobservedNonNullTypeOrIgnoreMetadataDrift() {
        final var release = DataExportObservedFretesContractCatalog.release();
        final var policy =
                ContractCompatibilityPolicy.create(
                        "observed-strict-v1", release.contractFingerprint(), List.of());
        final var validator = new ContractValidator(release, policy);
        validator.validateMetadata(release.metadata());
        validator.validateResponse(release.response());
        final var fields = new ArrayList<>(release.response().fields());
        final var unknown = release.response().find("/fit_fis_see_name").orElseThrow();
        assertTrue(unknown.nullable());
        assertTrue(unknown.jsonTypes().isEmpty());
        fields.remove(unknown);
        fields.add(
                new ContractResponse.Field(
                        unknown.path(),
                        ContractResponse.Cardinality.SCALAR,
                        unknown.presence(),
                        true,
                        List.of(ContractResponse.JsonType.STRING)));
        final var changed =
                new ContractResponse(
                        release.response().recordRoot(),
                        release.response().rootCardinality(),
                        release.response().observationState(),
                        release.response().keyPath(),
                        fields);
        assertThrows(ContractDriftException.class, () -> validator.validateResponse(changed));
        assertThrows(
                ContractDriftException.class,
                () ->
                        validator.validateMetadata(
                                DataExportFirstWaveContractCatalog.release(
                                                DataExportTemplate.FRETES)
                                        .metadata()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 65_536, 65_537})
    void rejectsMissingOrChangedPackagedBytesBeforeParsing(final int size) {
        final var bytes = new ByteArrayInputStream(new byte[size]);
        assertThrows(
                IllegalStateException.class,
                () -> DataExportObservedFretesContractCatalog.read(bytes));
    }

    @Test
    void rejectsAnAbsentPackagedResource() {
        assertThrows(
                IllegalStateException.class,
                () -> DataExportObservedFretesContractCatalog.read(null));
    }
}
