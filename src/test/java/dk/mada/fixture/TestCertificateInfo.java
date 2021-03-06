package dk.mada.fixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Contains information about the no-password
 * test certificate in src/test/data/gpghome.
 */
public class TestCertificateInfo {
	private static final Path IMPORT_TEST_GNUPG_HOME = Paths.get("build/actions-gpg-home");
	private static final Path LOCAL_SOURCE_TEST_GNUPG_HOME = Paths.get("src/test/data/gpghome");
	private static final Path TEST_GNUPG_HOME = (isRunningOnGitHubActions() ? IMPORT_TEST_GNUPG_HOME : LOCAL_SOURCE_TEST_GNUPG_HOME);
	public static final String ABS_TEST_GNUPG_HOME = TEST_GNUPG_HOME.toAbsolutePath().toString();
	public static final String TEST_RECIPIEND_KEY_ID = /*KEYID*/"D44089983B1F25F4EC98CD26D1487E949A58FDAA"/*KEYID*/;
	public static final Map<String, String> TEST_KEY_ENVIRONMENT_OVERRIDES = Map.of("GNUPGHOME", ABS_TEST_GNUPG_HOME);

	private TestCertificateInfo() {}
	
	private static boolean isRunningOnGitHubActions() {
		return Files.exists(Paths.get("/etc/debian_version"));
	}

	public static Map<String, String> makeEnvOverrideForGnuPgpHome(Path home) {
		return Map.of("GNUPGHOME", home.toAbsolutePath().toString());
	}
}
