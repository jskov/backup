package dk.mada.backup;

/**
 * Get backup application version from manifest.
 */
public class Version {
	public static String getBackupVersion() {
		return Version.class.getPackage().getImplementationVersion();
	}
}
