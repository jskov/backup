package dk.mada.accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import dk.mada.backup.restore.RestoreExecutor;
import dk.mada.backup.restore.RestoreExecutor.Result;
import dk.mada.fixture.DisplayNameCamelCase;
import dk.mada.fixture.InfoParser;
import dk.mada.fixture.InfoParser.Info;
import dk.mada.fixture.MakeBackup;
import dk.mada.fixture.TestCertificateInfo;

/**
 * Makes a backup, checks info in backup.
 */
@DisplayNameGeneration(DisplayNameCamelCase.class)
class BackupInfoTest {
    private static Path restoreScript;

    @BeforeAll
    static void makeBackup() throws IOException, ArchiveException {
        restoreScript = MakeBackup.makeBackup();
    }

    /**
     * Tests that the backup information is included in the restore script.
     */
    @Test
    void backupContainsInfo() throws IOException, InterruptedException {
        Result res = runRestoreCmd("info");

        assertThat(res.exitValue)
                .isEqualTo(0);
        assertThat(res.output)
                .contains(
                        "Backup 'test'",
                        "made with backup version undef",
                        "created on 20",
                        "original size 66 B",
                        "encrypted with key id " + TestCertificateInfo.TEST_RECIPIEND_KEY_ID);
    }

    /**
     * Tests that the backup information for crypted archives can be printed.
     *
     * Size is checked to be within a range since crypted data on Ubuntu (GitHub
     * actions) seems to differ from data crypted on Fedora (my dev box).
     */
    @Test
    void backupInfoCrypted() throws IOException, InterruptedException {
        Result res = runRestoreCmd("info", "-c");

        assertThat(res.exitValue)
                .isEqualTo(0);

        List<Info> infos = new InfoParser().parse(res.output);
        assertThat(infos)
                .hasSize(1)
                .first()
                .satisfies(i -> {
                    assertThat(i.filename()).isEqualTo("test-01.crypt");
                    assertThat(i.size()).isBetween(22100L, 22300L);
                });
    }

    /**
     * Tests that the backup information for tar archives can be printed.
     */
    @Test
    void backupInfoTars() throws IOException, InterruptedException {
        Result res = runRestoreCmd("info", "-a");

        assertThat(res.exitValue)
                .isEqualTo(0);
        assertThat(res.output)
                .contains(
                        "dir-a.tar e42fa7a5806b41d4e1646ec1885e1f43bdbd9488465fa7022c1aa541ead9348f        2560",
                        "file-root1.bin");
    }

    /**
     * Tests that the backup information for the original files can be printed.
     */
    @Test
    void backupInfoFiles() throws IOException, InterruptedException {
        Result res = runRestoreCmd("info", "-f");

        System.out.println(res.output);

        assertThat(res.exitValue)
                .isEqualTo(0);
        assertThat(res.output)
                .contains(
                        "dir-a/file-a1.bin e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855           0",
                        "dir-e/file-e with space.bin e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855           0");
    }

    private Result runRestoreCmd(String... args) throws IOException {
        return runRestoreCmd(restoreScript, args);
    }

    private Result runRestoreCmd(Path script, String... args) throws IOException {
        return RestoreExecutor.runRestoreScript(script, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES, args);
    }
}
