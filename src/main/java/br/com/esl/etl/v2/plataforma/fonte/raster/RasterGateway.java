package br.com.esl.etl.v2.plataforma.fonte.raster;

import br.com.esl.etl.v2.modulos.raster.domain.RasterBinding;
import java.io.IOException;

/** Source boundary with separately supplied synthetic evidence, never provider payload metadata. */
public interface RasterGateway {
    Response fetch(RasterWindow window) throws IOException, InterruptedException;

    RasterBinding binding(RasterWindow window, int tripPosition, int stopPosition);

    record Response(byte[] body, Terminal terminal) {
        public Response {
            if (body == null || body.length == 0 || body.length > RasterResponseParser.MAX_BYTES) {
                throw new IllegalArgumentException("RAS_BODY_BOUND");
            }
            body = body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    record Terminal(
            RasterWindow window,
            long trips,
            long stops,
            String receipt,
            String source,
            String tenant,
            String contract) {
        public Terminal {
            if (window == null
                    || trips < 0
                    || stops < 0
                    || receipt == null
                    || !receipt.matches("synthetic-[A-Za-z0-9-]{1,54}")
                    || source == null
                    || !source.matches("SYNTHETIC_[A-Z0-9_]{1,30}")
                    || tenant == null
                    || !tenant.matches("SYNTHETIC_[A-Z0-9_]{1,30}")
                    || !"synthetic-analytic-v1".equals(contract)) {
                throw new IllegalArgumentException("RAS_TERMINAL_SCOPE");
            }
        }
    }
}
