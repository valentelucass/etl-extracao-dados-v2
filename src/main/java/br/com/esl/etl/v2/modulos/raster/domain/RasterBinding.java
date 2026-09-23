package br.com.esl.etl.v2.modulos.raster.domain;

/** Explicit lateral contract, independent of candidate source codes and physical positions. */
public record RasterBinding(
        String tripKey,
        String stopKey,
        int revision,
        boolean active,
        boolean reactivate,
        String evidence,
        String source,
        String tenant,
        String contract) {
    public RasterBinding {
        if (tripKey == null
                || !tripKey.matches("synthetic-[A-Za-z0-9-]{1,54}")
                || (stopKey != null && !stopKey.matches("synthetic-[A-Za-z0-9-]{1,54}"))
                || revision < 1
                || revision > 100000
                || evidence == null
                || !evidence.matches("synthetic-[A-Za-z0-9-]{1,54}")
                || source == null
                || !source.matches("SYNTHETIC_[A-Z0-9_]{1,30}")
                || tenant == null
                || !tenant.matches("SYNTHETIC_[A-Z0-9_]{1,30}")
                || !"synthetic-analytic-v1".equals(contract)) {
            throw new IllegalArgumentException("RAS_BINDING_CONTRACT");
        }
    }
}
