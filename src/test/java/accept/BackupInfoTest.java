package accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.ArchiveException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import dk.mada.backup.restore.RestoreExecutor;
import dk.mada.backup.restore.RestoreExecutor.Result;
import fixture.DisplayNameCamelCase;
import fixture.MakeBackup;
import fixture.TestCertificateInfo;

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
				"encrypted with key id 281DE650E39B5DCA3E9D542092B7BAA1D6B4A52D"
			);
	}

	/**
	 * Tests that the backup information for crypted archives can be printed.
	 */
	@Test
	void backupInfoCrypted() throws IOException, InterruptedException {
		Result res = runRestoreCmd("info", "-c");

		assertThat(res.exitValue)
			.isEqualTo(0);
		assertThat(res.output)
			.contains(
				"test-01.crypt",
				"22348"
			);
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
				"file-root1.bin"
			);
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
				"dir-e/file-e with space.bin e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855           0"
			);
	}


	private Result runRestoreCmd(String... args) throws IOException {
		return runRestoreCmd(restoreScript, args);
	}
	
	private Result runRestoreCmd(Path script, String... args) throws IOException {
		return RestoreExecutor.runRestoreScript(script, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES, args);
	}
}