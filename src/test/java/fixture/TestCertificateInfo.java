package fixture;

import java.nio.file.Paths;
import java.util.Map;

/**
 * Contains information about the no-password
 * test certificate in src/test/data/gpghome.
 */
public class TestCertificateInfo {
	public static final String ABS_TEST_GNUPG_HOME = Paths.get("src/test/data/gpghome").toAbsolutePath().toString();
	public static final String TEST_RECIPIEND_KEY_ID = "281DE650E39B5DCA3E9D542092B7BAA1D6B4A52D";
	public static final Map<String, String> TEST_KEY_ENVIRONMENT_OVERRIDES = Map.of("GNUPGHOME", ABS_TEST_GNUPG_HOME);

	private TestCertificateInfo() {}
}
