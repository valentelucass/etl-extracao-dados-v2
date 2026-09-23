package br.com.esl.etl.v2.plataforma.qualificacao;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Exact cross-document correspondence, including executable JAR resources and dependency origins.
 */
public final class QualificationPackageContents {
    private QualificationPackageContents() {}

    public static void verify(final QualifiedPackage payload) throws IOException {
        dependencies(payload);
        resources(payload);
        schema(payload);
    }

    private static void dependencies(final QualifiedPackage payload) throws IOException {
        final var lock =
                QualificationJson.read(payload.member("dependencies.json", "POLICY"), 65536);
        QualificationJson.fields(lock, "version", "scope", "dependencies");
        if (!"qualification-dependencies-v1".equals(lock.path("version").asText())) {
            throw new IllegalArgumentException("QUAL_DEPENDENCY_LOCK_VERSION");
        }
        QualificationJson.array(lock.path("dependencies"), 9, 9);
        final var bom = QualificationJson.read(payload.member("sbom.cdx.json", "SBOM"), 262144);
        final Map<String, JsonNode> components = new HashMap<>();
        for (final var component : bom.path("components")) {
            if (components.put(QualificationJson.text(component, "bom-ref", 200), component)
                    != null) {
                throw new IllegalArgumentException("QUAL_SBOM_COMPONENT_DUPLICATE");
            }
        }
        final var seen = new HashSet<String>();
        for (final var item : lock.path("dependencies")) {
            QualificationJson.fields(
                    item,
                    "group",
                    "name",
                    "version",
                    "type",
                    "file",
                    "sha256",
                    "size",
                    "license",
                    "origin",
                    "pom",
                    "pomSha256");
            final String type = QualificationJson.text(item, "type", 8);
            if (!Set.of("dll", "jar").contains(type)) {
                throw new IllegalArgumentException("QUAL_DEPENDENCY_TYPE");
            }
            final String file =
                    QualifiedPackage.safeMember(QualificationJson.text(item, "file", 120));
            final String member = (type.equals("dll") ? "native/" : "lib/") + file;
            final var packaged = payload.members().get(member);
            final var component = components.get(member);
            final String group = QualificationJson.text(item, "group", 100);
            final String name = QualificationJson.text(item, "name", 80);
            final String version = QualificationJson.text(item, "version", 40);
            final String origin =
                    "https://repo.maven.apache.org/maven2/"
                            + group.replace('.', '/')
                            + "/"
                            + name
                            + "/"
                            + version
                            + "/"
                            + file;
            if (packaged == null
                    || component == null
                    || !seen.add(member)
                    || !origin.equals(item.path("origin").asText())
                    || !packaged.sha256().equals(QualificationJson.digest(item, "sha256"))
                    || packaged.size() != QualificationJson.number(item, "size", 1, 33554432)
                    || !group.equals(component.path("group").asText())
                    || !name.equals(component.path("name").asText())
                    || !version.equals(component.path("version").asText())
                    || !("pkg:maven/" + group + "/" + name + "@" + version + "?type=" + type)
                            .equals(component.path("purl").asText())
                    || component.path("licenses").size() != 1
                    || !item.path("license").equals(component.path("licenses").get(0))
                    || component.path("externalReferences").size() != 1
                    || !origin.equals(
                            component.path("externalReferences").get(0).path("url").asText())
                    || !"distribution"
                            .equals(
                                    component
                                            .path("externalReferences")
                                            .get(0)
                                            .path("type")
                                            .asText())) {
                throw new IllegalArgumentException("QUAL_SBOM_LOCK_CORRESPONDENCE");
            }
            final String pom =
                    "licenses/"
                            + QualifiedPackage.safeMember(QualificationJson.text(item, "pom", 140));
            if (!QualificationJson.digest(item, "pomSha256")
                    .equals(QualificationJson.sha256(payload.member(pom, "LICENSE")))) {
                throw new IllegalArgumentException("QUAL_DEPENDENCY_POM_CORRESPONDENCE");
            }
        }
        if (!seen.equals(components.keySet())) {
            throw new IllegalArgumentException("QUAL_SBOM_COMPONENT_SET");
        }
        final var provenance =
                QualificationJson.read(payload.member("provenance.json", "PROVENANCE"), 8192);
        if (!payload.members()
                        .get("dependencies.json")
                        .sha256()
                        .equals(provenance.path("dependencyLockSha256").asText())
                || !payload.members()
                        .get("etl-dataexport-v2.jar")
                        .sha256()
                        .equals(
                                bom.path("metadata")
                                        .path("component")
                                        .path("hashes")
                                        .path(0)
                                        .path("content")
                                        .asText())) {
            throw new IllegalArgumentException("QUAL_PACKAGE_PROVENANCE_CORRESPONDENCE");
        }
    }

    private static void resources(final QualifiedPackage payload) throws IOException {
        final var index =
                QualificationJson.read(
                        payload.member("fixtures/fixture-index.json", "FIXTURE"), 65536);
        QualificationJson.fields(index, "version", "origin", "files");
        if (!"qualification-fixtures-v1".equals(index.path("version").asText())
                || !"SYNTHETIC_INPUTS_NOT_QUERY_OUTPUTS".equals(index.path("origin").asText())) {
            throw new IllegalArgumentException("QUAL_FIXTURE_INDEX_ORIGIN");
        }
        QualificationJson.array(index.path("files"), 9, 128);
        final var seen = new HashSet<String>();
        try (var jar =
                new JarFile(payload.member("etl-dataexport-v2.jar", "APPLICATION").toFile())) {
            for (final var item : index.path("files")) {
                QualificationJson.fields(item, "resource", "member", "sha256");
                final String resource =
                        QualifiedPackage.safeMember(QualificationJson.text(item, "resource", 180));
                final String member = QualificationJson.text(item, "member", 200);
                if (!member.equals("fixtures/" + resource)
                        || !seen.add(member)
                        || !QualificationJson.digest(item, "sha256")
                                .equals(
                                        QualificationJson.sha256(
                                                payload.member(member, "FIXTURE")))) {
                    throw new IllegalArgumentException("QUAL_FIXTURE_INDEX_CONTENT");
                }
                resource(jar, resource, payload.members().get(member).sha256());
            }
            for (final var name :
                    List.of(
                            "outputs.synthetic.json",
                            "location-hashes.synthetic.json",
                            "location-representative-hashes.synthetic.json")) {
                payload.member("oracles/" + name, "ORACLE");
                resource(
                        jar,
                        "qualification-laboratory/" + name,
                        payload.members().get("oracles/" + name).sha256());
            }
            resource(
                    jar,
                    "qualification-laboratory/physical-columns.v098.json",
                    QualificationJson.sha256(
                            payload.member("contracts/physical-columns.v098.json", "CONTRACT")));
            resource(
                    jar,
                    "qualification-laboratory/config.synthetic.json",
                    payload.members().get("config/config.synthetic.json").sha256());
        }
        final var expected = new HashSet<String>();
        payload.members().values().stream()
                .filter(item -> item.role().equals("FIXTURE"))
                .forEach(item -> expected.add(item.path()));
        expected.remove("fixtures/fixture-index.json");
        if (!seen.equals(expected)) {
            throw new IllegalArgumentException("QUAL_FIXTURE_MEMBER_SET");
        }
    }

    private static void resource(final JarFile jar, final String name, final String hash)
            throws IOException {
        final var entry = jar.getJarEntry(name);
        if (entry == null || entry.getSize() < 1 || entry.getSize() > 1048576) {
            throw new IllegalArgumentException("QUAL_PACKAGE_JAR_RESOURCE");
        }
        try (var input = jar.getInputStream(entry)) {
            if (!hash.equals(QualificationJson.sha256(input.readNBytes(1048577)))) {
                throw new IllegalArgumentException("QUAL_PACKAGE_JAR_RESOURCE_HASH");
            }
        }
    }

    private static void schema(final QualifiedPackage payload) throws IOException {
        final var index =
                QualificationJson.read(payload.member("schema/schema-index.json", "SCHEMA"), 65536);
        QualificationJson.fields(
                index,
                "version",
                "lastVersion",
                "installationAutomatic",
                "migrations",
                "baselineSha256");
        if (!"qualification-schema-v1".equals(index.path("version").asText())
                || QualificationJson.number(index, "lastVersion", 98, 98) != 98
                || QualificationJson.flag(index, "installationAutomatic")) {
            throw new IllegalArgumentException("QUAL_SCHEMA_INDEX_POLICY");
        }
        QualificationJson.array(index.path("migrations"), 98, 98);
        final var seen = new HashSet<String>();
        int version = 0;
        for (final var item : index.path("migrations")) {
            QualificationJson.fields(item, "path", "sha256");
            final String name = QualificationJson.text(item, "path", 200);
            if (!name.matches(
                            String.format(
                                    java.util.Locale.ROOT,
                                    "schema/migrations/V%03d__[a-z0-9_]+\\.sql",
                                    ++version))
                    || !seen.add(name)
                    || !QualificationJson.digest(item, "sha256")
                            .equals(QualificationJson.sha256(payload.member(name, "SCHEMA")))) {
                throw new IllegalArgumentException("QUAL_SCHEMA_INDEX_MEMBER");
            }
        }
        final String baseline = "schema/baseline/001_schema_foundation_baseline.sql";
        if (!QualificationJson.digest(index, "baselineSha256")
                .equals(QualificationJson.sha256(payload.member(baseline, "SCHEMA")))) {
            throw new IllegalArgumentException("QUAL_SCHEMA_BASELINE_HASH");
        }
        seen.add(baseline);
        seen.add("schema/schema-index.json");
        final var expected = new HashSet<String>();
        payload.members().values().stream()
                .filter(item -> item.role().equals("SCHEMA"))
                .forEach(item -> expected.add(item.path()));
        if (!seen.equals(expected)) {
            throw new IllegalArgumentException("QUAL_SCHEMA_MEMBER_SET");
        }
    }
}
