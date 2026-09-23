package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Authors test-only fixtures against the worker JAR without weakening its runtime binding. */
final class PackagedFixtureRuntime implements AutoCloseable {
    private final URLClassLoader loader;

    PackagedFixtureRuntime(final Path application) throws Exception {
        this(application, true);
    }

    static PackagedFixtureRuntime exploded() throws Exception {
        return new PackagedFixtureRuntime(Path.of("target/classes").toAbsolutePath(), false);
    }

    private PackagedFixtureRuntime(final Path application, final boolean packaged)
            throws Exception {
        if (packaged) {
            QualificationJson.regular(application);
        } else if (!Files.isDirectory(application, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("FIXTURE_EXPLODED_DIRECTORY_REQUIRED");
        }
        final var urls = new ArrayList<URL>();
        urls.add(application.toUri().toURL());
        urls.add(PackagedFixtureRuntime.class.getProtectionDomain().getCodeSource().getLocation());
        for (final Class<?> assertion :
                java.util.List.of(
                        org.junit.jupiter.api.Assertions.class,
                        org.junit.platform.commons.util.Preconditions.class,
                        org.opentest4j.AssertionFailedError.class,
                        org.apiguardian.api.API.class)) {
            urls.add(assertion.getProtectionDomain().getCodeSource().getLocation());
        }
        try (var libraries = Files.list(application.toAbsolutePath().getParent().resolve("lib"))) {
            for (final Path library :
                    libraries.filter(path -> path.toString().endsWith(".jar")).sorted().toList()) {
                QualificationJson.regular(library);
                urls.add(library.toUri().toURL());
            }
        }
        // Platform parent supplies java.sql, but cannot leak Maven's exploded application classes.
        loader = new TestRuntimeLoader(urls.toArray(URL[]::new));
        try {
            final var scenario = loader.loadClass(LocalArtifactScenario.class.getName());
            final Path loaded =
                    Path.of(scenario.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isSameFile(application, loaded)) {
                throw new IllegalArgumentException("FIXTURE_RUNTIME_NOT_PACKAGED");
            }
        } catch (final Exception failure) {
            loader.close();
            throw failure;
        }
    }

    Path location() throws Exception {
        return Path.of(
                loader.loadClass(LocalArtifactScenario.class.getName())
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
    }

    String fingerprint() throws Exception {
        try {
            return (String)
                    loader.loadClass(LocalArtifactScenario.class.getName())
                            .getMethod("runtimeFingerprint")
                            .invoke(null);
        } catch (final InvocationTargetException failure) {
            rethrow(failure);
            throw new AssertionError("UNREACHABLE");
        }
    }

    void author(final Path directory) throws Exception {
        try {
            loader.loadClass(IntegralCampaignPackageFixtures.class.getName())
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[] {directory.toString()});
        } catch (final InvocationTargetException failure) {
            rethrow(failure);
        }
    }

    void verifyOracle(final Path input, final Path oracle) throws Exception {
        final var token =
                loader.loadClass("br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken");
        try {
            loader.loadClass(LocalArtifactScenario.class.getName())
                    .getConstructor(Path.class, Path.class, token)
                    .newInstance(input, oracle, token.getMethod("none").invoke(null));
        } catch (final InvocationTargetException failure) {
            rethrow(failure);
        }
    }

    void verifySequence(final Path sequence) throws Exception {
        final var token =
                loader.loadClass("br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken");
        try {
            final var type = loader.loadClass(LocalArtifactSequence.class.getName());
            final Object none = token.getMethod("none").invoke(null);
            final Object instance =
                    type.getConstructor(Path.class, token).newInstance(sequence, none);
            type.getMethod("verifyFiles", token).invoke(instance, none);
        } catch (final InvocationTargetException failure) {
            rethrow(failure);
        }
    }

    static boolean runIfExploded(final Class<?> suite, final String method) throws Exception {
        final Path location =
                Path.of(
                        LocalArtifactScenario.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        if (Files.isRegularFile(location)) {
            return false;
        }
        try (var runtime =
                new PackagedFixtureRuntime(
                        Path.of("target/etl-dataexport-v2.jar").toAbsolutePath())) {
            runtime.invokeTest(suite, method);
        }
        return true;
    }

    void invokeTest(final Class<?> suite, final String method) throws Exception {
        final var type = loader.loadClass(suite.getName());
        final var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        final var test = type.getDeclaredMethod(method);
        test.setAccessible(true);
        try {
            test.invoke(constructor.newInstance());
        } catch (final InvocationTargetException failure) {
            rethrow(failure);
        }
    }

    private static void rethrow(final InvocationTargetException failure) throws Exception {
        if (failure.getCause() instanceof Exception cause) {
            throw cause;
        }
        if (failure.getCause() instanceof Error cause) {
            throw cause;
        }
        throw failure;
    }

    @Override
    public void close() throws java.io.IOException {
        loader.close();
    }

    private static final class TestRuntimeLoader extends URLClassLoader {
        private TestRuntimeLoader(final URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {
            // Other ITs already own integrated-auth native loading in this test JVM.
            if (name.startsWith("com.microsoft.sqlserver.jdbc.")) {
                return com.microsoft.sqlserver.jdbc.SQLServerDataSource.class
                        .getClassLoader()
                        .loadClass(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
