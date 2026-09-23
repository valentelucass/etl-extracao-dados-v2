package br.com.esl.etl.v2.contratos.medicao;

import java.util.function.Consumer;

/** Adapta um streamer produtivo real sem reproduzir seu loop de paginação. */
@FunctionalInterface
public interface MeasurementStreamer<P> {

    void stream(Consumer<? super P> pageConsumer);
}
