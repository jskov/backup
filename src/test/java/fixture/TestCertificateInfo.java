package fixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Contains information about the no-password
 * test certificate in src/test/data/gpghome.
 */
public class TestCertificateInfo {
	private static final Path IMPORT_TEST_GNUPG_HOME = Paths.get("build/azure-gpg-home");
	private static final Path LOCAL_SOURCE_TEST_GNUPG_HOME = Paths.get("src/test/data/gpghome");
	private static final Path TEST_GNUPG_HOME = (isRunningOnAzure() ? IMPORT_TEST_GNUPG_HOME : LOCAL_SOURCE_TEST_GNUPG_HOME);
	public static final String ABS_TEST_GNUPG_HOME = TEST_GNUPG_HOME.toAbsolutePath().toString();
	public static final String TEST_RECIPIEND_KEY_ID = "281DE650E39B5DCA3E9D542092B7BAA1D6B4A52D";
	public static final Map<String, String> TEST_KEY_ENVIRONMENT_OVERRIDES = Map.of("GNUPGHOME", ABS_TEST_GNUPG_HOME);

	private TestCertificateInfo() {}
	
	private static boolean isRunningOnAzure() {
		return Files.exists(Paths.get("/etc/debian_version"));
	}
}
