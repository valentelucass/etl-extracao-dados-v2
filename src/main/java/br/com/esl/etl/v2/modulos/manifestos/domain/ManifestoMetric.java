package br.com.esl.etl.v2.modulos.manifestos.domain;

/** Os oito sinais lógicos de MAN-07; capacidade possui uma única fonte física. */
public enum ManifestoMetric {
    KM("km"),
    TOTAL_COST("total_cost"),
    MANIFEST_FREIGHTS_TOTAL("manifest_freights_total"),
    TOTAL_TAXED_WEIGHT("total_taxed_weight"),
    VEHICLE_WEIGHT_CAPACITY("mft_vie_weight_capacity"),
    MANIFEST_ITEMS_COUNT("manifest_items_count"),
    FINALIZED_MANIFEST_ITEMS_COUNT("finalized_manifest_items_count");

    private final String sourceField;

    ManifestoMetric(final String sourceField) {
        this.sourceField = sourceField;
    }

    public String sourceField() {
        return sourceField;
    }

    /** Alias lógico, não uma segunda leitura ou um segundo reducer. */
    public boolean projectsCapacidadeKg() {
        return this == VEHICLE_WEIGHT_CAPACITY;
    }
}
