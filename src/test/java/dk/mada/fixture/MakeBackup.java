package dk.mada.fixture;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.cli.CliMain;
import dk.mada.backup.impl.output.DirectoryDeleter;
import dk.mada.logging.LoggerConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.compress.archivers.ArchiveException;
import org.jspecify.annotations.Nullable;

/**
 * Makes a backup from canned input. Mostly used for testing the resulting restore script.
 */
public final class MakeBackup {
    private MakeBackup() {}

    /**
     * Create backup from test data.
     *
     * @param outputType       the output type
     * @param cleanDestination flag to enable cleaning of the output directory before creating backup
     * @return restore script
     */
    public static Path makeBackup(BackupOutputType outputType, boolean cleanDestination)
            throws IOException, ArchiveException {
        return makeBackup(outputType, cleanDestination, null);
    }

    /**
     * Create backup from test data.
     *
     * @param outputType       the output type
     * @param cleanDestination flag to enable cleaning of the output directory before creating backup
     * @param srcModifier      consumer able to modify the src directory before the backup is created
     * @return restore script
     */
    public static Path makeBackup(
            BackupOutputType outputType, boolean cleanDestination, @Nullable SrcTreeModifier srcModifier)
            throws IOException, ArchiveException {
        LoggerConfig.loadConfig("/logging-test.properties");

        Path srcDir = TestDataPrepper.prepareTestInputTree("simple-input-tree");

        if (srcModifier != null) {
            srcModifier.accept(srcDir);
        }

        Path targetDir = TestDataPrepper.BACKUP_DEST_DIR.toAbsolutePath();
        Path repositoryDir = targetDir.resolve("_repository");

        if (cleanDestination) {
            DirectoryDeleter.delete(targetDir);
        }

        Path restoreScript = targetDir.resolve("test.sh");

        List<String> args = new ArrayList<>();
        args.addAll(List.of(
                "-n",
                "test",
                "--repository",
                repositoryDir.toString(),
                "-r",
                TestCertificateInfo.TEST_RECIPIEND_KEY_ID.id(),
                "--gpg-homedir",
                TestCertificateInfo.ABS_TEST_GNUPG_HOME));

        args.addAll(List.of(CliMain.OPT_MAX_ROOT_ELEMENT_SIZE, "10m"));

        if (outputType == BackupOutputType.NAMED) {
            args.add("--by-name");
        }

        args.add(srcDir.toString());
        args.add(targetDir.toString());

        CliMain.main(ExitHandlerFixture.exitForTesting(), args.toArray(new String[args.size()]));

        return restoreScript;
    }

    public interface SrcTreeModifier {
        void accept(Path srcDir) throws IOException;
    }
}
