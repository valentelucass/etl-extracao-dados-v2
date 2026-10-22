package br.com.esl.etl.v2.plataforma.persistencia.controle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Complete own report before a small console summary. Caller holds its reviewed directory lease.
 */
public final class C78DevCatalogReportStore {
    public static final int MAX_FILE_BYTES = 327680;
    public static final int MAX_COVERAGE_FILE_BYTES = 2_097_152;

    public record StoredReport(Path path, String sha256, int bytes) {}

    private C78DevCatalogReportStore() {}

    public static StoredReport persist(
            final C78DevCatalogInspector.Report report, final Path reviewedOwnOccurrenceDirectory)
            throws IOException {
        return persist(report, reviewedOwnOccurrenceDirectory, C78DevCatalogPlan.Mode.ORIGINAL12);
    }

    /** Mode is supplied by the admitted consumer plan, never selected from a report tag. */
    public static StoredReport persist(
            final C78DevCatalogInspector.Report report,
            final Path reviewedOwnOccurrenceDirectory,
            final C78DevCatalogPlan.Mode admittedMode)
            throws IOException {
        Objects.requireNonNull(report);
        if (admittedMode == null || report.plan().mode() != admittedMode) {
            throw new IOException("C78_CATALOG_REPORT_MODE_SCOPE");
        }
        if (admittedMode == C78DevCatalogPlan.Mode.CONTRACT_COVERAGE12) {
            verifyModules(report);
        } else if (admittedMode == C78DevCatalogPlan.Mode.SAMPLE12_CURRENT12) {
            verifyModules(
                    report,
                    C78DevCatalogPlan.Query.CURRENT_MODULES,
                    C78DevCatalogPlan.currentModuleNames(),
                    C78DevCatalogPlan.CURRENT_MODULE_CHUNK_BYTES,
                    false);
        } else if (admittedMode == C78DevCatalogPlan.Mode.OWNED_SAMPLE12_RECOVERY12) {
            verifyOwnedRecovery(report);
        }
        final Path directory =
                Objects.requireNonNull(reviewedOwnOccurrenceDirectory).toAbsolutePath().normalize();
        for (Path ancestor = directory; ancestor != null; ancestor = ancestor.getParent()) {
            final var attributes =
                    Files.readAttributes(
                            ancestor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
                throw new IOException("C78_CATALOG_REPORT_DIRECTORY_REFUSED");
            }
        }
        if (!directory.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(directory.toRealPath())) {
            throw new IOException("C78_CATALOG_REPORT_DIRECTORY_ALIAS");
        }
        final byte[] bytes = serialize(report, admittedMode);
        final Path file = directory.resolve("catalog-" + report.plan().invocationId() + ".json");
        try (final var channel =
                FileChannel.open(
                        file,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        final byte[] actual;
        try (final var channel =
                FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            actual = readback(channel, bytes, admittedMode);
        }
        return new StoredReport(file, sha256(actual), actual.length);
    }

    private static byte[] serialize(
            final C78DevCatalogInspector.Report report, final C78DevCatalogPlan.Mode admittedMode)
            throws IOException {
        final boolean recovery = admittedMode == C78DevCatalogPlan.Mode.OWNED_SAMPLE12_RECOVERY12;
        final var output =
                new StringBuilder("{\"schema\":\"")
                        .append(
                                recovery
                                        ? "C78_DEV_OWNED_RECOVERY_REPORT_V1"
                                        : "C78_DEV_CATALOG_REPORT_V1")
                        .append("\",\"invocation\":\"")
                        .append(report.plan().invocationId())
                        .append("\",\"profile\":\"C78_LOCAL_SHADOW_SQL_PIN_V1\",\"layer\":\"")
                        .append(
                                recovery
                                        ? "owned_occurrence_recovery_counts_only"
                                        : "catalog_metadata_only")
                        .append("\",")
                        .append("\"selectsAttempted\":12,\"jdbcClosed\":true,\"queries\":[");
        boolean firstQuery = true;
        for (final var evidence : report.evidence()) {
            if (!firstQuery) {
                output.append(',');
            }
            firstQuery = false;
            output.append("{\"id\":\"").append(evidence.query().name()).append("\",\"rows\":[");
            boolean firstRow = true;
            for (final String row : evidence.rows()) {
                if (!firstRow) {
                    output.append(',');
                }
                firstRow = false;
                output.append('"').append(row).append('"');
            }
            output.append("]}");
        }
        return encodeUtf8(output.append("]}\n"), admittedMode);
    }

    static void verifyOwnedRecovery(final C78DevCatalogInspector.Report report) throws IOException {
        final var matches =
                report.evidence().stream()
                        .filter(
                                value ->
                                        value.query()
                                                == C78DevCatalogPlan.Query.OWNED_RECOVERY_SUMMARY)
                        .toList();
        final var names = C78DevCatalogPlan.ownedRecoveryMetricNames();
        if (matches.size() != 1 || matches.get(0).rows().size() != names.size()) {
            throw new IOException("C78_OWNED_RECOVERY_SUMMARY_INCOMPLETE");
        }
        for (int index = 0; index < names.size(); index++) {
            final String[] cells = matches.get(0).rows().get(index).split("\\|", -1);
            if (cells.length != 2
                    || !names.get(index).equals(cells[0])
                    || !cells[1].matches("0|[1-9][0-9]{0,18}")) {
                throw new IOException("C78_OWNED_RECOVERY_SUMMARY_SHAPE");
            }
            try {
                Long.parseLong(cells[1]);
            } catch (final NumberFormatException invalid) {
                throw new IOException("C78_OWNED_RECOVERY_SUMMARY_COUNT", invalid);
            }
        }
    }

    static byte[] encodeUtf8(final CharSequence text, final C78DevCatalogPlan.Mode admittedMode)
            throws IOException {
        final int limit = fileLimit(admittedMode);
        final var bytes = ByteBuffer.allocate(limit + 1);
        final var encoder =
                StandardCharsets.UTF_8
                        .newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        final var result = encoder.encode(CharBuffer.wrap(text), bytes, true);
        if (result.isOverflow() || bytes.position() > limit) {
            throw new IOException("C78_CATALOG_REPORT_FILE_LIMIT");
        }
        if (result.isError()) {
            throw new IOException("C78_CATALOG_REPORT_UTF8_INVALID");
        }
        final var flushed = encoder.flush(bytes);
        if (flushed.isError() || flushed.isOverflow() || bytes.position() > limit) {
            throw new IOException("C78_CATALOG_REPORT_FILE_LIMIT");
        }
        return Arrays.copyOf(bytes.array(), bytes.position());
    }

    private static int fileLimit(final C78DevCatalogPlan.Mode admittedMode) throws IOException {
        if (admittedMode == null) {
            throw new IOException("C78_CATALOG_REPORT_MODE_SCOPE");
        }
        return admittedMode != C78DevCatalogPlan.Mode.ORIGINAL12
                ? MAX_COVERAGE_FILE_BYTES
                : MAX_FILE_BYTES;
    }

    static byte[] readback(
            final FileChannel channel,
            final byte[] expected,
            final C78DevCatalogPlan.Mode admittedMode)
            throws IOException {
        if (expected.length > fileLimit(admittedMode) || channel.size() != expected.length) {
            throw new IOException("C78_CATALOG_REPORT_READBACK_SIZE");
        }
        final byte[] actual = new byte[expected.length];
        final var buffer = ByteBuffer.wrap(actual);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new IOException("C78_CATALOG_REPORT_READBACK_PARTIAL");
            }
        }
        if (channel.read(ByteBuffer.allocate(1)) != -1 || !Arrays.equals(expected, actual)) {
            throw new IOException("C78_CATALOG_REPORT_READBACK_MISMATCH");
        }
        return actual;
    }

    private static void verifyModules(final C78DevCatalogInspector.Report report)
            throws IOException {
        verifyModules(
                report,
                C78DevCatalogPlan.Query.MODULE_CONTRACTS,
                C78DevCatalogPlan.moduleContractNames(),
                256,
                true);
    }

    private static void verifyModules(
            final C78DevCatalogInspector.Report report,
            final C78DevCatalogPlan.Query query,
            final List<String> names,
            final int chunkBytes,
            final boolean allowUnavailable)
            throws IOException {
        final List<String> rows =
                report.evidence().stream()
                        .filter(e -> e.query() == query)
                        .findFirst()
                        .orElseThrow(() -> new IOException("C78_CATALOG_MODULE_QUERY_REQUIRED"))
                        .rows();
        int position = 0;
        for (final String name : names) {
            if (position == rows.size()) {
                throw new IOException("C78_CATALOG_MODULE_NOT_OBSERVED");
            }
            int chunk = 0, bytes = 0, declared = -1;
            String expectedHash = null;
            final MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (final NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("C78_CATALOG_SHA256_UNAVAILABLE");
            }
            boolean unavailable = false;
            while (position < rows.size()) {
                final String[] cells = rows.get(position).split("\\|", -1);
                if (cells.length != 6) {
                    throw new IOException("C78_CATALOG_MODULE_ROW_SHAPE");
                }
                if (!(cells[0] + "." + cells[1]).equals(name)) {
                    break;
                }
                if (!cells[2].matches("[0-9]{1,3}") || !cells[3].matches("[0-9]{1,6}")) {
                    throw new IOException("C78_CATALOG_MODULE_RAW_SIZE");
                }
                final int size = Integer.parseInt(cells[3]);
                if (Integer.parseInt(cells[2]) != chunk
                        || size > 131072
                        || size % 2 != 0
                        || chunk >= 131072 / chunkBytes
                        || unavailable) {
                    throw new IOException("C78_CATALOG_MODULE_CONTIGUITY");
                }
                if (chunk == 0) {
                    declared = size;
                    expectedHash = cells[4];
                } else if (size != declared || !cells[4].equals(expectedHash)) {
                    throw new IOException("C78_CATALOG_MODULE_DECLARED_BYTES");
                }
                if (size == 0 && cells[4].equals("UNAVAILABLE") && cells[5].equals("UNAVAILABLE")) {
                    if (!allowUnavailable) {
                        throw new IOException("C78_CATALOG_CURRENT_MODULE_UNAVAILABLE");
                    }
                    unavailable = true;
                } else {
                    if (!cells[4].matches("[A-Fa-f0-9]{64}")
                            || !cells[5].matches("(?:[A-Fa-f0-9]{2}){1," + chunkBytes + "}")) {
                        throw new IOException("C78_CATALOG_MODULE_HEX_OR_DIGEST");
                    }
                    final byte[] raw = HexFormat.of().parseHex(cells[5]);
                    if (raw.length != Math.min(chunkBytes, size - bytes) || size - bytes <= 0) {
                        throw new IOException("C78_CATALOG_MODULE_DECLARED_BYTES");
                    }
                    digest.update(raw);
                    bytes += raw.length;
                }
                chunk++;
                position++;
            }
            if (chunk == 0
                    || !unavailable
                            && (bytes != declared
                                    || !HexFormat.of()
                                            .formatHex(digest.digest())
                                            .equalsIgnoreCase(expectedHash))) {
                throw new IOException("C78_CATALOG_MODULE_INCOMPLETE_OR_DIGEST");
            }
        }
        if (position != rows.size()) {
            throw new IOException("C78_CATALOG_MODULE_FOREIGN_OR_DUPLICATE");
        }
    }

    private static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("C78_CATALOG_SHA256_UNAVAILABLE");
        }
    }
}
