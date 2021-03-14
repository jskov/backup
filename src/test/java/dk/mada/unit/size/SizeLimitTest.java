package dk.mada.unit.size;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.api.BackupApi;
import dk.mada.fixture.DisplayNameCamelCase;
import dk.mada.fixture.TestCertificateInfo;
import dk.mada.fixture.TestDataPrepper;

/**
 * It should be possible to size limit the output files.
 */
@DisplayNameGeneration(DisplayNameCamelCase.class)
class SizeLimitTest {
	@TempDir Path targetDir;
	private BackupApi api;
	private static Path srcDir;

	@BeforeAll
	static void prepSource() throws IOException, ArchiveException {
		srcDir = TestDataPrepper.prepareTestInputTree("simple-input-tree");
	}
	
	@BeforeEach
	void createBackupApi() {
		api = new BackupApi(TestCertificateInfo.TEST_RECIPIEND_KEY_ID, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES, 8000);
	}
	
	@Test
	void shouldSplitBackupOverSeveralFiles() throws IOException {
		api.makeBackup("test", srcDir, targetDir);
		System.out.println("From " + srcDir);
		
		try (Stream<String> files = Files.list(targetDir).map(Path::getFileName).map(Path::toString)) {
			assertThat(files)
				.containsExactlyInAnyOrder("test.sh", "test-01.crypt", "test-02.crypt", "test-03.crypt");
		}
	}
}
