package dk.mada.accept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.impl.output.DirectoryDeleter;
import dk.mada.backup.restore.RestoreExecutor.Result;
import dk.mada.backup.restore.RestoreScriptReader;
import dk.mada.backup.restore.RestoreScriptReader.RestoreScriptData;
import dk.mada.fixture.ExitHandlerFixture.TestFailedWithException;
import dk.mada.fixture.MakeBackup;
import dk.mada.fixture.MakeRestore;

/**
 * Makes a backup, tweaks subset of data folders, makes another backup. Ensures that only the changed data folders are
 * updated.
 */
@Tag("accept")
class NamedBackupKeepingValidArchivesTest {

    /**
     * Tests that an existing backup's BROKEN encrypted files cause an abort when trying to update.
     */
    @Test
    void cannotUpdateBrokenBackup() throws IOException, ArchiveException {
        Path restoreScriptFile = MakeBackup.makeBackup(BackupOutputType.NAMED, true);

        Path aCryptFile = parentDir(restoreScriptFile).resolve("dir-tricky.tar.crypt");
        Files.writeString(aCryptFile, "invalid data");

        assertThatExceptionOfType(TestFailedWithException.class)
                .isThrownBy(() -> MakeBackup.makeBackup(BackupOutputType.NAMED, false))
                .havingCause()
                .withMessage("Validation of old backup failed");
    }

    /**
     * Tests that a new updated backup set will contain a clone of the old state in the .prev-sets/ directory.
     */
    @Test
    // TODO: check that the clone contains original files
    void backupCloneIsCreated() throws IOException, ArchiveException {
        fail();
    }

    /**
     * Tests that an existing backup's encrypted files are reused when possible.
     */
    @Test
    void backupContainsInfo() throws IOException, ArchiveException, InterruptedException {
        Path restoreScriptFile = MakeBackup.makeBackup(BackupOutputType.NAMED, true);

        // Unpack for access to archives
//        Path restoreDir = Paths.get("build/backup-restored");
//        DirectoryDeleter.delete(restoreDir);
//        Result res = MakeRestore.runRestoreCmd(restoreScriptFile, "unpack", "-a", restoreDir.toAbsolutePath().toString());
//        System.out.println(res.output());

        // TODO: change a file/folder

        System.out.println("\n\n===== Making update backup ====\n\n");

//        Thread.sleep(Duration.ofSeconds(61));
        
        Path updatedRestoreScriptFile = MakeBackup.makeBackup(BackupOutputType.NAMED, false);

        RestoreScriptReader reader = new RestoreScriptReader();
        RestoreScriptData originalSet = reader.readRestoreScriptData(restoreScriptFile);
        RestoreScriptData updatedSet = reader.readRestoreScriptData(updatedRestoreScriptFile);
        
        assertThat(originalSet.cryptsV2())
                .isEqualTo(updatedSet.cryptsV2());
    }

    // TODO: with added folder/file
    // TODO: with removed folder/file

    private Path parentDir(Path restoreScript) {
        return Objects.requireNonNull(restoreScript.getParent(), "No parent for restore script?!");
    }
}
