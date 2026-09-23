package br.com.esl.etl.v2.plataforma.qualificacao;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/** Exact content inventory, pinned by the caller, verified before opening a laboratory session. */
public record QualifiedPackage(
        Path root, String manifestSha256, String revision, Map<String, Member> members) {
    public static final int SCHEMA_VERSION = 102;

    public record Member(String path, long size, String sha256, String type, String role) {}

    public QualifiedPackage {
        if (members == null || members.size() > 512) {
            throw new IllegalArgumentException("QUAL_PACKAGE_MEMBERS_BOUND");
        }
        members = Map.copyOf(members);
    }

    public static QualifiedPackage verify(final Path directory, final String manifestSha256)
            throws IOException {
        final var root = directory.toAbsolutePath().normalize();
        QualificationControlFiles.directory(root);
        final var manifest = root.resolve("package.json");
        if (manifestSha256 == null
                || !manifestSha256.matches("[a-f0-9]{64}")
                || !manifestSha256.equals(QualificationJson.sha256(manifest))) {
            throw new IllegalArgumentException("QUAL_PACKAGE_PIN");
        }
        final var json = QualificationJson.read(manifest, 1048576);
        QualificationJson.fields(
                json,
                "version",
                "revision",
                "java",
                "os",
                "architecture",
                "schemaVersion",
                "files");
        if (!"qualification-package-v1".equals(QualificationJson.text(json, "version", 40))
                || QualificationJson.number(json, "java", 17, 17) != 17
                || !"Windows".equals(QualificationJson.text(json, "os", 16))
                || !"x64".equals(QualificationJson.text(json, "architecture", 8))
                || QualificationJson.number(json, "schemaVersion", SCHEMA_VERSION, SCHEMA_VERSION)
                        != SCHEMA_VERSION) {
            throw new IllegalArgumentException("QUAL_PACKAGE_COMPATIBILITY");
        }
        QualificationJson.array(json.path("files"), 12, 512);
        final Map<String, Member> members = new HashMap<>();
        final Set<String> folded = new HashSet<>();
        long total = 0;
        for (final var item : json.path("files")) {
            QualificationJson.fields(item, "path", "size", "sha256", "type", "role");
            final String name = safeMember(QualificationJson.text(item, "path", 200));
            final String type = QualificationJson.text(item, "type", 16);
            final String role = QualificationJson.text(item, "role", 24);
            if (!List.of("JAR", "DLL", "JSON", "SQL", "TEXT", "POWERSHELL", "XML").contains(type)
                    || !List.of(
                                    "APPLICATION",
                                    "DEPENDENCY",
                                    "NATIVE_AUTH",
                                    "CONFIGURATION",
                                    "CONTRACT",
                                    "SCHEMA",
                                    "FIXTURE",
                                    "ORACLE",
                                    "LAUNCHER",
                                    "DOCUMENTATION",
                                    "SBOM",
                                    "PROVENANCE",
                                    "LICENSE",
                                    "POLICY")
                            .contains(role)) {
                throw new IllegalArgumentException("QUAL_PACKAGE_ROLE");
            }
            final int size = QualificationJson.number(item, "size", 1, 33554432);
            total = Math.addExact(total, size);
            if (total > 100663296 || !folded.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("QUAL_PACKAGE_DUPLICATE_OR_SIZE");
            }
            final String hash = QualificationJson.digest(item, "sha256");
            final Path file = root.resolve(name);
            QualificationJson.regular(file);
            if (Files.size(file) != size || !hash.equals(QualificationJson.sha256(file))) {
                throw new IllegalArgumentException("QUAL_PACKAGE_MEMBER_HASH");
            }
            members.put(name, new Member(name, size, hash, type, role));
        }
        final Set<String> actual = new HashSet<>();
        try (var paths = Files.walk(root)) {
            final var iterator = paths.iterator();
            while (iterator.hasNext()) {
                final var path = iterator.next();
                if (Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    QualificationControlFiles.directory(path);
                    continue;
                }
                QualificationJson.regular(path);
                actual.add(root.relativize(path).toString().replace('\\', '/'));
                if (actual.size() > 514) {
                    throw new IllegalArgumentException("QUAL_PACKAGE_MEMBER_LIMIT");
                }
            }
        }
        final Set<String> expected = new HashSet<>(members.keySet());
        expected.add("package.json");
        expected.add("package.sha256");
        if (!actual.equals(expected)
                || !Files.readString(root.resolve("package.sha256"), StandardCharsets.US_ASCII)
                        .equals(manifestSha256 + "\n")) {
            throw new IllegalArgumentException("QUAL_PACKAGE_CONTENT_SET");
        }
        final var result =
                new QualifiedPackage(
                        root, manifestSha256, QualificationJson.digest(json, "revision"), members);
        result.requiredRoles();
        // Conservative local Windows native-loader boundary, checked before any JDBC consumer.
        if (members.values().stream()
                .filter(item -> item.role().equals("NATIVE_AUTH"))
                .anyMatch(item -> root.resolve(item.path()).toString().length() > 240)) {
            throw new IllegalArgumentException("QUAL_PACKAGE_NATIVE_PATH_LIMIT");
        }
        result.verifySbom();
        result.verifyJar();
        QualificationPackageContents.verify(result);
        return result;
    }

    public static String safeMember(final String name) {
        if (name == null
                || !name.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,199}")
                || name.endsWith("/")
                || name.equals("package.json")
                || name.equals("package.sha256")) {
            throw new IllegalArgumentException("QUAL_PACKAGE_PATH");
        }
        for (final String part : name.split("/", -1)) {
            if (part.isEmpty()
                    || part.equals(".")
                    || part.equals("..")
                    || part.endsWith(".")
                    || part.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(?:\\..*)?")) {
                throw new IllegalArgumentException("QUAL_PACKAGE_PATH");
            }
        }
        return name;
    }

    public Path member(final String name, final String role) {
        final var item = members.get(name);
        if (item == null || !item.role().equals(role)) {
            throw new IllegalArgumentException("QUAL_PACKAGE_REQUIRED_MEMBER");
        }
        return root.resolve(name);
    }

    private void requiredRoles() {
        member("etl-dataexport-v2.jar", "APPLICATION");
        member("config/config.synthetic.json", "CONFIGURATION");
        member("contracts/query-contracts.synthetic.json", "CONTRACT");
        member("oracles/outputs.synthetic.json", "ORACLE");
        member("fixtures/fixture-index.json", "FIXTURE");
        member("schema/schema-index.json", "SCHEMA");
        member("sbom.cdx.json", "SBOM");
        member("provenance.json", "PROVENANCE");
        member("Invoke-Qualification.ps1", "LAUNCHER");
        if (members.values().stream().filter(item -> item.role().equals("DEPENDENCY")).count() != 8
                || members.values().stream()
                                .filter(item -> item.role().equals("NATIVE_AUTH"))
                                .count()
                        != 1) {
            throw new IllegalArgumentException("QUAL_PACKAGE_DEPENDENCY_COUNT");
        }
    }

    private void verifySbom() throws IOException {
        final JsonNode bom = QualificationJson.read(member("sbom.cdx.json", "SBOM"), 262144);
        if (!"CycloneDX".equals(bom.path("bomFormat").asText())
                || !"1.6".equals(bom.path("specVersion").asText())
                || !bom.path("version").isIntegralNumber()
                || bom.path("version").intValue() != 1) {
            throw new IllegalArgumentException("QUAL_SBOM_VERSION");
        }
        QualificationJson.array(bom.path("components"), 9, 9);
        final Set<String> actual = new HashSet<>();
        for (final var component : bom.path("components")) {
            final String name = QualificationJson.text(component, "bom-ref", 200);
            final var entry = members.get(name);
            if (entry == null
                    || !List.of("DEPENDENCY", "NATIVE_AUTH").contains(entry.role())
                    || !actual.add(name)
                    || !component.path("version").isTextual()
                    || component.path("licenses").size() != 1
                    || !component.path("purl").asText().startsWith("pkg:maven/")
                    || component.path("externalReferences").size() != 1
                    || component.path("hashes").size() != 1
                    || !"SHA-256".equals(component.path("hashes").get(0).path("alg").asText())
                    || !entry.sha256()
                            .equals(component.path("hashes").get(0).path("content").asText())) {
                throw new IllegalArgumentException("QUAL_SBOM_CONTENT");
            }
        }
        final var provenance =
                QualificationJson.read(member("provenance.json", "PROVENANCE"), 262144);
        if (!revision.equals(provenance.path("revision").asText())
                || !"NOT_EXECUTED".equals(provenance.path("vulnerabilityFeed").asText())
                || provenance.path("signed").asBoolean(true)
                || provenance.path("publishedCi").asBoolean(true)) {
            throw new IllegalArgumentException("QUAL_PROVENANCE_SCOPE");
        }
    }

    private void verifyJar() throws IOException {
        try (var jar = new JarFile(member("etl-dataexport-v2.jar", "APPLICATION").toFile())) {
            final String classpath = jar.getManifest().getMainAttributes().getValue("Class-Path");
            final Set<String> expected = new HashSet<>();
            members.values().stream()
                    .filter(item -> item.role().equals("DEPENDENCY"))
                    .forEach(item -> expected.add(item.path()));
            if (classpath == null
                    || !new HashSet<>(List.of(classpath.split(" +"))).equals(expected)) {
                throw new IllegalArgumentException("QUAL_PACKAGE_JAR_CLASSPATH");
            }
            final var entry = jar.getJarEntry("analytic-laboratory/query-contracts.synthetic.json");
            if (entry == null) {
                throw new IllegalArgumentException("QUAL_PACKAGE_JAR_CONTRACT");
            }
            try (var input = jar.getInputStream(entry)) {
                if (!QualificationJson.sha256(input.readNBytes(262145))
                        .equals(members.get("contracts/query-contracts.synthetic.json").sha256())) {
                    throw new IllegalArgumentException("QUAL_PACKAGE_JAR_CONTRACT");
                }
            }
        }
    }

    public void verifyPins(final QualificationCampaign.Pins pins) {
        if (!revision.equals(pins.revision())
                || !members.get("etl-dataexport-v2.jar").sha256().equals(pins.jar())
                || !members.get("schema/schema-index.json").sha256().equals(pins.schema())
                || !members.get("contracts/query-contracts.synthetic.json")
                        .sha256()
                        .equals(pins.contracts())
                || !members.get("fixtures/fixture-index.json").sha256().equals(pins.fixture())
                || !members.get("oracles/outputs.synthetic.json").sha256().equals(pins.oracle())) {
            throw new IllegalArgumentException("QUAL_PACKAGE_CAMPAIGN_REVISION");
        }
    }

    public void verifyRunningArtifact(final Class<?> entrypoint) throws Exception {
        final var location =
                Path.of(entrypoint.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isSameFile(location, member("etl-dataexport-v2.jar", "APPLICATION"))
                || Runtime.version().feature() != 17
                || !System.getProperty("os.name").startsWith("Windows")
                || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch"))) {
            throw new IllegalArgumentException("QUAL_PACKAGE_RUNTIME_LOCATION");
        }
        final String classpath = System.getProperty("java.class.path");
        if (classpath.contains(java.io.File.pathSeparator)
                || !Files.isSameFile(Path.of(classpath), location)) {
            throw new IllegalArgumentException("QUAL_PACKAGE_RUNTIME_CLASSPATH");
        }
    }
}
