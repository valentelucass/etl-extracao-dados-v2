package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.databind.JsonNode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Suspeita de replay por conteúdo físico, com Bloom de 1 MiB por travessia. Não conserva registros
 * nem fingerprints entre páginas. Falsos positivos podem interromper páginas novas, com risco
 * crescente conforme o filtro se ocupa; uma recusa não comprova replay do fornecedor.
 *
 * <p>Ignora a ordem das propriedades de objetos, mas conserva a ordem de linhas/arrays, tipos e
 * escala decimal. Não detecta toda ausência de progresso, nem prova identidade ou completude.
 */
final class DataExportPageProgressGuard {

    private static final int BIT_COUNT = 1 << 23;
    private static final int WORD_SHIFT = 6;
    private static final int HASH_FUNCTIONS = 6;
    private final long[] observedBits = new long[BIT_COUNT >>> WORD_SHIFT];
    private final MessageDigest digest = sha256();

    void observe(final DataExportPageResponse response) {
        if (response.recordCount() == 0) {
            return;
        }
        digest.reset();
        updateInt(response.recordCount());
        response.forEachRecord(this::updateNode);
        final byte[] fingerprint = digest.digest();
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
            throw new IllegalStateException(
                    "DATAEXPORT_PAGE_PROGRESS_NOT_OBSERVED: suspeita de repetição de página;"
                            + " o filtro probabilístico pode recusar uma página nova.");
        }
    }

    private void updateNode(final JsonNode node) {
        updateInt(node.getNodeType().ordinal());
        switch (node.getNodeType()) {
            case OBJECT -> updateObject(node);
            case ARRAY -> {
                updateInt(node.size());
                for (final JsonNode element : node) {
                    updateNode(element);
                }
            }
            case STRING -> updateString(node.textValue());
            case NUMBER -> {
                updateInt(node.numberType().ordinal());
                updateString(node.asText());
            }
            case BOOLEAN -> digest.update((byte) (node.booleanValue() ? 1 : 0));
            case NULL, MISSING -> {
                // O tag de tipo distingue null de campo ausente ou nó missing.
            }
            default ->
                    throw new IllegalStateException(
                            "DATAEXPORT_PAGE_PROGRESS_NODE_UNSUPPORTED: tipo JSON não suportado.");
        }
    }

    private void updateObject(final JsonNode node) {
        final List<String> names = new ArrayList<>(node.size());
        node.fieldNames().forEachRemaining(names::add);
        names.sort(String::compareTo);
        updateInt(names.size());
        for (final String name : names) {
            updateString(name);
            updateNode(node.get(name));
        }
    }

    private void updateString(final String value) {
        updateInt(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char codeUnit = value.charAt(index);
            digest.update((byte) (codeUnit >>> Byte.SIZE));
            digest.update((byte) codeUnit);
        }
    }

    private void updateInt(final int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível.", exception);
        }
    }
}
