package dk.mada.backup.gpg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveEntry;
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
//					"--homedir",
//					"/opt/sources/eclipse.git/backup/src/test/data/gpghome",
					"--compress-algo",
					"none",
					"--with-colons",
					"--cipher-algo",
					"AES256",
					"--batch",
					"--recipient",
					recipientKeyId,
					"--encrypt"));
			ProcessBuilder pb = new ProcessBuilder()
				.command(cmd)
				.redirectErrorStream(true);
			
			pb.environment().putAll(envOverrides);
			
			logger.info("Env: {}", envOverrides);
			logger.info("Run: {}", cmd);
			
			Process p = pb.start();

			new Thread(() -> copy(is, p.getOutputStream())).start();
			
			return p.getInputStream();
		} catch (IOException e) {
			throw new GpgEncrypterException("Failed to run encryption", e);
		}
	}

	private static void copy(InputStream is, OutputStream outputStream) {
		logger.info("start copy");
		byte[] buffer = new byte[8192];

		try (BufferedInputStream bis = new BufferedInputStream(is);
			 BufferedOutputStream bos = new BufferedOutputStream(outputStream)) {
			int read;
			while ((read = bis.read(buffer)) > 0) {
				logger.info("Read {} crypted bytes", read);
				bos.write(buffer, 0, read);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to copy data to crypt stream");
		}
	}
}
