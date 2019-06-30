package unit.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.digest.Sha256Digester;
import dk.mada.backup.gpg.GpgEncrypter;
import fixture.DisplayNameCamelCase;

@DisplayNameGeneration(DisplayNameCamelCase.class)
class EncryptionTest {
	private static final String ABS_TEST_GNUPG_HOME = Paths.get("src/test/data/gpghome").toAbsolutePath().toString();

	public static final String TEST_RECIPIEND_KEY_ID = "281DE650E39B5DCA3E9D542092B7BAA1D6B4A52D";

	@TempDir Path dir;

	/**
	 * FIXME
	 * @throws InterruptedException 
	 */
	@Test
	void defaultEncryptionWorks() throws IOException, InterruptedException {
		Path srcFile = Paths.get("src/test/data/simple-input-tree.tar");
		Path destFile = Paths.get("build/crypted.tar");
		Path expandedFile = Paths.get("build/unpacked.tar");
		Map<String, String> testEnv = Map.of("GNUPGHOME", ABS_TEST_GNUPG_HOME);
		try (InputStream is = Files.newInputStream(srcFile);
				BufferedInputStream bis = new BufferedInputStream(is)) {
			InputStream cryptedStream = GpgEncrypter.encryptFrom(TEST_RECIPIEND_KEY_ID, testEnv, bis);
			
			Files.copy(cryptedStream, destFile, StandardCopyOption.REPLACE_EXISTING);
		}
		
		Files.deleteIfExists(expandedFile);
		
		List<String> unpackCmd = List.of("/usr/bin/gpg", "--homedir", ABS_TEST_GNUPG_HOME, "-o", expandedFile.toString(), "-d", destFile.toString());
		Process p = new ProcessBuilder(unpackCmd)
				.redirectErrorStream(true)
				.start();
			String out = new String(p.getInputStream().readAllBytes());
		p.waitFor(5, TimeUnit.SECONDS);
		
		System.out.println(unpackCmd);
		System.out.println("GOT " + out);
		
		assertThat(expandedFile)
			.hasSameContentAs(srcFile);
		
	}
}
