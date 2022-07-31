package dk.mada.backup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Get backup application version properties.
 */
public final class Version {
    /** The application properties. */
    private static Properties appProperties;

    private Version() { }

    /** {@return the version of the application} */
    public static String getBackupVersion() {
        return loadProperties().getProperty("version", "version-undefined");
    }

    /** {@return the build time of the application} */
    public static String getBuildTime() {
        return loadProperties().getProperty("builtOn", "build-time-undefined");
    }

    private static synchronized Properties loadProperties() {
        if (appProperties == null) {
            appProperties = new Properties();
            try (InputStream is = Version.class.getResourceAsStream("/backup-version.properties");
                    InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr)) {
    
                appProperties.load(br);
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to read application properties!", e);
            }
        }
        return appProperties;
    }
}
