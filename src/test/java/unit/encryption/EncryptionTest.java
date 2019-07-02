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
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.gpg.GpgEncrypter;
import fixture.DisplayNameCamelCase;
import fixture.TestCertificateInfo;

@DisplayNameGeneration(DisplayNameCamelCase.class)
class EncryptionTest {
	@TempDir Path dir;

	private GpgEncrypter sut;

	@BeforeEach
	public void init() {
		sut = new GpgEncrypter(TestCertificateInfo.TEST_RECIPIEND_KEY_ID, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES);
	}
	
	/**
	 * Tests that encrypted works by crypting a file,
	 * and verifying that decrypting it results in file
	 * like the input.
	 */
	@Test
	void defaultEncryptionWorks() throws IOException, InterruptedException {
		Path originFile = Paths.get("src/test/data/simple-input-tree.tar");
		Path cryptedFile = dir.resolve("crypted.tar");
		Path decryptedFile = dir.resolve("decrypted.tar");
		
		encryptWithTestCertificate(originFile, cryptedFile);
		
		Process p = decryptFile(cryptedFile, decryptedFile);

		assertThat(p.exitValue())
			.isEqualTo(0);
		
		assertThat(decryptedFile)
			.hasSameContentAs(originFile);
	}

	private void encryptWithTestCertificate(Path originFile, Path cryptedFile) throws IOException {
		try (InputStream is = Files.newInputStream(originFile);
				BufferedInputStream bis = new BufferedInputStream(is)) {
			InputStream cryptedStream = sut.encryptFrom(bis);
			
			Files.copy(cryptedStream, cryptedFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private Process decryptFile(Path cryptedFile, Path decryptedFile) throws IOException, InterruptedException {
		Files.deleteIfExists(decryptedFile);
		List<String> unpackCmd = List.of(
				"/usr/bin/gpg",
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
