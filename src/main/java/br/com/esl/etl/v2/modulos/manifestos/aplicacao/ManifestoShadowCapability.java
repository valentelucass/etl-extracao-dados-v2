package br.com.esl.etl.v2.modulos.manifestos.aplicacao;

/** Registro declarativo da vertical na composição local; não concede execução operacional. */
public record ManifestoShadowCapability(
        int templateId,
        String entity,
        boolean stagingAllowed,
        boolean networkAllowed,
        boolean publicationAllowed,
        boolean dispatcherAllowed) {

    public ManifestoShadowCapability {
        if (templateId != ManifestoDataExportPageRequest.TEMPLATE_ID
                || !"manifestos".equals(entity)
                || !stagingAllowed
                || networkAllowed
                || publicationAllowed
                || dispatcherAllowed) {
            throw new IllegalArgumentException(
                    "A capability de Manifestos deve permanecer shadow deny-all.");
        }
    }

    public static ManifestoShadowCapability localDenyAll() {
        return new ManifestoShadowCapability(6399, "manifestos", true, false, false, false);
    }
}
