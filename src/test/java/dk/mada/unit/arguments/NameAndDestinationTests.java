package dk.mada.unit.arguments;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.api.BackupArguments;
import dk.mada.backup.cli.CliMain;
import dk.mada.backup.cli.DefaultArgs;
import dk.mada.backup.cli.EnvironmentInputs;
import dk.mada.fixture.TestCertificateInfo;
import dk.mada.fixture.TestDataPrepper;
import picocli.CommandLine;

/**
 * Source may affect name and destination.
 */
class NameAndDestinationTests {
    /** Directory to backup of. */
    private static Path srcDir;
    /** Environment inputs with CWD at root of srcDir. */
    private static EnvironmentInputs envAtRootOfSrc;
    /** Target directory for test.*/
    private @TempDir Path targetDir;

    @BeforeAll
    static void prepSource() throws IOException, ArchiveException {
        srcDir = TestDataPrepper.prepareTestInputTree("simple-input-tree").toAbsolutePath();

        envAtRootOfSrc = new EnvironmentInputs() {
            public Path getCurrentWorkingDirectory() {
                return srcDir;
            }
        };
    }

    /**
     * Source and target paths are relative to CWD.
     */
    @Test
    void sourceAndTargetDirsAreRelativeToCurrentDir() throws IOException {
        BackupArguments args = runBackup("dir-a", "output-test");

        assertThat(args.sourceDir())
            .isEqualTo(srcDir.resolve("dir-a"));
        assertThat(args.targetDir())
            .isEqualTo(srcDir.resolve("output-test"));
    }

    /**
     * Unnamed source input gets translated to actual directory
     * name.
     */
    @Test
    void translatesDotSrcDir() throws IOException {
        BackupArguments args = runBackup(".", targetDir.toString());

        assertThat(args.name())
            .isEqualTo("simple-input-tree");
        assertThat(args.sourceDir())
            .isEqualTo(srcDir.toAbsolutePath());
        assertThat(args.targetDir())
            .isEqualTo(targetDir);
    }

    /**
     * If absolute paths given as source/target there will
     * be no change to them as part of parsing.
     *
     * @see sourceAndTargetMayBeRelative
     */
    @Test
    void absolutePathsOverrideChanges() throws IOException {
        BackupArguments args = runBackup(srcDir.toString(), targetDir.toString());

        assertThat(args.name())
            .isEqualTo("simple-input-tree");
        assertThat(args.sourceDir())
            .isEqualTo(srcDir);
        assertThat(args.targetDir())
            .isEqualTo(targetDir);
    }

    /**
     * If source is relative the (source) parent-dir path will
     * be used to extend the target dir and also for the
     * name.
     *
     * For example input 'music/A dst' should result
     * in backup name "music-A" (not "A") and target folder
     * "dst/music" (not "dst")
     */
    @Test
    void sourceAndTargetMayBeRelative() throws IOException {
        BackupArguments args = runBackup("./dir-deep/dir-sub-a", targetDir.toString());

        assertThat(args.name())
            .isEqualTo("dir-deep-dir-sub-a");
        assertThat(args.targetDir())
            .isEqualTo(targetDir.resolve("dir-deep"));
    }

    private BackupArguments runBackup(String... args) throws IOException {
        List<String> combinedArgs = new ArrayList<>();
        combinedArgs.addAll(List.of("--running-tests",
                "-r", TestCertificateInfo.TEST_RECIPIEND_KEY_ID.id(),
                "--gpg-homedir", TestCertificateInfo.ABS_TEST_GNUPG_HOME));
        combinedArgs.addAll(List.of(args));

        AtomicReference<BackupArguments> ref = new AtomicReference<>();

        new CommandLine(new CliMain(envAtRootOfSrc, ref::set))
                .setDefaultValueProvider(new DefaultArgs(envAtRootOfSrc))
                .execute(combinedArgs.toArray(new String[combinedArgs.size()]));

        BackupArguments returned = ref.get();
        assertThat(returned)
            .withFailMessage("Failed processing arguments?!")
            .isNotNull();

        return returned;
    }
}
