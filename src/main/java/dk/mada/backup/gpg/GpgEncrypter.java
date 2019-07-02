package dk.mada.backup.gpg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encrypts an input stream via an external GPG process.
 * The output from the GPG process is returned as an inputStream
 * for the client to consume.
 */
public class GpgEncrypter {
	private static final Logger logger = LoggerFactory.getLogger(GpgEncrypter.class);
	private final String recipientKeyId;
	private final Map<String, String> envOverrides;

	/**
	 * @param recipientKeyId
	 * @param envOverrides Environment overrides allows tests to specify the GNUPGHOME variable.
	 */
	public GpgEncrypter(String recipientKeyId, Map<String, String> envOverrides) {
		this.recipientKeyId = recipientKeyId;
		this.envOverrides = envOverrides;
	}

	public GpgEncrypter(String recipientKeyId) {
		this(recipientKeyId, Collections.emptyMap());
	}
	
	public InputStream encryptFrom(InputStream is) {
		try {
			List<String> cmd = new ArrayList<>(List.of(
					"/usr/bin/gpg",
					"--compress-algo",
					"none",
					"--with-colons",
					"--cipher-algo",
					"AES256",
					"--batch",
					"--no-tty",
					"--recipient",
					recipientKeyId,
					"--encrypt"));
			ProcessBuilder pb = new ProcessBuilder()
				.command(cmd)
				.redirectErrorStream(false);
			
			pb.environment().putAll(envOverrides);
			
			logger.debug("Env: {}", envOverrides);
			logger.debug("Run: {}", cmd);
			
			Process p = pb.start();

			new Thread(() -> copy(is, p.getOutputStream())).start();
			
			return p.getInputStream();
		} catch (IOException e) {
			throw new GpgEncrypterException("Failed to run encryption", e);
		}
	}

	private static void copy(InputStream is, OutputStream outputStream) {
		byte[] buffer = new byte[8192];

		try (BufferedInputStream bis = new BufferedInputStream(is);
			 BufferedOutputStream bos = new BufferedOutputStream(outputStream)) {
			int read;
			while ((read = bis.read(buffer)) > 0) {
				bos.write(buffer, 0, read);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to copy data to crypt stream");
		}
	}
}
