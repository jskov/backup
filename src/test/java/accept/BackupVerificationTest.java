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
					"dir-a.tar 095678b2811934c6d70e6f47d47a9d967e2f3b08f496f6e52d456a1c6c88e6af        2560",
					"dir-b.tar 5840ce59c1b5a415fd30f7ae1d13b26597a6690cc70e5bf64e9005fe5e1b3c45        2048");
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
			.contains("test.tar 56d10ee345f6f43d04e5d0128ed5c8f3b557298a9af27ad6aadb8dbf994319bc        6656");
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