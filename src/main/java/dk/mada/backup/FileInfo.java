package dk.mada.backup;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileInfo implements BackupElement {
	private final String pathName;
	private final long size;
	private final String checksum;

	public FileInfo(String pathName, long size, String checksum) {
		this.pathName = pathName;
		this.size = size;
		this.checksum = checksum;
	}

	public String getPathName() {
		return pathName;
	}

	public long getSize() {
		return size;
	}

	public String getChecksum() {
		return checksum;
	}

	public static FileInfo from(Path rootDir, Path file) {
		byte[] buffer = new byte[8192];

		try (InputStream is = Files.newInputStream(file); BufferedInputStream bis = new BufferedInputStream(is)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			long size = Files.size(file);

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
			String checksum = sb.toString();

			String relPath = rootDir.relativize(file).getFileName().toString();
			
			return new FileInfo(relPath, size, checksum);
			
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("No algo", e);
		}
	}

	@Override
	public String toString() {
		return "FileInfo [pathName=" + pathName + ", size=" + size + ", checksum=" + checksum + "]";
	}

	@Override
	public String toBackupSummary() {
		StringBuilder sb = new StringBuilder();
		sb.append('"');
		sb.append(String.format("% 11d", size));
		sb.append(',');
		sb.append(checksum);
		sb.append(',');
		sb.append(pathName);
		sb.append('"');
		return sb.toString();
	}
}
