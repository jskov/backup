package dk.mada.unit.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.gpg.GpgEncryptedOutputStream;
import dk.mada.fixture.DisplayNameCamelCase;
import dk.mada.fixture.TestCertificateInfo;

@DisplayNameGeneration(DisplayNameCamelCase.class)
class EncryptionOutputStreamTest {
	private static final Logger logger = LoggerFactory.getLogger(EncryptionOutputStreamTest.class);
	private static final String USR_BIN_GPG = "/usr/bin/gpg";
	@TempDir Path dir;
	
	/**
	 * Tests that encrypted works by encrypting an output stream
	 * and verifying that decrypting again results in a file
	 * like the input.
	 */
	@Test
	void defaultEncryptionWorks() throws IOException, InterruptedException {
		Files.createDirectories(dir);
		Path originFile = Paths.get("src/test/data/simple-input-tree.tar");
		Path cryptedFile = dir.resolve("crypted.tar");
		Path decryptedFile = dir.resolve("decrypted.tar");
		
		try (OutputStream os = Files.newOutputStream(cryptedFile);
				BufferedOutputStream bos = new BufferedOutputStream(os);
				GpgEncryptedOutputStream sutOutputStream = new GpgEncryptedOutputStream(bos, TestCertificateInfo.TEST_RECIPIEND_KEY_ID, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES)) {
			Files.copy(originFile, sutOutputStream);
		} catch (Exception e) {
			logger.warn("Failed", e);
			throw e;
		}

		Process p = decryptFile(cryptedFile, decryptedFile);
		printProcessOutput(p);
		
		assertThat(decryptedFile)
			.hasSameContentAs(originFile);

		assertThat(p.exitValue())
			.isEqualTo(0);
	}

	private void printProcessOutput(Process p) throws IOException {
		System.out.println(new String(p.getInputStream().readAllBytes()));
	}

	private Process decryptFile(Path cryptedFile, Path decryptedFile) throws IOException, InterruptedException {
		Files.deleteIfExists(decryptedFile);
		List<String> unpackCmd = List.of(
				USR_BIN_GPG,
				"--homedir", TestCertificateInfo.ABS_TEST_GNUPG_HOME,
				"-o", decryptedFile.toString(),
				"-d", cryptedFile.toString());
		Process p = new ProcessBuilder(unpackCmd)
				.redirectErrorStream(true)
				.start();
		p.waitFor(5, TimeUnit.SECONDS);
		return p;
	}
}
