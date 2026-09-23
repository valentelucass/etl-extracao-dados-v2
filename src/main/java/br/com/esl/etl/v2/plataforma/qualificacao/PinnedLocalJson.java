package br.com.esl.etl.v2.plataforma.qualificacao;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** A bounded local file reference; each read verifies the exact bytes returned to its consumer. */
public final class PinnedLocalJson {
    private final Path path;
    private final String sha256;
    private final int maximumBytes;

    public static PinnedLocalJson open(final Path path, final int maximumBytes) throws IOException {
        QualificationJson.regular(path);
        if (maximumBytes < 1 || maximumBytes > 2097152) {
            throw new IllegalArgumentException("LOCAL_PIN_BOUND");
        }
        final byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(maximumBytes + 1);
        }
        QualificationJson.parse(bytes, maximumBytes);
        return new PinnedLocalJson(path, QualificationJson.sha256(bytes), maximumBytes);
    }

    public PinnedLocalJson(final Path path, final String sha256, final int maximumBytes) {
        if (path == null
                || sha256 == null
                || !sha256.matches("[a-f0-9]{64}")
                || maximumBytes < 1
                || maximumBytes > 2097152) {
            throw new IllegalArgumentException("LOCAL_PIN_BOUND");
        }
        this.path = path.toAbsolutePath().normalize();
        this.sha256 = sha256;
        this.maximumBytes = maximumBytes;
    }

    /** A single file descriptor, not a traversable path or directory inventory. */
    public File file() {
        return path.toFile();
    }

    public String sha256() {
        return sha256;
    }

    public static PinnedLocalJson reference(
            final Path directory, final JsonNode descriptor, final int maximumBytes) {
        QualificationJson.fields(descriptor, "file", "sha256");
        final String name = QualificationJson.text(descriptor, "file", 160);
        if (!name.matches("[a-z0-9][a-z0-9/-]{0,150}\\.json") || name.contains("//")) {
            throw new IllegalArgumentException("LOCAL_PIN_PATH");
        }
        return new PinnedLocalJson(
                directory.resolve(name),
                QualificationJson.digest(descriptor, "sha256"),
                maximumBytes);
    }

    private byte[] bytes() throws IOException {
        QualificationJson.regular(path);
        final byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(maximumBytes + 1);
        }
        if (bytes.length == 0
                || bytes.length > maximumBytes
                || !sha256.equals(QualificationJson.sha256(bytes))) {
            throw new IllegalArgumentException("LOCAL_PIN_BYTES");
        }
        return bytes;
    }

    public JsonNode read() throws IOException {
        return QualificationJson.parse(bytes(), maximumBytes);
    }

    public void verify() throws IOException {
        bytes();
    }

    /** Reads one bounded page, verifies its pin, and passes those exact bytes to the consumer. */
    public <T, E extends Exception> T consume(final Reader<T, E> reader) throws IOException, E {
        return Objects.requireNonNull(reader).read(bytes());
    }

    @FunctionalInterface
    public interface Reader<T, E extends Exception> {
        T read(byte[] bytes) throws IOException, E;
    }
}
