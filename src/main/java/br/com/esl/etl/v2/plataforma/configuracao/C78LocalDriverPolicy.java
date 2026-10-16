package br.com.esl.etl.v2.plataforma.configuracao;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Driver;
import java.util.HexFormat;
import java.util.jar.JarFile;

/** Exact existing 12.8.2.jre11 bytes; no driver discovery, download or fallback. */
public final class C78LocalDriverPolicy {
    public static final String DRIVER_CLASS = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    public static final String DRIVER_SHA256 =
            "A298B4B42A80C283961674CC37223B84CACECF5CC6ABCCF1319EBD7D2A2462DF";
    private static final long MAX_JAR_BYTES = 2 * 1024 * 1024;

    private C78LocalDriverPolicy() {}

    public static Driver load() {
        try {
            final var type =
                    Class.forName(DRIVER_CLASS, false, C78LocalDriverPolicy.class.getClassLoader());
            validate(type);
            final var driver = (Driver) type.getConstructor().newInstance();
            if (driver.getMajorVersion() != 12 || driver.getMinorVersion() != 8) {
                throw new IllegalStateException("C78_LOCAL_SHADOW_DRIVER_VERSION_REFUSED");
            }
            return driver;
        } catch (final Exception | LinkageError failure) {
            throw new IllegalStateException("C78_LOCAL_SHADOW_DRIVER_12_8_2_REQUIRED");
        }
    }

    static void validate(final Class<?> type) throws Exception {
        if (type == null || !DRIVER_CLASS.equals(type.getName())) {
            throw new IllegalStateException("C78_LOCAL_SHADOW_DRIVER_REFUSED");
        }
        final var source = type.getProtectionDomain().getCodeSource();
        if (source == null || !"file".equals(source.getLocation().getProtocol())) {
            throw new IllegalStateException("C78_LOCAL_SHADOW_DRIVER_LOCAL_JAR_REQUIRED");
        }
        final Path jar = Path.of(source.getLocation().toURI());
        if (!Files.isRegularFile(jar) || Files.size(jar) > MAX_JAR_BYTES) {
            throw new IllegalStateException("C78_LOCAL_SHADOW_DRIVER_JAR_BOUND");
        }
        final var hash = MessageDigest.getInstance("SHA-256");
        final byte[] buffer = new byte[8192];
        long total = 0;
        try (var stream = Files.newInputStream(jar)) {
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_JAR_BYTES) {
                    throw new IllegalStateException("C78_LOCAL_SHADOW_DRIVER_JAR_BOUND");
                }
                hash.update(buffer, 0, count);
            }
        } finally {
            java.util.Arrays.fill(buffer, (byte) 0);
        }
        if (!DRIVER_SHA256.equals(HexFormat.of().withUpperCase().formatHex(hash.digest()))) {
            throw new IllegalStateException("C78_LOCAL_SHADOW_DRIVER_BYTES_REFUSED");
        }
        try (var archive = new JarFile(jar.toFile(), false)) {
            if (!"12.8.2"
                    .equals(archive.getManifest().getMainAttributes().getValue("Bundle-Version"))) {
                throw new IllegalStateException("C78_LOCAL_SHADOW_DRIVER_VERSION_REFUSED");
            }
        }
    }
}
