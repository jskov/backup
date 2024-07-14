package dk.mada.accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
     * (Middle) archive checksums should be unchanged over time, as long as the
     * input (backup) files are not touched. I.e. wall clock time when the backup is
     * made should not affect checksums.
     *
     * The entire backup checksum should be stable over time.
     *
     * The backup clears user/group information in the archives, which affects
     * the checksums. This is how apache-commons worked until version 1.21.
     */
    @Test
    void archiveChecksumsWithoutUserGroup() throws IOException, ArchiveException {
        Path restoreScript = MakeBackup.makeBackup();
        Result res = runRestoreCmd(restoreScript, "info", "-a");

        assertThat(res.exitValue())
                .isZero();
        assertThat(res.output())
                .contains(
                    "dir-a.tar e42fa7a5806b41d4e1646ec1885e1f43bdbd9488465fa7022c1aa541ead9348f        2560",
                    "dir-b.tar 628b2ef22626e6a2d74c4bf441cf6394d5db0bf149a4a98ee048b51d9ce69374        2048",
                    "dir-c.tar 7b3a2129a589e26f52a0c6b08cb5deeecaeb6b148aa955bf9f165c35c8d2e45c        2560",
                    "dir-d with space.tar 702e1d9f4282ff5c4f75b9f4383e69ac831925c4ce1b465750f15438d99e3771        1536",
                    "dir-deep.tar 3a3bdb716a5ccd79b623f66ca3846d4e99cde7cc6473b5d3481d1faae5d68229        2048",
                    "dir-e.tar 195ba00464fecff0449ee30f54685247e6f254f3e761678e0fcc403b399853fb        1536",
                    "dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.tar 006e8260325290418ad66d8ae8f5338b097663218b4c54eee213916646ffab51        2560",
                    "dir-m-with-[brackets]-and-(parens)-dir.tar a49d85c6255891ce8b674f1261137408ca56baa966f422163bacacf318cb6a23        2560",
                    "file-root1.bin e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855           0",
                    "file-root2 with space.bin e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855           0");
    }

    private Result runRestoreCmd(Path script, String... args) {
        return RestoreExecutor.runRestoreScript(script, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES, args);
    }
}
