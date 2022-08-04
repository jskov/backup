package dk.mada.unit.arguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.api.BackupArguments;
import dk.mada.backup.cli.CliMain;
import dk.mada.fixture.TestCertificateInfo;
import dk.mada.fixture.TestDataPrepper;
import picocli.CommandLine;

/**
 * Source may affect name and destination.
 */
class NameAndDestinationTests {
    /** Directory to backup of. */
    private static Path srcDir;
    /** Target directory for test.*/
    private @TempDir Path targetDir;

    @BeforeAll
    static void prepSource() throws IOException, ArchiveException {
        srcDir = TestDataPrepper.prepareTestInputTree("simple-input-tree");
    }

    /**
     * If absolute paths given as source/target there will
     * be no change to them as part of parsing.
     *
     * @see sourceAndTargetMayBeRelative
     */
    @Test
    void absolutePathsOverrideChanges() throws IOException {
        
        Path absSrc = srcDir.toAbsolutePath();
        Path absTarget = targetDir.toAbsolutePath();
        BackupArguments args = runBackup(absSrc.toString(), absTarget.toString());
        
        assertThat(args.targetDir())
            .isEqualTo(absTarget);
        assertThat(args.sourceDir())
            .isEqualTo(absSrc);
    }

    /**
     * If source is relative the parent-dir path will
     * be used to extend the target dir and also for the
     * name.
     *
     * For example input 'music/A dst' should result
     * in backup name "music-A" (not "A") and target folder
     * "dst/music" (not "dst")
     */
    @Test
    @Disabled("FIXME: not done yet")
    void sourceAndTargetMayBeRelative() throws IOException {
        BackupArguments args = runBackup(srcDir.toAbsolutePath().toString(), targetDir.toAbsolutePath().toString());
        
        System.out.println("ARGS: " + args);
        
    }
    
    /**
     * Specifying a relative path as input (e.g. "./music/S") should
     * result in changes to name and destination directory.
     * Name should be "music-S" and destination should be targetDir/music
     */
    @Disabled("still not done")
    @Test
    void relativeSourceDirElementsShouldAffectNameAndTarget() {
        Path restoreScript = targetDir.resolve("test.sh");

        assertThatCode(() -> runBackup("-h"))
                .doesNotThrowAnyException();

        assertThat(restoreScript)
                .exists();
    }

    private BackupArguments runBackup(String... args) throws IOException {
        List<String> combinedArgs = new ArrayList<>();
        combinedArgs.addAll(List.of("--running-tests", "--gpg-homedir", TestCertificateInfo.ABS_TEST_GNUPG_HOME));
        combinedArgs.addAll(List.of(args));
        
        AtomicReference<BackupArguments> ref = new AtomicReference<>();
        
        new CommandLine(new CliMain(parsedArgs -> ref.set(parsedArgs)))
                .execute(combinedArgs.toArray(new String[combinedArgs.size()]));

        return ref.get();
    }
}
