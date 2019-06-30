package unit.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import org.apache.commons.compress.utils.ChecksumCalculatingInputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dk.mada.backup.digest.Sha256Digester;
import dk.mada.backup.gpg.GpgEncrypter;
import dk.mada.backup.gpg.GpgEncrypterException;
import fixture.DisplayNameCamelCase;

@DisplayNameGeneration(DisplayNameCamelCase.class)
class EncryptionTest {
	public static final String TEST_RECIPIEND_KEY_ID = "281DE650E39B5DCA3E9D542092B7BAA1D6B4A52D";

	@TempDir Path dir;

	/**
	 * FIXME
	 */
	@Test
	void defaultEncryptionWorks() throws IOException {
		Path srcFile = Paths.get("src/test/data/simple-input-tree.tar");
		Path destFile = Paths.get("build/crypted.tar");
		Map<String, String> testEnv = Map.of("GNUPGHOME", Paths.get("src/test/data/gpghome").toAbsolutePath().toString());
		try (InputStream is = Files.newInputStream(srcFile);
				BufferedInputStream bis = new BufferedInputStream(is)) {
			InputStream cryptedStream = GpgEncrypter.encryptFrom(TEST_RECIPIEND_KEY_ID, testEnv, bis);
			
			Files.copy(cryptedStream, destFile, StandardCopyOption.REPLACE_EXISTING);

		}
		
		assertThat(Sha256Digester.makeDigest(destFile))
			.matches("cf0f5754bab71c0bcba92a50dbedb2650067fafce60461ed1c45c471dd10bff6");
	}
}
