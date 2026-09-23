package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

/** Capability local: staging sintético permitido, todas as saídas externas negadas. */
public record LocalizacaoCargaShadowCapability(
        int templateId,
        String entity,
        boolean stagingAllowed,
        boolean networkAllowed,
        boolean publicationAllowed,
        boolean dispatcherAllowed) {

    public LocalizacaoCargaShadowCapability {
        if (templateId != LocalizacaoCargaDataExportPageRequest.TEMPLATE_ID
                || !"localizacao_cargas".equals(entity)
                || !stagingAllowed
                || networkAllowed
                || publicationAllowed
                || dispatcherAllowed) {
            throw new IllegalArgumentException(
                    "A capability de Localização deve permanecer shadow deny-all.");
        }
    }

    public static LocalizacaoCargaShadowCapability localDenyAll() {
        return new LocalizacaoCargaShadowCapability(
                LocalizacaoCargaDataExportPageRequest.TEMPLATE_ID,
                "localizacao_cargas",
                true,
                false,
                false,
                false);
    }
}
