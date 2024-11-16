package dk.mada.accept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.restore.RestoreScriptReader;
import dk.mada.backup.restore.RestoreScriptReader.DataCryptV2;
import dk.mada.backup.restore.RestoreScriptReader.RestoreScriptData;
import dk.mada.fixture.ExitHandlerFixture.TestFailedWithException;
import dk.mada.fixture.MakeBackup;

/**
 * Makes a backup, tweaks subset of data folders, makes another backup. Ensures that only the changed data folders are
 * updated.
 */
@Tag("accept")
class NamedBackupKeepingValidArchivesTest {
    private static final Logger logger = LoggerFactory.getLogger(NamedBackupKeepingValidArchivesTest.class);

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
     *
     * Also tests that files are added/removed/updated as needed.
     */
    @Test
    void updatedBackupRetainsExistingEncryptedFiles() throws IOException, ArchiveException {
        Path restoreScriptFile = MakeBackup.makeBackup(BackupOutputType.NAMED, true);

        RestoreScriptReader reader = new RestoreScriptReader();
        RestoreScriptData originalSet = reader.readRestoreScriptData(restoreScriptFile);

        // Now make an updated backup, but change some of the src files
        Path updatedRestoreScriptFile = MakeBackup.makeBackup(BackupOutputType.NAMED, false, srcDir -> {
            Path extraFile = srcDir.resolve("extra-file");
            Files.writeString(extraFile, "extra file text");

            Path extraDir = srcDir.resolve("extra-dir");
            Files.createDirectories(extraDir);
            Files.createFile(extraDir.resolve("dummy-file"));

            Path existingDir = srcDir.resolve("dir-a");
            Files.createFile(existingDir.resolve("new-a-file"));

            Path existingFile = srcDir.resolve("file-tricky.tar");
            Files.delete(existingFile);

            logger.info("XXX Modified src directory {}", srcDir);
        });

        RestoreScriptData updatedSet = reader.readRestoreScriptData(updatedRestoreScriptFile);

        logger.info("Original: {}", originalSet.cryptsV2());
        logger.info("Updated: {}", updatedSet.cryptsV2());

        List<DataCryptV2> sharedCrypts = originalSet.cryptsV2().stream()
                .filter(updatedSet.cryptsV2()::contains)
                .toList();
        List<String> cryptNamesOnlyInOriginalSet = originalSet.cryptsV2().stream()
                .filter(c -> !sharedCrypts.contains(c))
                .map(c -> c.file().getFileName().toString())
                .toList();
        List<String> cryptNamesOnlyInUpdatedSet = updatedSet.cryptsV2().stream()
                .filter(c -> !sharedCrypts.contains(c))
                .map(c -> c.file().getFileName().toString())
                .toList();

        logger.info("Matching crypts: {}", sharedCrypts);
        assertThat(cryptNamesOnlyInOriginalSet)
                .containsExactlyInAnyOrder("dir-a.crypt", "file-tricky.tar.crypt");
        assertThat(cryptNamesOnlyInUpdatedSet)
                .containsExactlyInAnyOrder("dir-a.crypt", "extra-dir.crypt", "extra-file.crypt");
    }

    private Path parentDir(Path restoreScript) {
        return Objects.requireNonNull(restoreScript.getParent(), "No parent for restore script?!");
    }
}
