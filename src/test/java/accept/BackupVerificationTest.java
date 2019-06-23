package accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

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
	
	@Test
	void backupCryptFilesCanBeVerified() throws IOException, InterruptedException {
		Process p = new ProcessBuilder("/bin/bash", restoreScript.toAbsolutePath().toString(), "verify")
				.directory(restoreScript.getParent().toFile())
				.redirectErrorStream(true)
				.start();
	
		String output = readOutput(p);
		
		System.out.println(output);
		
		assertThat(p.waitFor())
			.isEqualTo(0);
		assertThat(output)
			.contains("(1/1) test.tar... ok");
	}
	
	private String readOutput(Process p) throws IOException {
         try (InputStream in = p.getInputStream()) {
                 return new String(in.readAllBytes());
         }
	 }

	

}