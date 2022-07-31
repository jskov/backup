package dk.mada.fixture;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.compress.archivers.ArchiveException;

import dk.mada.backup.cli.CliMain;

/**
 * Makes a backup from canned input. Mostly used for testing the
 * resulting restore script.
 */
public final class MakeBackup {
    private MakeBackup() { }

    /**
     * Create backup from test data.
     *
     * @return restore script
     */
    public static Path makeBackup() throws IOException, ArchiveException {
        Path srcDir = TestDataPrepper.prepareTestInputTree("simple-input-tree");
        Path targetDir = Paths.get("build/backup-dest");

        DirectoryDeleter.delete(targetDir);

        Path restoreScript = targetDir.resolve("test.sh");
        CliMain.main(new String[] {
                "--running-tests",
                "-n", "test",
                "-r", TestCertificateInfo.TEST_RECIPIEND_KEY_ID.id(),
                "--gpg-homedir", TestCertificateInfo.ABS_TEST_GNUPG_HOME,
                srcDir.toAbsolutePath().toString(),
                targetDir.toAbsolutePath().toString()
        });

        return restoreScript;
    }

}
