package br.com.esl.etl.v2.plataforma.persistencia.controle;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Existing DPAPI UTF16 private frame, delivered only after the live Main's Source gate. */
final class C78Sample12SecretFrame {
    private C78Sample12SecretFrame() {}

    static String read(InputStream input) throws IOException {
        byte[] header = new byte[4];
        byte[] bytes = null;
        try {
            exact(input, header);
            long length =
                    Integer.toUnsignedLong(
                            ByteBuffer.wrap(header)
                                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                    .getInt());
            if (length < 2 || length > 8192 || (length & 1) != 0) {
                throw new IOException("C78_SAMPLE12_PRIVATE_SECRET_FRAME_LIMIT");
            }
            bytes = new byte[(int) length];
            exact(input, bytes);
            String secret =
                    StandardCharsets.UTF_16LE
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
            if (secret.isBlank() || secret.indexOf('\0') >= 0) {
                throw new IOException("C78_SAMPLE12_PRIVATE_SECRET_FRAME_INVALID");
            }
            return secret;
        } catch (CharacterCodingException invalid) {
            throw new IOException("C78_SAMPLE12_PRIVATE_SECRET_FRAME_ENCODING", invalid);
        } finally {
            Arrays.fill(header, (byte) 0);
            if (bytes != null) {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }

    private static void exact(InputStream input, byte[] bytes) throws IOException {
        int offset = 0;
        while (offset < bytes.length) {
            int count = input.read(bytes, offset, bytes.length - offset);
            if (count < 1) {
                throw new EOFException("C78_SAMPLE12_PRIVATE_SECRET_FRAME_INCOMPLETE");
            }
            offset += count;
        }
    }
}
