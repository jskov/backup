package dk.mada.backup.digest;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Sha256Digester {

	public static String makeDigest(Path file) {
		byte[] buffer = new byte[8192];
		try (InputStream is = Files.newInputStream(file); BufferedInputStream bis = new BufferedInputStream(is)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			int read;
			while ((read = bis.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
			}
			byte[] hash = digest.digest();
	
			BigInteger bigInt = new BigInteger(1, hash);
			StringBuilder sb = new StringBuilder(bigInt.toString(16));
			while (sb.length() < 64) {
				sb.insert(0, '0');
			}
			return sb.toString();
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("Failed to digest " + file, e);
		}
	}
}
