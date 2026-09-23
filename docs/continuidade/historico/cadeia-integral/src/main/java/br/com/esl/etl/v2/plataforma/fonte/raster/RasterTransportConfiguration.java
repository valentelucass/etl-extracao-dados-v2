package br.com.esl.etl.v2.plataforma.fonte.raster;

/** Dormant by default. Explicit enablement validates credentials before any transport I/O. */
public final class RasterTransportConfiguration {
    private final boolean enabled;
    private final String login;
    private final String password;

    public RasterTransportConfiguration(
            final boolean enabled, final String login, final String password) {
        if (enabled
                && (login == null || login.isBlank() || password == null || password.isBlank())) {
            throw new IllegalArgumentException("RAS_CREDENTIAL_REQUIRED");
        }
        this.enabled = enabled;
        this.login = login;
        this.password = password;
    }

    public static RasterTransportConfiguration disabled() {
        return new RasterTransportConfiguration(false, null, null);
    }

    public boolean enabled() {
        return enabled;
    }

    boolean syntheticLoopbackCredentials() {
        return "synthetic-raster-user".equals(login)
                && "synthetic-raster-password".equals(password);
    }

    @Override
    public String toString() {
        return "RasterTransportConfiguration[enabled=" + enabled + ",credentials=REDACTED]";
    }
}
