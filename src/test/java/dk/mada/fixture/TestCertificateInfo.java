package dk.mada.fixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import dk.mada.backup.types.GpgId;

/**
 * Contains information about the no-password test certificate in
 * src/test/data/gpghome.
 *
 * This can (only) by used for local development on Fedora.
 * But works with Eclipse.
 *
 * On GitHub actions the certificates are imported into a
 * separate GPG home to be writable/secure/compatible with
 * Ubuntu.
 */
public final class TestCertificateInfo {
    /** GPG home for GitHub Actions - depends on setup in build.gradle. */
    private static final Path IMPORT_TEST_GNUPG_HOME = Paths.get("build/actions-gpg-home");
    /** GPG home working on my development box. */
    private static final Path LOCAL_SOURCE_TEST_GNUPG_HOME = Paths.get("src/test/data/gpghome");
    /** The GPG home to use. */
    private static final Path TEST_GNUPG_HOME =
            isRunningOnGitHubActions() ? IMPORT_TEST_GNUPG_HOME : LOCAL_SOURCE_TEST_GNUPG_HOME;
    /** The absolute GPG home to use. */
    public static final String ABS_TEST_GNUPG_HOME = TEST_GNUPG_HOME.toAbsolutePath().toString();
    /** Key of the test certificate. */
    public static final GpgId TEST_RECIPIEND_KEY_ID = new GpgId(/* KEYID*/"BE21CC0432AD986F8B932A265C2E6527F2998EF7"/*KEYID */);
    /** Environment overrides necessary for the GPG process. */
    public static final Map<String, String> TEST_KEY_ENVIRONMENT_OVERRIDES = Map.of("GNUPGHOME", ABS_TEST_GNUPG_HOME);

    private TestCertificateInfo() { }

    private static boolean isRunningOnGitHubActions() {
        return Files.exists(Paths.get("/etc/debian_version"));
    }
}
