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
public class Version {
    private static Properties appProperties = new Properties();

    private Version() {}

    /** {@return the version of the application} */
    public static String getBackupVersion() {
        readProperties();
        return appProperties.getProperty("version", "version-undefined");
    }

    /** {@return the build time of the application} */
    public static String getBuildTime() {
        readProperties();
        return appProperties.getProperty("builtOn", "build-time-undefined");
    }

    private static void readProperties() {
        try (InputStream is = Version.class.getResourceAsStream("/backup-version.properties");
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr)) {

            appProperties.load(br);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read application properties!", e);
        }
    }
}
