package dk.mada.accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.impl.output.DirectoryDeleter;
import dk.mada.backup.restore.RestoreExecutor.Result;
import dk.mada.fixture.MakeBackup;
import dk.mada.fixture.MakeRestore;

/**
 * Makes a backup, runs multiple checks on the restore of this backup.
 */
@Tag("accept")
class BackupVerificationNamedTest {
    /** Restore script for created backup. */
    private static Path restoreScript;
    /** Backup destination folder. */
    private static Path backupDestination;

    @BeforeAll
    static void makeBackup() throws IOException, ArchiveException {
        restoreScript = MakeBackup.makeBackup(BackupOutputType.NAMED, true);
        backupDestination = Objects.requireNonNull(restoreScript.getParent(), "No parent for restore script?!");
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
                .contains("(1/12) dir-a.crypt... ok")
                .contains("(2/12) dir-b.crypt... ok")
                .contains("(3/12) dir-c.crypt... ok")
                .contains("(4/12) dir-d_with_space.crypt... ok")
                .contains("(5/12) dir-deep.crypt... ok")
                .contains("(6/12) dir-e.crypt... ok")
                .contains(
                        "(7/12) dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.crypt... ok")
                .contains("(8/12) dir-m-with-_brackets_-and-_parens_-dir.crypt... ok")
                .contains("(9/12) dir-tricky.tar.crypt... ok")
                .contains("(10/12) file-root1.bin.crypt... ok")
                .contains("(11/12) file-root2_with_space.bin.crypt... ok")
                .contains("(12/12) file-tricky.tar.crypt... ok");
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
                .contains("dir-a.crypt");
    }

    /**
     * Tests that the contained archives can be decrypted and verified.
     */
    @Test
    void backupArchivesCanBeRestored() {
        Path restoreDir = Paths.get("build/backup-restored");
        DirectoryDeleter.delete(restoreDir);

        Result res = runRestoreCmd("unpack", "-a", restoreDir.toAbsolutePath().toString());

        assertThat(res.output())
                .contains(" - (1/12) ./dir-a.tar... ok",
                        " - (2/12) ./dir-b.tar... ok",
                        " - (3/12) ./dir-c.tar... ok",
                        " - (4/12) ./dir-d with space.tar... ok",
                        " - (5/12) ./dir-deep.tar... ok",
                        " - (6/12) ./dir-e.tar... ok",
                        " - (7/12) ./dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.tar... ok", // NOSONAR
                        " - (8/12) ./dir-m-with-[brackets]-and-(parens)-dir.tar... ok",
                        " - (9/12) ./dir-tricky.tar.tar... ok",
                        " - (10/12) file-root1.bin... ok",
                        " - (11/12) file-root2 with space.bin... ok",
                        " - (12/12) file-tricky.tar... ok",
                        "Success!");
        assertThat(res.exitValue())
                .isZero();
    }

    /**
     * Tests that the full backup can be decrypted and verified.
     */
    @Test
    void backupFilesCanBeRestored() {
        Path restoreDir = Paths.get("build/backup-restored");
        DirectoryDeleter.delete(restoreDir);

        Result res = runRestoreCmd("unpack", restoreDir.toAbsolutePath().toString());

        assertThat(res.output())
                .contains(" - (1/15) dir-a/file-a1.bin... ok",
                        " - (2/15) dir-a/file-a2.bin... ok",
                        " - (3/15) dir-b/file-b1.bin... ok",
                        " - (4/15) dir-c/file-c-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.bin... ok", // NOSONAR
                        " - (5/15) dir-d with space/file-d1.bin... ok",
                        " - (6/15) dir-deep/dir-sub-a/file-deep-a.bin... ok",
                        " - (7/15) dir-deep/dir-sub-b/file-deep-b.bin... ok",
                        " - (8/15) dir-e/file-e with space.bin... ok",
                        " - (9/15) dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890/file-long1.bin... ok", // NOSONAR
                        " - (10/15) dir-m-with-[brackets]-and-(parens)-dir/empty-file... ok",
                        " - (11/15) dir-m-with-[brackets]-and-(parens)-dir/text-file.txt... ok",
                        " - (12/15) dir-tricky.tar/file-in-tricky... ok",
                        " - (13/15) file-root1.bin... ok",
                        " - (14/15) file-root2 with space.bin... ok",
                        " - (15/15) file-tricky.tar... ok",
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
     * Tests that a faulty deep file in the backup set can be found by the streaming verifier.
     */
    @Test
    void brokenDeepFileCanBeFoundByStreamVerifier() throws IOException {
        // replace last 4 chars of checksum with "dead"
        assertValidationFailsForFile(restoreScript, "dir-b/file-b1.bin");
    }

    /**
     * Tests that a faulty root-element file in the backup set can be found by the streaming verifier.
     */
    @Test
    void brokenRootFileCanBeFoundByStreamVerifier() throws IOException {
        // replace last 4 chars of checksum with "dead"
        assertValidationFailsForFile(restoreScript, "file-root1.bin");
    }

    /**
     * Tests that a faulty deep file in the backup set can be found by the streaming verifier.
     */
    @Test
    void DeepFileCanBeFoundByStreamVerifier() throws IOException {
        // replace last 4 chars of checksum with "dead"
        assertValidationFailsForFile(restoreScript, "dir-b/file-b1.bin");
    }

    /**
     * Tests that a faulty root-element file in the backup set can be found by the streaming verifier.
     */
    @Test
    void missingCryptFileBreaksStreamVerifier() throws IOException {
        Files.delete(backupDestination.resolve("dir-b.crypt"));
        Result res = MakeRestore.runRestoreCmd(restoreScript, "verify", "-s");

        assertThat(res.output())
                .contains("dir-b.crypt: No such file or directory");

        assertThat(res.exitValue())
                .isNotZero();
    }

    /**
     * Breaks one of the XXH3 checksum lines in the script and runs verification.
     *
     * Finds a line with an XXH3 checksum and the given path. The checksum is replaced.
     *
     * @param restoreScript       the script to break validation in
     * @param breakingElementPath the path of a backup element to break
     * @throws IOException if there is an IO failure
     */
    static void assertValidationFailsForFile(Path restoreScript, String breakingElementPath) throws IOException {
        Path badScriptFile = Objects.requireNonNull(restoreScript.getParent()).resolve("bad.sh");
        String goodRestoreScript = Files.readString(restoreScript, StandardCharsets.UTF_8);
        String withBrokenChecksum = goodRestoreScript.lines()
                .map(s -> s.replaceAll(",[0-9a-f]{16},(?=.*" + breakingElementPath + ")", ",deaddeaddeaddead,"))
                .collect(Collectors.joining("\n")) + "\n";
        Files.writeString(badScriptFile, withBrokenChecksum);

        if (withBrokenChecksum.equals(goodRestoreScript)) {
            throw new IllegalArgumentException("Bad script matches good script!?!");
        }

        Result res = MakeRestore.runRestoreCmd(badScriptFile, "verify", "-s");

        assertThat(res.output())
                .contains("Did not find matching checksum for file '" + breakingElementPath + "'");

        assertThat(res.exitValue())
                .isNotZero();
    }

    @Test
    void restoreScriptIsWrittenToRepository() {
        assertThat(restoreScript)
                .hasSameTextualContentAs(backupDestination.resolve("_repository/test.sh"));
    }

    private Result runRestoreCmd(String... args) {
        return MakeRestore.runRestoreCmd(restoreScript, args);
    }
}
