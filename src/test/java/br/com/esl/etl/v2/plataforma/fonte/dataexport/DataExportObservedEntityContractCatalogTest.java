package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DataExportObservedEntityContractCatalogTest {
    @Test
    void retainsRootFieldCountsWhileDescribingObservedNestedArrays() {
        assertFields(DataExportTemplate.MANIFESTOS, 91, 95);
        assertFields(DataExportTemplate.COTACOES, 37, 37);
        assertFields(DataExportTemplate.LOCALIZACAO_CARGAS, 26, 27);
        final var manifests =
                DataExportObservedEntityContractCatalog.release(DataExportTemplate.MANIFESTOS);
        for (final String path :
                List.of("/mft_mte_delivery_region_names", "/mft_mte_unloading_recipient_names")) {
            assertEquals(
                    List.of(ContractResponse.JsonType.ARRAY),
                    manifests.response().find(path + "/*").orElseThrow().jsonTypes());
            assertEquals(
                    List.of(ContractResponse.JsonType.STRING),
                    manifests.response().find(path + "/*/*").orElseThrow().jsonTypes());
            assertTrue(manifests.response().find(path + "/*/unknown").isEmpty());
        }
        final var localization =
                DataExportObservedEntityContractCatalog.release(
                        DataExportTemplate.LOCALIZACAO_CARGAS);
        assertEquals(
                List.of(ContractResponse.JsonType.STRING),
                localization.response().find("/invoices_mapping/*").orElseThrow().jsonTypes());
        assertTrue(localization.response().find("/invoices_mapping/*/*").isEmpty());
    }

    @Test
    void rejectsResourceSubstitutionAndWrongExplicitSubjectVersion() throws Exception {
        for (final var pin : DataExportObservedEntityPins.ALL) {
            final byte[] bytes;
            try (var input =
                    DataExportObservedEntityContractCatalog.class.getResourceAsStream(
                            pin.resource())) {
                if (input == null) {
                    throw new AssertionError("Resource missing");
                }
                bytes = input.readAllBytes();
            }
            assertEquals(
                    pin.releaseSha256(),
                    DataExportObservedEntityContractCatalog.read(
                                    pin.template(), pin.version(), new ByteArrayInputStream(bytes))
                            .contractFingerprint()
                            .sha256());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            DataExportObservedEntityContractCatalog.read(
                                    pin.template(),
                                    "not-an-observed-version",
                                    new ByteArrayInputStream(bytes)));
            final byte[] changed = bytes.clone();
            changed[changed.length / 2] ^= 1;
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            DataExportObservedEntityContractCatalog.read(
                                    pin.template(),
                                    pin.version(),
                                    new ByteArrayInputStream(changed)));
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            DataExportObservedEntityContractCatalog.read(
                                    pin.template(), pin.version(), null));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> DataExportObservedEntityContractCatalog.release(DataExportTemplate.COLETAS));
        assertThrows(
                IllegalArgumentException.class,
                () -> DataExportObservedEntityContractCatalog.release(null));
    }

    @Test
    void leavesUnobservedValueTypesAndLegacyRuntimeDefaultsClosed() {
        for (final var pin : DataExportObservedEntityPins.ALL) {
            final var release = DataExportObservedEntityContractCatalog.release(pin.template());
            assertSame(release, DataExportObservedEntityContractCatalog.release(pin.template()));
            assertFalse(release.contractVersion().equals(pin.template().contractVersion()));
            assertEquals("bloco55-synthetic-v1", pin.template().contractVersion());
            assertEquals(100, pin.template().defaultPageSize());
            assertEquals(DataExportTransport.GET_WITH_QUERY, pin.template().approvedTransport());
            assertFalse(pin.template().approvesRequestSemantics(50, List.of()));
            assertEquals(
                    List.of(ContractResponse.JsonType.INTEGER, ContractResponse.JsonType.NUMBER),
                    release.response()
                            .find(release.response().keyPath())
                            .orElseThrow()
                            .jsonTypes());
            assertTrue(
                    release.metadata().elements().stream()
                            .filter(
                                    field ->
                                            field.kind() == ContractMetadata.ElementKind.DATA_FIELD)
                            .allMatch(
                                    field ->
                                            field.declaredType()
                                                            == ContractMetadata.DeclaredType
                                                                    .UNDECLARED
                                                    && field.declaredTypeFingerprint().isEmpty()));
        }
        final var manifests =
                DataExportObservedEntityContractCatalog.release(DataExportTemplate.MANIFESTOS);
        assertTrue(manifests.response().find("/mft_iks_id").orElseThrow().jsonTypes().isEmpty());
        final var quotes =
                DataExportObservedEntityContractCatalog.release(DataExportTemplate.COTACOES);
        assertTrue(
                quotes.response()
                        .find("/qoe_qes_fit_nse_issued_at")
                        .orElseThrow()
                        .jsonTypes()
                        .isEmpty());
    }

    private static void assertFields(
            final DataExportTemplate template, final int root, final int total) {
        final var release = DataExportObservedEntityContractCatalog.release(template);
        assertEquals(total, release.response().fields().size());
        assertEquals(
                root,
                release.response().fields().stream()
                        .filter(field -> field.path().indexOf('/', 1) < 0)
                        .count());
        assertEquals("$", release.response().recordRoot());
        assertEquals(ContractResponse.Cardinality.ARRAY, release.response().rootCardinality());
    }
}
