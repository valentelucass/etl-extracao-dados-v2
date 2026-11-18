package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/** In-process boundary only. No CLI, property, environment or service-loader selection exists. */
@FunctionalInterface
interface OwnedExportWire {
    CurlExportTransport.Wire exchange(
            String method,
            URI target,
            byte[] body,
            boolean authentication,
            int maximumBodyBytes,
            Duration timeout,
            Instant deadline)
            throws Exception;
}
