package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualifiedPackage;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.jar.JarFile;

/** Independent verifier fixture assembled from this Maven package phase, never a release proof. */
final class QualificationPackageFixture {
    static final int SEQUENCE_CAMPAIGN_DEADLINE_SECONDS = 259200;

    private final Path root;
    private final ObjectNode manifest;
    private final ArrayNode members;

    private QualificationPackageFixture(final Path root, final ObjectNode manifest) {
        this.root = root;
        this.manifest = manifest;
        members = (ArrayNode) manifest.path("files");
    }

    static QualificationPackageFixture create() throws Exception {
        final Path root =
                Path.of("target", "qf", UUID.randomUUID().toString().substring(0, 18), "payload")
                        .toAbsolutePath();
        Files.createDirectories(root);
        final var document =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "qualification-package-v1")
                        .put(
                                "revision",
                                QualificationJson.sha256(Path.of("target/etl-dataexport-v2.jar")))
                        .put("java", 17)
                        .put("os", "Windows")
                        .put("architecture", "x64")
                        .put("schemaVersion", QualifiedPackage.SCHEMA_VERSION);
        document.putArray("files");
        final var fixture = new QualificationPackageFixture(root, document);
        fixture.copy(
                Path.of("target/etl-dataexport-v2.jar"),
                "etl-dataexport-v2.jar",
                "JAR",
                "APPLICATION");
        final Path catalog = Path.of("docs/catalogos/macrobloco-qualificacao-pacote");
        fixture.copy(
                catalog.resolve("dependency-lock.json"), "dependencies.json", "JSON", "POLICY");
        final var lock = QualificationJson.read(root.resolve("dependencies.json"), 65536);
        final var bom =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("bomFormat", "CycloneDX")
                        .put("specVersion", "1.6")
                        .put("version", 1);
        bom.putObject("metadata")
                .putObject("component")
                .putArray("hashes")
                .addObject()
                .put("alg", "SHA-256")
                .put("content", fixture.hash("etl-dataexport-v2.jar"));
        final var components = bom.putArray("components");
        for (final var dependency : lock.path("dependencies")) {
            final boolean nativeAuth = dependency.path("type").asText().equals("dll");
            final String name =
                    (nativeAuth ? "native/" : "lib/") + dependency.path("file").asText();
            fixture.copy(
                    Path.of("target").resolve(name),
                    name,
                    nativeAuth ? "DLL" : "JAR",
                    nativeAuth ? "NATIVE_AUTH" : "DEPENDENCY");
            fixture.copy(
                    catalog.resolve("third-party").resolve(dependency.path("pom").asText()),
                    "licenses/" + dependency.path("pom").asText(),
                    "XML",
                    "LICENSE");
            final String group = dependency.path("group").asText();
            final String artifact = dependency.path("name").asText();
            final String version = dependency.path("version").asText();
            final var component =
                    components
                            .addObject()
                            .put("bom-ref", name)
                            .put("group", group)
                            .put("name", artifact)
                            .put("version", version)
                            .put(
                                    "purl",
                                    "pkg:maven/"
                                            + group
                                            + "/"
                                            + artifact
                                            + "@"
                                            + version
                                            + "?type="
                                            + dependency.path("type").asText());
            component
                    .putArray("hashes")
                    .addObject()
                    .put("alg", "SHA-256")
                    .put("content", fixture.hash(name));
            component.putArray("licenses").add(dependency.path("license"));
            component
                    .putArray("externalReferences")
                    .addObject()
                    .put("type", "distribution")
                    .put("url", dependency.path("origin").asText());
        }
        fixture.json("sbom.cdx.json", bom, "SBOM");
        fixture.json(
                "provenance.json",
                JsonNodeFactory.instance
                        .objectNode()
                        .put("revision", document.path("revision").asText())
                        .put("sourceKind", "INDEPENDENT_INTEGRATION_FIXTURE_AFTER_MAVEN_PACKAGE")
                        .put("vulnerabilityFeed", "NOT_EXECUTED")
                        .put("signed", false)
                        .put("publishedCi", false)
                        .put("dependencyLockSha256", fixture.hash("dependencies.json")),
                "PROVENANCE");
        final var index =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "qualification-fixtures-v1")
                        .put("origin", "SYNTHETIC_INPUTS_NOT_QUERY_OUTPUTS");
        final var resources = index.putArray("files");
        try (var jar = new JarFile(root.resolve("etl-dataexport-v2.jar").toFile())) {
            final var entries = jar.entries();
            while (entries.hasMoreElements()) {
                final var entry = entries.nextElement();
                if (entry.isDirectory()
                        || !(entry.getName().startsWith("analytic-laboratory/")
                                || entry.getName().startsWith("expansion-laboratory/"))) {
                    continue;
                }
                final String name = "fixtures/" + entry.getName();
                fixture.resource(jar, entry.getName(), name, "FIXTURE");
                resources
                        .addObject()
                        .put("resource", entry.getName())
                        .put("member", name)
                        .put("sha256", fixture.hash(name));
            }
            fixture.resource(
                    jar,
                    "analytic-laboratory/query-contracts.synthetic.json",
                    "contracts/query-contracts.synthetic.json",
                    "CONTRACT");
            fixture.resource(
                    jar,
                    "qualification-laboratory/config.synthetic.json",
                    "config/config.synthetic.json",
                    "CONFIGURATION");
            fixture.resource(
                    jar,
                    "qualification-laboratory/physical-columns.v098.json",
                    "contracts/physical-columns.v098.json",
                    "CONTRACT");
            for (final String name :
                    new String[] {
                        "outputs.synthetic.json",
                        "location-hashes.synthetic.json",
                        "location-representative-hashes.synthetic.json"
                    }) {
                fixture.resource(
                        jar, "qualification-laboratory/" + name, "oracles/" + name, "ORACLE");
            }
        }
        fixture.json("fixtures/fixture-index.json", index, "FIXTURE");
        final var schema =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "qualification-schema-v1")
                        .put("lastVersion", QualifiedPackage.SCHEMA_VERSION)
                        .put("installationAutomatic", false);
        final var migrations = schema.putArray("migrations");
        try (var paths = Files.list(Path.of("database/migrations"))) {
            for (final Path path : paths.sorted().toList()) {
                final String name = "schema/migrations/" + path.getFileName();
                fixture.copy(path, name, "SQL", "SCHEMA");
                migrations.addObject().put("path", name).put("sha256", fixture.hash(name));
            }
        }
        final String baseline = "schema/baseline/001_schema_foundation_baseline.sql";
        fixture.copy(
                Path.of("database/baseline/001_schema_foundation_baseline.sql"),
                baseline,
                "SQL",
                "SCHEMA");
        schema.put("baselineSha256", fixture.hash(baseline));
        fixture.json("schema/schema-index.json", schema, "SCHEMA");
        fixture.copy(
                Path.of("scripts/validation/Invoke-Qualification.ps1"),
                "Invoke-Qualification.ps1",
                "POWERSHELL",
                "LAUNCHER");
        fixture.seal();
        return fixture;
    }

    QualificationPackageFixture fork() throws Exception {
        return fork("");
    }

    QualificationPackageFixture fork(final String prefix) throws Exception {
        final Path next =
                root.getParent()
                        .getParent()
                        .resolve(prefix + UUID.randomUUID().toString().substring(0, 18))
                        .resolve("payload");
        try (var paths = Files.walk(root)) {
            for (final Path path : paths.toList()) {
                final Path destination = next.resolve(root.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
        return new QualificationPackageFixture(next, manifest.deepCopy());
    }

    Path root() {
        return root;
    }

    QualifiedPackage verify() throws Exception {
        return QualifiedPackage.verify(
                root, QualificationJson.sha256(root.resolve("package.json")));
    }

    ObjectNode campaign() throws Exception {
        final var campaign = QualificationContractTest.campaign();
        final var pins = (ObjectNode) campaign.path("pins");
        pins.put("revision", manifest.path("revision").asText())
                .put("jar", hash("etl-dataexport-v2.jar"))
                .put("schema", hash("schema/schema-index.json"))
                .put("contracts", hash("contracts/query-contracts.synthetic.json"))
                .put("fixture", hash("fixtures/fixture-index.json"))
                .put("oracle", hash("oracles/outputs.synthetic.json"));
        final var item = (ObjectNode) campaign.path("cases").get(0);
        item.put("lookbackSeconds", 0);
        final var outputs = item.putArray("outputs");
        for (int number = 1; number <= 19; number++) {
            outputs.add(String.format(java.util.Locale.ROOT, "SQL-%02d", number));
        }
        return campaign;
    }

    void includeSequenceCases() throws Exception {
        final Path source =
                root.getParent()
                        .resolve(
                                "sequence-author-" + UUID.randomUUID().toString().substring(0, 18));
        try (var runtime = new PackagedFixtureRuntime(root.resolve("etl-dataexport-v2.jar"))) {
            runtime.author(source);
        }
        final Path indexPath = source.resolve("artifact-cases/index.json");
        final var index = QualificationJson.read(indexPath, 262144);
        final var selected = (ObjectNode) index.path("cases").get(0);
        for (final var member : selected.path("members")) {
            final String name = member.path("path").asText();
            copy(source.resolve(name), name, "JSON", member.path("role").asText());
        }
        final var selectedIndex = (ObjectNode) index.deepCopy();
        final var cases = (ArrayNode) selectedIndex.path("cases");
        cases.removeAll();
        cases.add(selected.deepCopy());
        json("artifact-cases/index.json", selectedIndex, "FIXTURE");
        seal();
    }

    ObjectNode sequenceCampaign(final String id, final String expected, final String barrier)
            throws Exception {
        final var campaign = campaign();
        final var sequence =
                (ObjectNode)
                        QualificationJson.read(
                                root.resolve("artifact-cases").resolve(id).resolve("sequence.json"),
                                65536);
        final var item = (ObjectNode) campaign.path("cases").get(0);
        campaign.put("id", id)
                .put("roots", sequence.path("roots").asInt())
                .put("pageSize", sequence.path("pageSize").asInt())
                .put("maximumSeconds", 1800);
        item.put("id", id)
                .put("wave", "sequence")
                .put("action", "SEQUENCE")
                .put("barrier", barrier)
                .put("expected", expected)
                .put("tick", sequence.path("windowEndExclusive").asText() + "T12:00:00Z")
                .put("start", sequence.path("windowStart").asText())
                .put("endExclusive", sequence.path("windowEndExclusive").asText())
                .put("lookbackSeconds", 0)
                // The bounded planner dispatches the first 11-Aug civil window at the 14-Aug tick.
                // Its logical deadline must cover that fixture interval; this is not a process
                // timeout.
                .put("deadlineSeconds", SEQUENCE_CAMPAIGN_DEADLINE_SECONDS)
                .put("maximumCatchUp", 1);
        return campaign;
    }

    void makeSequenceStageFail(final String id, final int stage) throws Exception {
        final String sequenceMember = "artifact-cases/" + id + "/sequence.json";
        final var sequence =
                (ObjectNode) QualificationJson.read(root.resolve(sequenceMember), 65536);
        final String oracleMember =
                "artifact-cases/"
                        + id
                        + "/"
                        + sequence.path("steps").get(stage).path("oracle").path("file").asText();
        final var oracle = (ObjectNode) QualificationJson.read(root.resolve(oracleMember), 65536);
        final String outputMember =
                "artifact-cases/" + id + "/" + oracle.path("outputs").path("file").asText();
        mutate(outputMember, value -> ((ObjectNode) value.path("facts")).put("MAT01", 999));
        final String outputHash = hash(outputMember);
        mutate(
                oracleMember,
                value -> ((ObjectNode) value.path("outputs")).put("sha256", outputHash));
        final String oracleHash = hash(oracleMember);
        mutate(
                sequenceMember,
                value ->
                        ((ObjectNode) value.path("steps").get(stage).path("oracle"))
                                .put("sha256", oracleHash));
    }

    void mutate(final String name, final java.util.function.Consumer<ObjectNode> change)
            throws Exception {
        final var document = (ObjectNode) QualificationJson.read(root.resolve(name), 1048576);
        change.accept(document);
        Files.writeString(root.resolve(name), document + "\n");
        for (final var entry : members) {
            if (entry.path("path").asText().equals(name)) {
                ((ObjectNode) entry)
                        .put("size", Files.size(root.resolve(name)))
                        .put("sha256", hash(name));
            }
        }
        seal();
    }

    private void resource(
            final JarFile jar, final String resource, final String name, final String role)
            throws Exception {
        try (var input = jar.getInputStream(jar.getJarEntry(resource))) {
            final var bytes = input.readNBytes(1048577);
            if (bytes.length > 1048576) {
                throw new IllegalArgumentException("TEST_RESOURCE_BOUND");
            }
            write(name, bytes, name.endsWith(".sql") ? "SQL" : "JSON", role);
        }
    }

    private void copy(final Path source, final String name, final String type, final String role)
            throws Exception {
        write(name, Files.readAllBytes(source), type, role);
    }

    private void json(final String name, final ObjectNode value, final String role)
            throws Exception {
        write(name, (value + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8), "JSON", role);
    }

    private void write(final String name, final byte[] bytes, final String type, final String role)
            throws Exception {
        Files.createDirectories(root.resolve(name).getParent());
        Files.write(root.resolve(name), bytes);
        members.addObject()
                .put("path", name)
                .put("size", bytes.length)
                .put("sha256", hash(name))
                .put("type", type)
                .put("role", role);
    }

    private String hash(final String name) throws Exception {
        return QualificationJson.sha256(root.resolve(name));
    }

    private void seal() throws Exception {
        Files.writeString(root.resolve("package.json"), manifest + "\n");
        Files.writeString(root.resolve("package.sha256"), hash("package.json") + "\n");
    }
}
