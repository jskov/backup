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

public class GpgEncrypter {
	private static final Logger logger = LoggerFactory.getLogger(GpgEncrypter.class);

	public static InputStream encryptFrom(String recipientKeyId, InputStream is) {
		return encryptFrom(recipientKeyId, Collections.emptyMap(), is);
	}
	
	public static InputStream encryptFrom(String recipientKeyId, Map<String, String> envOverrides, InputStream is) {
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
