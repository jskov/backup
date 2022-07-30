package dk.mada.accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dk.mada.backup.restore.RestoreExecutor;
import dk.mada.backup.restore.RestoreExecutor.Result;
import dk.mada.fixture.DirectoryDeleter;
import dk.mada.fixture.MakeBackup;
import dk.mada.fixture.TestCertificateInfo;

/**
 * Makes a backup, runs multiple checks on the restore of this backup.
 */
class BackupVerificationTest {
    private static Path restoreScript;

    @BeforeAll
    static void makeBackup() throws IOException, ArchiveException {
        restoreScript = MakeBackup.makeBackup();
    }

    /**
     * Tests that the verification of the encrypted archive(s) works.
     */
    @Test
    void backupCryptFilesCanBeVerified() throws IOException, InterruptedException {
        Result res = runRestoreCmd("verify");

        assertThat(res.exitValue())
                .isZero();
        assertThat(res.output())
                .contains("(1/1) test-01.crypt... ok");
    }

    /**
     * (Middle) archive checksums should be unchanged over time, as long as the
     * input (backup) files are not touched. I.e. wall clock time when the backup is
     * made should not affect checksums.
     * 
     * The entire backup checksum should be stable over time.
     */
    @Test
    void archiveChecksumsStableOverTime() throws IOException, InterruptedException {
        Result res = runRestoreCmd("info", "-a");

        assertThat(res.exitValue())
                .isZero();
        assertThat(res.output())
                .contains(
                        "dir-a.tar e42fa7a5806b41d4e1646ec1885e1f43bdbd9488465fa7022c1aa541ead9348f        2560",
                        "dir-b.tar 628b2ef22626e6a2d74c4bf441cf6394d5db0bf149a4a98ee048b51d9ce69374        2048");
    }

    /**
     * Encrypted archive checksums is time-dependent. But content is constant.
     */
    @Test
    void cryptContentStableOverTime() throws IOException, InterruptedException {
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
    void backupArchivesCanBeRestored() throws IOException, InterruptedException {
        Path restoreDir = Paths.get("build/backup-restored");
        DirectoryDeleter.delete(restoreDir);

        Result res = runRestoreCmd("unpack", "-a", restoreDir.toAbsolutePath().toString());

        assertThat(res.exitValue())
                .isZero();
        assertThat(res.output())
                .contains(" - (1/9) dir-a.tar... ok",
                        " - (2/9) dir-b.tar... ok",
                        " - (3/9) dir-c.tar... ok",
                        " - (4/9) dir-d with space.tar... ok",
                        " - (5/9) dir-e.tar... ok",
                        " - (6/9) dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.tar... ok",
                        " - (7/9) dir-m-with-[brackets]-and-(parens)-dir.tar... ok",
                        " - (8/9) file-root1.bin... ok",
                        " - (9/9) file-root2 with space.bin... ok",
                        "Success!");
    }

    /**
     * Tests that the full backup can be decrypted and verified.
     */
    @Test
    void backupFilesCanBeRestored() throws IOException, InterruptedException {
        Path restoreDir = Paths.get("build/backup-restored");
        DirectoryDeleter.delete(restoreDir);

        Result res = runRestoreCmd("unpack", restoreDir.toAbsolutePath().toString());

        assertThat(res.output())
                .contains(" - (1/9) dir-a/file-a1.bin... ok",
                        " - (2/9) dir-a/file-a2.bin... ok",
                        " - (3/9) dir-b/file-b1.bin... ok",
                        " - (4/9) dir-c/file-c-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.bin... ok",
                        " - (5/9) dir-d with space/file-d1.bin... ok",
                        " - (6/9) dir-e/file-e with space.bin... ok",
                        " - (7/9) dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890/file-long1.bin... ok",
                        " - (8/9) dir-m-with-[brackets]-and-(parens)-dir/empty-file... ok",
                        " - (9/9) dir-m-with-[brackets]-and-(parens)-dir/text-file.txt... ok",
                        "Success!");

        assertThat(res.exitValue())
                .isZero();
    }

    /**
     * Tests that the files in the archive can be verified by streaming.
     */
    @Test
    void backupFilesCanBeVerifiedByStream() throws IOException, InterruptedException {
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
    void brokenBackupFilesCanBeFoundByStreamVerifier() throws IOException, InterruptedException {
        // replace last 4 chars of checksum with "dead"
        Path badRestoreScript = restoreScript.getParent().resolve("bad.sh");
        String withBrokenChecksum = Files.readAllLines(restoreScript).stream()
                .map(s -> s.replaceAll("....,dir-b/file-b1.bin", "dead,dir-b/file-b1.bin"))
                .collect(Collectors.joining("\n"));
        Files.writeString(badRestoreScript, withBrokenChecksum);

        Result res = runRestoreCmd(badRestoreScript, "verify", "-s");

        assertThat(res.output())
                .contains("Did not find matching checksum for file 'dir-b/file-b1.bin'");

        assertThat(res.exitValue())
                .isNotZero();
    }

    private Result runRestoreCmd(String... args) throws IOException {
        return runRestoreCmd(restoreScript, args);
    }

    private Result runRestoreCmd(Path script, String... args) throws IOException {
        return RestoreExecutor.runRestoreScript(script, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES, args);
    }
}
