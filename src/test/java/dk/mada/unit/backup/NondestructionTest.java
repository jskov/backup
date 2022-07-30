package dk.mada.unit.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.api.BackupApi;
import dk.mada.backup.api.BackupTargetExistsException;
import dk.mada.fixture.DisplayNameCamelCase;
import dk.mada.fixture.TestCertificateInfo;
import dk.mada.fixture.TestDataPrepper;

/**
 * The backup program must not overwrite any files - it should fail instead.
 */
@DisplayNameGeneration(DisplayNameCamelCase.class)
class NondestructionTest {
    @TempDir
    Path targetDir;
    private BackupApi api;
    private static Path srcDir;

    @BeforeAll
    static void prepSource() throws IOException, ArchiveException {
        srcDir = TestDataPrepper.prepareTestInputTree("simple-input-tree");
    }

    @BeforeEach
    void createBackupApi() {
        api = new BackupApi(TestCertificateInfo.TEST_RECIPIEND_KEY_ID,
                TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES);
    }

    @Test
    void shouldRunWithEmptyTargetDir() {
        Path restoreScript = targetDir.resolve("test.sh");

        assertThatCode(() -> runBackup("test"))
                .doesNotThrowAnyException();

        assertThat(restoreScript)
                .exists();
    }

    @Test
    void shouldFailIfScriptExits() throws IOException {
        Path restoreScript = targetDir.resolve("test.sh");
        Files.createFile(restoreScript);

        assertThatThrownBy(() -> runBackup("test"))
                .isInstanceOf(BackupTargetExistsException.class);
    }

    @Test
    void shouldFailIfCryptFileExits() throws IOException {
        Path tarFile = targetDir.resolve("test-01.crypt");
        Files.createFile(tarFile);

        assertThatThrownBy(() -> runBackup("test"))
                .isInstanceOf(BackupTargetExistsException.class);
    }

    private void runBackup(String name) throws IOException {
        api.makeBackup(name, srcDir, targetDir);

        Files.list(targetDir).forEach(p -> System.out.println("SEE " + p));
    }
}
