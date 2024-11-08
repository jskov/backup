package dk.mada.accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.restore.RestoreExecutor;
import dk.mada.backup.restore.RestoreExecutor.Result;
import dk.mada.fixture.MakeBackup;
import dk.mada.fixture.TestCertificateInfo;

/**
 * Makes a backup, verifies expected checksums of the files.
 */
@Tag("accept")
class BackupChecksumTest {
    /**
     * (Middle) archive checksums should be unchanged over time, as long as the input (backup) files are not touched. I.e.
     * wall clock time when the backup is made should not affect checksums.
     *
     * The entire backup checksum should be stable over time.
     *
     * The backup clears user/group information in the archives, which affects the checksums. This is how apache-commons
     * worked until version 1.21.
     */
    @Test
    void archiveChecksumsWithoutUserGroupNumbered() throws IOException, ArchiveException {
        runTest(BackupOutputType.NUMBERED);
    }

    @Test
    void archiveChecksumsWithoutUserGroupNamed() throws IOException, ArchiveException {
        runTest(BackupOutputType.NAMED);
    }

    private void runTest(BackupOutputType outputType) throws IOException, ArchiveException {
        Path restoreScript = MakeBackup.makeBackup(outputType, true);
        Result res = runRestoreCmd(restoreScript, "info", "-a");

        assertThat(res.exitValue())
                .isZero();
        assertThat(res.output())
                .contains(
                        "dir-a.tar 9869af844d76a2db        2560",
                        "dir-b.tar 0ea598bbf9098835        2048",
                        "dir-c.tar da76d613f264ce2d        2560",
                        "dir-d with space.tar a63fea44fa74b2f5        1536",
                        "dir-deep.tar 6f41d839e51a4fe5        2048",
                        "dir-e.tar 448f9c1bd8c6073e        1536",
                        "dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.tar 732bc3c3aecb13cf        2560",
                        "dir-m-with-[brackets]-and-(parens)-dir.tar 43dccbcc5c9daa51        2560",
                        "file-root1.bin 2d06800538d394c2           0",
                        "file-root2 with space.bin 2d06800538d394c2           0");
    }

    private Result runRestoreCmd(Path script, String... args) {
        return RestoreExecutor.runRestoreScript(script, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES, args);
    }
}
