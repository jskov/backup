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
     * This backup clears user/group information in the archives, which affects
     * the checksums. This is how apache-commons worked until version 1.21.
     */
    @Test
    void archiveChecksumsWithoutUserGroup() throws IOException, InterruptedException, ArchiveException {
        Path restoreScript = MakeBackup.makeBackupWoUserGroup();
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

    /**
     * (Middle) archive checksums should be unchanged over time, as long as the
     * input (backup) files are not touched. I.e. wall clock time when the backup is
     * made should not affect checksums.
     *
     * The entire backup checksum should be stable over time.
     */
    @Test
    void archiveChecksumsAreStable() throws IOException, InterruptedException, ArchiveException {
        Path restoreScript = MakeBackup.makeBackup();
        Result res = runRestoreCmd(restoreScript, "info", "-a");

        assertThat(res.exitValue())
                .isZero();
        assertThat(res.output())
                .contains(
                    "dir-a.tar 08a3dbafa61d552eece5032f626bfd19f0bbb03a21db9e549474d3cb77f52a60        2560",
                    "dir-b.tar 88adf60bd19ea90ae7582a4bce9fa0f2b41ce5dc921e5c04b174d2a832b94cb5        2048",
                    "dir-c.tar e645c86002f94e5ea02a52343bc06941ac5c4e13646114ab70e52e5545e48830        2560",
                    "dir-d with space.tar ee2ec3e6ff9614aab5e730f3a9127b43939ca65c47d6a6d18b0ab1d5e2f70561        1536",
                    "dir-deep.tar c5dcec0d69db837be615a147425676ba602651cabfe451001cd14a7275a16460        2048",
                    "dir-e.tar 4aaa0a565575668934cbce39a406c4da40b26a7feabc75b6ca1cd8f334b5ce8f        1536",
                    "dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.tar bea61c89b48ce000c0591e8c5be482825605dbbe7d6c46c64faa4369cf424f44        2560",
                    "dir-m-with-[brackets]-and-(parens)-dir.tar 82946e62820ad1024f41136baacaeb50d1fcac52bda768d9b5d4c219ffba834a        2560",
                    "file-root1.bin e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855           0",
                    "file-root2 with space.bin e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855           0");
    }

    private Result runRestoreCmd(Path script, String... args) throws IOException {
        return RestoreExecutor.runRestoreScript(script, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES, args);
    }
}
