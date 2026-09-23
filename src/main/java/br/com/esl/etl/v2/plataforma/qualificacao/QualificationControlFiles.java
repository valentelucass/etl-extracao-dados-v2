package br.com.esl.etl.v2.plataforma.qualificacao;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/**
 * Campaign artifacts are immutable siblings of an extracted package inside its own target round.
 */
public final class QualificationControlFiles {
    private final Path root;

    public QualificationControlFiles(
            final Path packageRoot, final Path requested, final boolean create) throws IOException {
        final var payload = packageRoot.toAbsolutePath().normalize();
        root = requested.toAbsolutePath().normalize();
        final var parent = payload.getParent();
        boolean withinTarget = false;
        for (final var part : payload) {
            withinTarget |= part.toString().equals("target");
        }
        if (!withinTarget
                || parent == null
                || !root.getParent().equals(parent)
                || root.equals(payload)
                || !root.getFileName().toString().matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("QUAL_CONTROL_ROOT");
        }
        directory(parent);
        directory(payload);
        if (create) {
            Files.createDirectory(root);
        }
        directory(root);
    }

    public Path root() {
        return root;
    }

    public Path attempt(final String caseId, final boolean create) throws IOException {
        if (caseId == null || !caseId.matches("[a-z][a-z0-9-]{0,39}")) {
            throw new IllegalArgumentException("QUAL_CONTROL_CASE");
        }
        final var result = root.resolve("case-" + caseId);
        directory(root);
        if (create) {
            Files.createDirectory(result);
        }
        directory(result);
        return result;
    }

    public static void directory(final Path path) throws IOException {
        Path current = path.toAbsolutePath().normalize();
        if (current.toString().startsWith("\\\\")) {
            throw new IllegalArgumentException("QUAL_CONTROL_UNC");
        }
        while (current != null) {
            final var attributes =
                    Files.readAttributes(
                            current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory()
                    || attributes.isSymbolicLink()
                    || attributes.isOther()
                    || !current.toRealPath()
                            .equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
                throw new IllegalArgumentException("QUAL_CONTROL_DIRECTORY_LINK");
            }
            current = current.getParent();
        }
    }

    public static Path member(final Path directory, final String name) throws IOException {
        if (!Set.of(
                        "intent.json",
                        "process.json",
                        "receipt.json",
                        "barrier.json",
                        "cancel.json",
                        "stdout.log",
                        "stderr.log",
                        "configuration.json",
                        "campaign.json",
                        "owner.json",
                        "reconciliation.json",
                        "campaign-result.json",
                        "baseline.json")
                .contains(name)) {
            throw new IllegalArgumentException("QUAL_CONTROL_MEMBER");
        }
        directory(directory);
        return directory.resolve(name);
    }

    public static void atomic(final Path path, final JsonNode document) throws IOException {
        directory(path.getParent());
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("QUAL_CONTROL_IMMUTABLE");
        }
        final var bytes = (document + "\n").getBytes(StandardCharsets.UTF_8);
        QualificationJson.parse(bytes, 1048576);
        // Retained exclusive reservation prevents two writers from racing an atomic rename.
        // Failure after reservation is an incomplete artifact, never permission to overwrite it.
        Files.createFile(path.resolveSibling(path.getFileName() + ".reserved"));
        final var temporary = path.resolveSibling(path.getFileName() + ".partial");
        try (var file =
                FileChannel.open(
                        temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                file.write(buffer);
            }
            file.force(true);
        }
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
        QualificationJson.regular(path);
    }
}
