package dk.mada.backup;

/**
 * Get backup application version from manifest.
 */
public class Version {
	public static String getBackupVersion() {
		String v = Version.class.getPackage().getImplementationVersion();
		if (v == null) {
			v = "undef";
		}
		return v;
	}
}
