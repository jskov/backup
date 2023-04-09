package dk.mada.unit.arguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.api.BackupApi;
import dk.mada.backup.api.BackupTargetExistsException;
import dk.mada.fixture.TestCertificateInfo;
import dk.mada.fixture.TestDataPrepper;

/**
 * The backup program must not overwrite any files - it should fail instead.
 */
class NondestructionTest {
    private static final Logger logger = LoggerFactory.getLogger(NondestructionTest.class);
    /** Directory to backup of. */
    private static Path srcDir;
    /** The backup API - SUT. */
    private BackupApi api;
    /** Target directory for test.*/
    private @TempDir Path targetDir;

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

        try (Stream<Path> fileStream = Files.list(targetDir)) {
            List<Path> filesInBackupFolder = fileStream.toList();
            logger.info("See backup files: {}", filesInBackupFolder);
        }
    }
}
