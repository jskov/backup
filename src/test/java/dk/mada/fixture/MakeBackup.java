package dk.mada.fixture;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.compress.archivers.ArchiveException;

import dk.mada.backup.cli.CliMain;

public class MakeBackup {
	
	/**
	 * Create backup from test data.
	 * @return restore script
	 * 
	 * @throws IOException
	 * @throws ArchiveException
	 */
	public static Path makeBackup() throws IOException, ArchiveException {
		Path srcDir = TestDataPrepper.prepareTestInputTree("simple-input-tree");
		Path targetDir = Paths.get("build/backup-dest");
		
		org.assertj.core.util.Files.delete(targetDir.toFile());

		Path restoreScript = targetDir.resolve("test.sh");
		CliMain.main(new String[] {
					"--running-tests",
					"-n", "test", 
					"-r", TestCertificateInfo.TEST_RECIPIEND_KEY_ID,
					"--gpg-homedir", TestCertificateInfo.ABS_TEST_GNUPG_HOME,
					srcDir.toAbsolutePath().toString(),
					targetDir.toAbsolutePath().toString()
				});
		
		return restoreScript;
	}

}
