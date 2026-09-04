package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Filtro de replay fail-closed com 1 MiB fixo. Não conserva cursor nem cresce com a travessia; um
 * raro falso positivo apenas interrompe a extração, e cursores repetidos não têm falso negativo.
 */
final class GraphQlCursorCycleDetector {

    private static final int BIT_COUNT = 1 << 23;
    private static final int WORD_SHIFT = 6;
    private static final int HASH_FUNCTIONS = 6;
    private final long[] observedBits = new long[BIT_COUNT >>> WORD_SHIFT];
    private final MessageDigest digest = sha256();

    void observe(final GraphQlCursor cursor) {
        final byte[] fingerprint = digest.digest(cursor.value().getBytes(StandardCharsets.UTF_8));
        boolean probablyObserved = true;
        for (int hash = 0; hash < HASH_FUNCTIONS; hash++) {
            final int offset = hash * Integer.BYTES;
            final int value =
                    (fingerprint[offset] & 0xff) << 24
                            | (fingerprint[offset + 1] & 0xff) << 16
                            | (fingerprint[offset + 2] & 0xff) << 8
                            | fingerprint[offset + 3] & 0xff;
            final int bit = value & (BIT_COUNT - 1);
            final int word = bit >>> WORD_SHIFT;
            final long mask = 1L << (bit & 63);
            probablyObserved &= (observedBits[word] & mask) != 0L;
            observedBits[word] |= mask;
        }
        if (probablyObserved) {
            throw new GraphQlPaginationException(GraphQlPaginationException.Reason.REPEATED_CURSOR);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível.", exception);
        }
    }
}
