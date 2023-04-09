package dk.mada.backup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

/**
 * Get backup application version properties.
 */
public final class Version implements IVersionProvider {
    /** The application properties. */
    private static Properties appProperties = loadProperties();

    private Version() {
    }

    @Override
    public String[] getVersion() {
        return new String[] { "Version " + getBackupVersion(), "Built " + getBuildTime() };
    }

    /** {@return the version of the application} */
    public static String getBackupVersion() {
        return appProperties.getProperty("version", "version-undefined");
    }

    /** {@return the build time of the application} */
    public static String getBuildTime() {
        return appProperties.getProperty("builtOn", "build-time-undefined");
    }

    private static synchronized Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = Version.class.getResourceAsStream("/backup-version.properties");
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr)) {

            props.load(br);
            return props;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read application properties!", e);
        }
    }
}
