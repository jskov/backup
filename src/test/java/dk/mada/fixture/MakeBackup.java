package dk.mada.fixture;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveException;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.cli.CliMain;
import dk.mada.backup.impl.output.DirectoryDeleter;
import dk.mada.logging.LoggerConfig;

/**
 * Makes a backup from canned input. Mostly used for testing the resulting restore script.
 */
public final class MakeBackup {
    private MakeBackup() {
    }

    /**
     * Create backup from test data.
     *
     * @return restore script
     */
    public static Path makeBackup(BackupOutputType outputType, boolean cleanDestination) throws IOException, ArchiveException {
        LoggerConfig.loadConfig("/logging-test.properties");

        Path srcDir = TestDataPrepper.prepareTestInputTree("simple-input-tree");
        Path targetDir = Paths.get("build/backup-dest").toAbsolutePath();
        Path repositoryDir = targetDir.resolve("_repository");

        if (cleanDestination) {
            DirectoryDeleter.delete(targetDir);
        }

        Path restoreScript = targetDir.resolve("test.sh");

        List<String> args = new ArrayList<>();
        args.addAll(List.of(
                "-n", "test",
                "--repository", repositoryDir.toString(),
                "-r", TestCertificateInfo.TEST_RECIPIEND_KEY_ID.id(),
                "--gpg-homedir", TestCertificateInfo.ABS_TEST_GNUPG_HOME));

        args.addAll(List.of(
                CliMain.OPT_MAX_CONTAINER_SIZE, "20m",
                CliMain.OPT_MAX_ROOT_DIR_SIZE, "10m"));

        if (outputType == BackupOutputType.NAMED) {
            args.add("--by-name");
        }

        args.add(srcDir.toString());
        args.add(targetDir.toString());

        CliMain.main(ExitHandlerFixture.exitForTesting(), args.toArray(new String[args.size()]));

        return restoreScript;
    }
}
