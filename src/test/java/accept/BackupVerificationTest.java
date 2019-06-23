package accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import dk.mada.backup.BackupApi;
import fixture.DisplayNameCamelCase;

@DisplayNameGeneration(DisplayNameCamelCase.class)
class BackupVerificationTest {

	private static Path restoreScript;

	@BeforeAll
	static void makeBackup() {
		BackupApi backupApi = new BackupApi();
		
		Path targetDir = Paths.get("build/backups");
		Path sourceDir = Paths.get("src/test/data/simple-input-tree");
		restoreScript = backupApi.makeBackup("test", sourceDir, targetDir);
	}
	
	/**
	 * Tests that the verification of the encrypted archive(s) works.
	 */
	@Test
	void backupCryptFilesCanBeVerified() throws IOException, InterruptedException {
		Process p = runRestoreCmd("verify");
		String output = readOutput(p);
		
		assertThat(p.waitFor())
			.isEqualTo(0);
		assertThat(output)
			.contains("(1/1) test.tar... ok");
	}

	/**
	 * Ensures that archive checksums do not change over time.
	 * 
	 * The entire backup checksum should be stable over time.
	 */
	@Test
	void archiveChecksumsStableOverTime() throws IOException, InterruptedException {
		Process p = runRestoreCmd("info", "archives");
		String output = readOutput(p);
		
		assertThat(p.waitFor())
			.isEqualTo(0);
		assertThat(output)
			.contains(
					"dir-a.tar 3c32b9dd275cc635ede8534a0ebc97b1c3413622d3e10177ebf06ab564619ca7        2560",
					"dir-b.tar 88ce90d3172bb3201451e49d8833b75ba12ebe2c947cdc78184b0a8d5c65e75b        2048");
	}

	/**
	 * Ensures that encrypted archive checksums do not change over time.
	 * 
	 * The entire backup checksum should be stable over time.
	 */
	@Test
	void cryptChecksumsStableOverTime() throws IOException, InterruptedException {
		Process p = runRestoreCmd("info", "crypts");
		String output = readOutput(p);
		
		assertThat(p.waitFor())
			.isEqualTo(0);
		assertThat(output)
			.contains("test.tar 7914e1fbce2294b5fa543a1dba3a85dcbdb0794cbc0426ba80f378be955b87e9        6656");
	}

	private Process runRestoreCmd(String... args) throws IOException {
		List<String> cmd = new ArrayList<>(List.of("/bin/bash", restoreScript.toAbsolutePath().toString()));
		cmd.addAll(List.of(args));
		return new ProcessBuilder(cmd)
				.directory(restoreScript.getParent().toFile())
				.redirectErrorStream(true)
				.start();
	}

	private String readOutput(Process p) throws IOException {
         try (InputStream in = p.getInputStream()) {
                 return new String(in.readAllBytes());
         }
	 }
}