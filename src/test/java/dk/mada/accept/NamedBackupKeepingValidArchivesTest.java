package dk.mada.accept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.impl.output.DirectoryDeleter;
import dk.mada.backup.restore.RestoreScriptReader;
import dk.mada.backup.restore.RestoreScriptReader.DataCrypt;
import dk.mada.backup.restore.RestoreScriptReader.DataRootFile;
import dk.mada.backup.restore.RestoreScriptReader.RestoreScriptData;
import dk.mada.fixture.ExitHandlerFixture.TestFailedWithException;
import dk.mada.fixture.MakeBackup;
import dk.mada.fixture.TestDataPrepper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes a backup, tweaks subset of data folders, makes another backup. Ensures that only the changed data folders are
 * updated.
 */
@Tag("accept")
class NamedBackupKeepingValidArchivesTest {
    private static final Logger logger = LoggerFactory.getLogger(NamedBackupKeepingValidArchivesTest.class);
    private static final Path DUMMY_STATIC_FILE = Paths.get(".");

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
                .withMessageContaining("Validation of old backup failed");
    }

    /**
     * Tests that an existing target folder with unknown contents is not clobbered.
     */
    @Test
    void cannotWriteToExistingFolder() throws IOException, ArchiveException {
        MakeBackup.makeBackup(BackupOutputType.NUMBERED, true);

        assertThatExceptionOfType(TestFailedWithException.class)
                .isThrownBy(() -> MakeBackup.makeBackup(BackupOutputType.NAMED, false))
                .havingCause()
                .withMessageContaining("Will not create a named backup in folder with existing");
    }

    /**
     * Tests that an existing target folder with unknown contents is not clobbered.
     */
    @Test
    void cannotWriteToExistingNonNamedBakupSet() throws IOException {
        DirectoryDeleter.delete(TestDataPrepper.BACKUP_DEST_DIR);
        Files.createDirectories(TestDataPrepper.BACKUP_DEST_DIR);
        assertThatExceptionOfType(TestFailedWithException.class)
                .isThrownBy(() -> MakeBackup.makeBackup(BackupOutputType.NAMED, false))
                .havingCause()
                .withMessageContaining("No existing restore script, will not write to");
    }

    /**
     * Tests that an existing target folder with a differently named backup is not clobbered.
     */
    @Test
    void cannotWriteToAnotherNamedBackupFolder() throws IOException, ArchiveException {
        Path rs = MakeBackup.makeBackup(BackupOutputType.NAMED, true);

        String script = Files.readString(rs);
        String updatedScript = script.replaceAll("# @name:.*", "# @name: another-name");
        Files.writeString(rs, updatedScript);

        assertThatExceptionOfType(TestFailedWithException.class)
                .isThrownBy(() -> MakeBackup.makeBackup(BackupOutputType.NAMED, false))
                .havingCause()
                .withMessageContaining("Will not clobber existing named backup set 'another-name'");
    }

    /**
     * Tests that a new updated backup set will contain a clone of the old state in the .prev-sets/ directory.
     */
    @Test
    void backupCloneIsCreated() throws IOException, ArchiveException {
        Path restoreScriptFile = MakeBackup.makeBackup(BackupOutputType.NAMED, true);

        RestoreScriptReader reader = new RestoreScriptReader();
        RestoreScriptData originalSet = reader.readRestoreScriptData(restoreScriptFile);

        RestoreScriptData updatedSet = makeNewChangedBackup(reader);

        Path oldSetDir = updatedSet.location().resolve(".old-sets").resolve(updatedSet.time());
        Path oldSetRestoreScriptFile = oldSetDir.resolve(originalSet.name() + ".sh");

        RestoreScriptData oldSetData = reader.readRestoreScriptData(oldSetRestoreScriptFile);

        assertThat(oldSetData.name()).isEqualTo(originalSet.name());
        assertThat(oldSetData.time()).isEqualTo(originalSet.time());
        assertThat(oldSetData.filesV2()).isEqualTo(originalSet.filesV2());

        // The root files contain current file location, so strip that out first
        List<DataRootFile> oldRoots =
                oldSetData.rootFilesV2().stream().map(this::trimPath).toList();
        List<DataRootFile> originalRoots =
                originalSet.rootFilesV2().stream().map(this::trimPath).toList();

        assertThat(oldRoots).isEqualTo(originalRoots);
    }

    private DataRootFile trimPath(DataRootFile d) {
        DataCrypt c = d.crypt();
        return new DataRootFile(
                d.name(), d.isDirectory(), new DataCrypt(c.size(), c.xxh3(), c.md5(), DUMMY_STATIC_FILE), d.archive());
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

        RestoreScriptData updatedSet = makeNewChangedBackup(reader);

        List<DataCrypt> originalCrypts =
                originalSet.rootFilesV2().stream().map(DataRootFile::crypt).toList();
        List<DataCrypt> updatedCrypts =
                updatedSet.rootFilesV2().stream().map(DataRootFile::crypt).toList();

        logger.info("Original: {}", originalCrypts);
        logger.info("Updated: {}", updatedCrypts);

        List<DataCrypt> sharedCrypts =
                originalCrypts.stream().filter(updatedCrypts::contains).toList();
        List<String> cryptNamesOnlyInOriginalSet = originalCrypts.stream()
                .filter(c -> !sharedCrypts.contains(c))
                .map(c -> c.file().getFileName().toString())
                .toList();
        List<String> cryptNamesOnlyInUpdatedSet = updatedCrypts.stream()
                .filter(c -> !sharedCrypts.contains(c))
                .map(c -> c.file().getFileName().toString())
                .toList();

        // Note that dir-a appears in both sets - but with different content
        logger.info("Matching crypts: {}", sharedCrypts);
        assertThat(cryptNamesOnlyInOriginalSet)
                .containsExactlyInAnyOrder("dir-a.crypt", "dir-b.crypt", "file-tricky.tar.crypt");
        assertThat(cryptNamesOnlyInUpdatedSet)
                .containsExactlyInAnyOrder("dir-a.crypt", "extra-dir.crypt", "extra-file.crypt");

        // Check that deleted files are removed from the new backup set
        assertThat(updatedSet.location().resolve("dir-b.crypt")).doesNotExist();
        assertThat(updatedSet.location().resolve("file-tricky.tar.crypt")).doesNotExist();
    }

    private RestoreScriptData makeNewChangedBackup(RestoreScriptReader reader) throws IOException, ArchiveException {
        // Now make an updated backup, but change some of the src files
        Path updatedRestoreScriptFile = MakeBackup.makeBackup(BackupOutputType.NAMED, false, srcDir -> {
            Path extraFile = srcDir.resolve("extra-file");
            Files.writeString(extraFile, "extra file text");

            Path extraDir = srcDir.resolve("extra-dir");
            Files.createDirectories(extraDir);
            Files.createFile(extraDir.resolve("dummy-file"));

            Path changedDir = srcDir.resolve("dir-a");
            Files.createFile(changedDir.resolve("new-a-file"));

            Path deletedFile = srcDir.resolve("file-tricky.tar");
            Files.delete(deletedFile);

            Path deletedDir = srcDir.resolve("dir-b");
            DirectoryDeleter.delete(deletedDir);
        });

        return reader.readRestoreScriptData(updatedRestoreScriptFile);
    }

    private Path parentDir(Path restoreScript) {
        return Objects.requireNonNull(restoreScript.getParent(), "No parent for restore script?!");
    }
}
