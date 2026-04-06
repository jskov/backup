package dk.mada.accept.restore;

import static org.assertj.core.api.Assertions.assertThat;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.restore.java.Restore;
import dk.mada.fixture.LoggerCapture;
import dk.mada.fixture.MakeBackup;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.Test;

/**
 * Test of the info command.
 */
class CmdInfoTest {
    /**
     * Tests that the info command prints information about the backup.
     */
    @Test
    void canShowInfoFromBackupSet() throws ArchiveException, IOException {
        Path bs = MakeBackup.makeBackup(BackupOutputType.NAMED, true);

        LoggerCapture.clear();
        Restore.mainReturn("info", "--full", "-b", bs.toString());
        String txt = LoggerCapture.getCaptured();

        assertThat(txt)
                .contains("Backup 'test'")
                .contains("made with backup version ")
                .contains("encrypted with key id ")
                .contains("Crypts (12)")
                .contains("Archives (12)")
                .contains("9869af844d76a2db       2560 ./dir-a.tar")
                .contains("Files (15)")
                .contains("2d06800538d394c2          0 dir-e/file-e with space.bin");
    }
}
