package dk.mada.accept;

import static org.assertj.core.api.Assertions.assertThat;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.restore.RestoreExecutor;
import dk.mada.backup.restore.RestoreExecutor.Result;
import dk.mada.fixture.InfoParser;
import dk.mada.fixture.InfoParser.Info;
import dk.mada.fixture.MakeBackup;
import dk.mada.fixture.TestCertificateInfo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Makes a backup, checks info in backup.
 */
@Tag("accept")
class BackupInfoNumberedTest {
    /** Minimal expected size of backup. */
    private static final long SIZE_LOWER_BOUND = 27000L;
    /** Maximal expected size of backup. */
    private static final long SIZE_UPPER_BOUND = 29000L;
    /** Restore script for created backup. */
    private static Path restoreScript;

    @BeforeAll
    static void makeBackup() throws IOException, ArchiveException {
        restoreScript = MakeBackup.makeBackup(BackupOutputType.NUMBERED, true);
    }

    /**
     * Tests that the backup information is included in the restore script.
     */
    @Test
    void backupContainsInfo() {
        Result res = runRestoreCmd("info");

        assertThat(res.exitValue()).isZero();
        assertThat(res.output())
                .contains(
                        "Backup 'test'",
                        "made with backup version",
                        "created on 20",
                        "original size 71 B",
                        "encrypted with key id " + TestCertificateInfo.TEST_RECIPIEND_KEY_ID.id());
    }

    /**
     * Tests that the backup information for crypted archives can be printed.
     *
     * Size is checked to be within a range since crypted data on Ubuntu (GitHub actions) seems to differ from data crypted
     * on Fedora (my dev box).
     */
    @Test
    void backupInfoCrypted() {
        Result res = runRestoreCmd("info", "-c");

        assertThat(res.exitValue()).isZero();

        List<Info> infos = new InfoParser().parse(res.output());
        assertThat(infos).hasSize(1).first().satisfies(i -> {
            assertThat(i.filename()).isEqualTo("test-01.crypt");
            assertThat(i.size()).isBetween(SIZE_LOWER_BOUND, SIZE_UPPER_BOUND);
        });
    }

    /**
     * Tests that the backup information for tar archives can be printed.
     */
    @Test
    void backupInfoTars() {
        Result res = runRestoreCmd("info", "-a");

        assertThat(res.exitValue()).isZero();
        assertThat(res.output())
                .containsPattern("dir-a.tar [0-9a-f]{16}        2560")
                .containsPattern("dir-deep.tar [0-9a-f]{16}        2048")
                .containsPattern("dir-m-with-.brackets.-and-.parens.-dir.tar [0-9a-f]{16}        2560");
    }

    /**
     * Tests that the backup information for the original files can be printed.
     */
    @Test
    void backupInfoFiles() {
        Result res = runRestoreCmd("info", "-f");

        assertThat(res.exitValue()).isZero();
        assertThat(res.output())
                .contains(
                        "dir-a/file-a1.bin 2d06800538d394c2           0",
                        "dir-e/file-e with space.bin 2d06800538d394c2           0");
    }

    private Result runRestoreCmd(String... args) {
        return runRestoreCmd(restoreScript, args);
    }

    private Result runRestoreCmd(Path script, String... args) {
        return RestoreExecutor.runRestoreScript(script, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES, args);
    }
}
