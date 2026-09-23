package br.com.esl.etl.v2.modulos.raster.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;

/** Typed source attributes; candidate codes and wire representations are not canonical identity. */
public record RasterRoute(ExpansionValue<String> codRota, ExpansionValue<String> descricao) {
    public RasterRoute {
        java.util.Objects.requireNonNull(codRota);
        java.util.Objects.requireNonNull(descricao);
    }

    public boolean valid() {
        return codRota.valid() && descricao.valid();
    }
}
