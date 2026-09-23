package br.com.esl.etl.v2.modulos.manifestos.domain;

/**
 * Limites físicos legados, agora aplicados como rejeição sem truncamento.
 *
 * <p>O limite é contado em unidades UTF-16, a mesma unidade do {@code NVARCHAR} do SQL Server.
 */
public enum ManifestoTextField {
    STATUS("status", 50),
    MDFE_KEY("mft_mfs_key", 100),
    MDFE_STATUS("mdfe_status", 50),
    APE_NAME("mft_ape_name", 255),
    MAN_NAME("mft_man_name", 255),
    VEHICLE_LICENSE_PLATE("mft_vie_license_plate", 10),
    VEHICLE_NAME("mft_vie_vee_name", 255),
    VEHICLE_OWNER_NAME("mft_vie_onr_name", 255),
    DRIVER_NAME("mft_mdr_iil_name", 255),
    CARRIER_NICKNAME("mft_crn_psn_nickname", 255),
    CONTRACT_NUMBER("mft_cat_cot_number", 50),
    CONTRACT_TYPE("contract_type", 50),
    DRIVER_CONTRACT_TYPE("mft_mdr_contract_type", 50),
    CALCULATION_TYPE("calculation_type", 50),
    CARGO_TYPE("cargo_type", 255),
    USER_NAME("mft_uer_name", 255),
    RER_NAME("mft_aoe_rer_name", 255),
    AOE_COMMENTS("mft_aoe_comments", 4000),
    COT_STATUS("mft_cat_cot_status", 50),
    IKS_ID("mft_iks_id", 100),
    S_N_SEQUENCE_CODE("mft_s_n_sequence_code", 50),
    TRAILER_ONE_LICENSE_PLATE("mft_tl1_license_plate", 10),
    TRAILER_TWO_LICENSE_PLATE("mft_tl2_license_plate", 10),
    OPERATIONAL_COMMENTS("operational_comments", 4000),
    CLOSING_COMMENTS("closing_comments", 4000),
    PYR_NICKNAME("mft_s_n_svs_sge_pyr_nickname", 255),
    SSE_NAME("mft_s_n_svs_sge_sse_name", 255);

    private final String sourceField;
    private final int maximumUtf16Units;

    ManifestoTextField(final String sourceField, final int maximumUtf16Units) {
        this.sourceField = sourceField;
        this.maximumUtf16Units = maximumUtf16Units;
    }

    public String sourceField() {
        return sourceField;
    }

    public int maximumUtf16Units() {
        return maximumUtf16Units;
    }
}
