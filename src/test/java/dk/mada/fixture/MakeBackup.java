package dk.mada.fixture;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
        return makeBackup(false);
    }

    private static Path makeBackup(boolean clearUserGroup) throws IOException, ArchiveException {
        Path srcDir = TestDataPrepper.prepareTestInputTree("simple-input-tree");
        Path targetDir = Paths.get("build/backup-dest").toAbsolutePath();
        Path repositoryDir = targetDir.resolve("_repository");

        DirectoryDeleter.delete(targetDir);

        Path restoreScript = targetDir.resolve("test.sh");

        List<String> args = List.of(
            "--running-tests",
            "-n", "test",
            "--repository", repositoryDir.toString(),
            "-r", TestCertificateInfo.TEST_RECIPIEND_KEY_ID.id(),
            "--gpg-homedir", TestCertificateInfo.ABS_TEST_GNUPG_HOME,
            srcDir.toString(),
            targetDir.toString()
            );

        CliMain.main(args.toArray(new String[args.size()]));

        return restoreScript;
    }

}
