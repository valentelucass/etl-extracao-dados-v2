package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.graphql.AnalyticCollectionSupplementMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryCollectionContractTest {
    @Test
    void collectionProfileIsExplicitAndCannotBecomeAnotherEntityOrTheHistoricalDefault() {
        final var source =
                new RelationalSyntheticSource(page -> "[]").withAnalyticCollectionDetails();
        final var release = source.contractRelease(DataExportTemplate.COLETAS);
        assertNotEquals(
                RelationalSyntheticSource.release(DataExportTemplate.COLETAS).contractFingerprint(),
                release.contractFingerprint());
        assertEquals(
                RelationalSyntheticSource.analyticContracts().coletas(),
                release.contractFingerprint().sha256());
        assertThrows(
                IllegalArgumentException.class,
                () -> source.contractRelease(DataExportTemplate.FRETES));
        assertEquals(
                RelationalSyntheticSource.analyticManifestContracts().manifestos(),
                RelationalSyntheticSource.analyticContracts().manifestos());
        assertEquals(
                RelationalSyntheticSource.contracts().fretes(),
                RelationalSyntheticSource.analyticContracts().fretes());
    }

    @Test
    void lateralGraphQlAttributesPreserveNestedAbsenceNullUnicodeAndExactTime() throws Exception {
        final ObjectNode source;
        try (var input =
                getClass()
                        .getResourceAsStream(
                                "/analytic-laboratory/collection-supplement.synthetic.json")) {
            source = (ObjectNode) new ObjectMapper().readTree(input);
        }
        final var mapper = new AnalyticCollectionSupplementMapper();
        final var values = mapper.map(source);
        assertTrue(values.valid());
        assertEquals(12, values.fields().size());
        assertEquals("CLIENTE SINTÉTICO", values.customerName().value());
        assertEquals(123456789, values.requestHour().value().getNano());
        assertEquals(123456789, values.statusUpdatedAt().value().getNano());
        final var changed = source.deepCopy();
        final var data = (ObjectNode) changed.path("data");
        data.remove("customer");
        assertEquals(Presence.ABSENT, mapper.map(changed).customerName().presence());
        data.putNull("customer");
        assertEquals(Presence.NULL, mapper.map(changed).customerName().presence());
        data.put("requestHour", "25:61:00");
        assertFalse(mapper.map(changed).valid());
        data.put("pickAddress", 7);
        assertThrows(IllegalArgumentException.class, () -> mapper.map(changed));
    }
}
