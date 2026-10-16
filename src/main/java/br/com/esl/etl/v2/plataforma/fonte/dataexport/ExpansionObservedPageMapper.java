package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.modulos.contasapagar.aplicacao.ContaPagarDataExportMapper;
import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturaClienteDataExportMapper;
import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioDataExportMapper;
import br.com.esl.etl.v2.modulos.sinistros.aplicacao.SinistroDataExportMapper;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionCaptured;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.List;

/** Source observations carry no synthetic envelope provenance or invented binding. */
public final class ExpansionObservedPageMapper {
    public List<ExpansionCaptured<? extends ExpansionObservation>> map(
            final DataExportObservedPage page) {
        if (page == null
                || switch (page.template()) {
                    case CONTAS_A_PAGAR, FATURAS_POR_CLIENTE, INVENTARIO, SINISTROS -> false;
                    default -> true;
                }) {
            throw new IllegalArgumentException("OBS_EXPANSION_TEMPLATE_REQUIRED");
        }
        return mapRecords(page.template(), page.records());
    }

    public List<ExpansionCaptured<? extends ExpansionObservation>> mapPhysical(
            final DataExportObservedPage.PhysicalObservation page) {
        if (page == null
                || switch (page.template()) {
                    case CONTAS_A_PAGAR, FATURAS_POR_CLIENTE, INVENTARIO, SINISTROS -> false;
                    default -> true;
                }) {
            throw new IllegalArgumentException("OBS_EXPANSION_TEMPLATE_REQUIRED");
        }
        return mapRecords(page.template(), page.records());
    }

    private List<ExpansionCaptured<? extends ExpansionObservation>> mapRecords(
            final DataExportTemplate template, final List<JsonNode> records) {
        final var captured = new ArrayList<ExpansionCaptured<? extends ExpansionObservation>>();
        int occurrence = 0;
        for (final var record : records) {
            occurrence++;
            final var envelope = JsonNodeFactory.instance.objectNode().set("data", record);
            final ExpansionObservation observation =
                    switch (template) {
                        case CONTAS_A_PAGAR ->
                                new ContaPagarDataExportMapper().map(occurrence, envelope);
                        case FATURAS_POR_CLIENTE ->
                                new FaturaClienteDataExportMapper().map(occurrence, envelope);
                        case INVENTARIO ->
                                new InventarioDataExportMapper().map(occurrence, envelope);
                        case SINISTROS -> new SinistroDataExportMapper().map(occurrence, envelope);
                        default ->
                                throw new IllegalArgumentException(
                                        "OBS_EXPANSION_TEMPLATE_REQUIRED");
                    };
            captured.add(new ExpansionCaptured<>(occurrence, observation, null));
        }
        return List.copyOf(captured);
    }
}
