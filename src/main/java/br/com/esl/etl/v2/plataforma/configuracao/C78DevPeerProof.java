package br.com.esl.etl.v2.plataforma.configuracao;

import java.io.IOException;

/** Mandatory DEV-only same-socket proof; callback carries no credential or JDBC/socket object. */
@FunctionalInterface
public interface C78DevPeerProof {
    record Tuple(String localAddress, int localPort, String remoteAddress, int remotePort) {
        public Tuple {
            if (!"127.0.0.1".equals(localAddress)
                    || !"127.0.0.1".equals(remoteAddress)
                    || localPort < 1024
                    || localPort > 65535
                    || localPort == 1433
                    || remotePort != 1433) {
                throw new IllegalArgumentException("C78_DEV_EXACT_PEER_TUPLE_REQUIRED");
            }
        }
    }

    /**
     * Runtime binder publishes this technical tuple and accepts only its exclusive, bounded
     * controller permit after current native proof on this open socket. Refusal/timeout throws.
     */
    void verify(Tuple tuple) throws IOException;
}
