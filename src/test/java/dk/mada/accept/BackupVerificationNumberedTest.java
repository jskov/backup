package dk.mada.accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.restore.RestoreExecutor.Result;
import dk.mada.fixture.DirectoryDeleter;
import dk.mada.fixture.MakeBackup;
import dk.mada.fixture.MakeRestore;

/**
 * Makes a backup, runs multiple checks on the restore of this backup.
 */
@Tag("accept")
class BackupVerificationNumberedTest {
    /** Restore script for created backup. */
    private static Path restoreScript;

    @BeforeAll
    static void makeBackup() throws IOException, ArchiveException {
        restoreScript = MakeBackup.makeBackup(BackupOutputType.NUMBERED, true);
    }

    /**
     * Tests that the verification of the encrypted archive(s) works.
     */
    @Test
    void backupCryptFilesCanBeVerified() {
        Result res = runRestoreCmd("verify");

        assertThat(res.exitValue())
                .isZero();
        assertThat(res.output())
                .contains("(1/1) test-01.crypt... ok");
    }

    /**
     * Encrypted archive checksums is time-dependent. But content is constant.
     */
    @Test
    void cryptContentStableOverTime() {
        Result res = runRestoreCmd("info", "-c");

        assertThat(res.exitValue())
                .isZero();
        assertThat(res.output())
                .contains("test-01.crypt");
    }

    /**
     * Tests that the contained archives can be decrypted and verified.
     */
    @Test
    void backupArchivesCanBeRestored() throws IOException {
        Path restoreDir = Paths.get("build/backup-restored");
        DirectoryDeleter.delete(restoreDir);

        Result res = runRestoreCmd("unpack", "-a", restoreDir.toAbsolutePath().toString());

        assertThat(res.output())
                .contains(" - (1/10) dir-a.tar... ok",
                        " - (2/10) dir-b.tar... ok",
                        " - (3/10) dir-c.tar... ok",
                        " - (4/10) dir-d with space.tar... ok",
                        " - (5/10) dir-deep.tar... ok",
                        " - (6/10) dir-e.tar... ok",
                        " - (7/10) dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.tar... ok", // NOSONAR
                        " - (8/10) dir-m-with-[brackets]-and-(parens)-dir.tar... ok",
                        " - (9/10) file-root1.bin... ok",
                        " - (10/10) file-root2 with space.bin... ok",
                        "Success!");
        assertThat(res.exitValue())
            .isZero();
    }

    /**
     * Tests that the full backup can be decrypted and verified.
     */
    @Test
    void backupFilesCanBeRestored() throws IOException {
        Path restoreDir = Paths.get("build/backup-restored");
        DirectoryDeleter.delete(restoreDir);

        Result res = runRestoreCmd("unpack", restoreDir.toAbsolutePath().toString());

        assertThat(res.output())
                .contains(" - (1/11) dir-a/file-a1.bin... ok",
                        " - (2/11) dir-a/file-a2.bin... ok",
                        " - (3/11) dir-b/file-b1.bin... ok",
                        " - (4/11) dir-c/file-c-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.bin... ok", // NOSONAR
                        " - (5/11) dir-d with space/file-d1.bin... ok",
                        " - (6/11) dir-deep/dir-sub-a/file-deep-a.bin... ok",
                        " - (7/11) dir-deep/dir-sub-b/file-deep-b.bin... ok",
                        " - (8/11) dir-e/file-e with space.bin... ok",
                        " - (9/11) dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890/file-long1.bin... ok", // NOSONAR
                        " - (10/11) dir-m-with-[brackets]-and-(parens)-dir/empty-file... ok",
                        " - (11/11) dir-m-with-[brackets]-and-(parens)-dir/text-file.txt... ok",
                        "Success!");

        assertThat(res.exitValue())
                .isZero();
    }

    /**
     * Tests that the files in the archive can be verified by streaming.
     */
    @Test
    void backupFilesCanBeVerifiedByStream() {
        Result res = runRestoreCmd("verify", "-s");

        assertThat(res.output())
                .contains("All files verified ok.");

        assertThat(res.exitValue())
                .isZero();
    }

    /**
     * Tests that the a faulty file in the backup set can be found by the streaming
     * verifier.
     *
     * Done by breaking the checksum in the restore script before running verify.
     */
    @Test
    void brokenBackupFilesCanBeFoundByStreamVerifier() throws IOException {
        // replace last 4 chars of checksum with "dead"
        Path badRestoreScript = restoreScript.getParent().resolve("bad.sh");
        String withBrokenChecksum = Files.readAllLines(restoreScript).stream()
                .map(s -> s.replaceAll("....,dir-b/file-b1.bin", "dead,dir-b/file-b1.bin"))
                .collect(Collectors.joining("\n"));
        Files.writeString(badRestoreScript, withBrokenChecksum);

        Result res = MakeRestore.runRestoreCmd(badRestoreScript, "verify", "-s");

        assertThat(res.output())
                .contains("Did not find matching checksum for file 'dir-b/file-b1.bin'");

        assertThat(res.exitValue())
                .isNotZero();
    }

    @Test
    void restoreScriptIsWrittenToRepository() {
        assertThat(restoreScript)
            .hasSameTextualContentAs(restoreScript.getParent().resolve("_repository/test.sh"));
    }

    private Result runRestoreCmd(String... args) {
        return MakeRestore.runRestoreCmd(restoreScript, args);
    }
}
