package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.devlocal.DevLocalDataset;
import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;
import com.fasterxml.jackson.databind.JsonNode;

/** Describes Fontes' declared sample; it never invents bindings for another dataset. */
final class DevLocalEngineSample {
    private DevLocalEngineSample() {}

    static JsonNode contract() {
        final JsonNode dataset = DevLocalDataset.characterize();
        final var contract = DevLocalEngine.JSON.createObjectNode();
        contract.put("synthetic", true);
        contract.put("version", "DEV_LOCAL_RELATION_SAMPLE_V1");
        contract.put("identityScope", DevLocalEngine.SCOPE);
        contract.put("datasetSha256", DevLocalLedger.sha256(DevLocalEngine.bytes(dataset)));
        contract.set("bindings", dataset.path("relationBindings"));
        contract.put("authority", "FONTES_EXPLICIT_LITERAL_SAMPLE_NOT_SUPPLIER_IDENTITY");
        return contract;
    }

    static JsonNode forDataset(final JsonNode dataset) {
        return DevLocalEngine.JSON.createArrayNode();
    }
}
