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
 * Makes a backup, cheks info in backup.
 */
@DisplayNameGeneration(DisplayNameCamelCase.class)
class BackupInfoTest {
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
		Result res = runRestoreCmd("info");
		
		assertThat(res.exitValue)
			.isEqualTo(0);
		assertThat(res.output)
			.contains(
				"Backup 'test'",
				"made with backup version undef",
				"created on 20",
				"original size 12.6 KiB",
				"encrypted with key id 281DE650E39B5DCA3E9D542092B7BAA1D6B4A52D"
			);
	}


	private Result runRestoreCmd(String... args) throws IOException {
		return runRestoreCmd(restoreScript, args);
	}
	
	private Result runRestoreCmd(Path script, String... args) throws IOException {
		return RestoreExecutor.runRestoreScript(script, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES, args);
	}
}