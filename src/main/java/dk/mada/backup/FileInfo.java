package dk.mada.backup;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;

/**
 * Captures information about a file.
 */
public final class FileInfo implements BackupElement {
    /** File scanning buffer size. */
    private static final int FILE_SCAN_BUFFER_SIZE = 8192;
    /** Path of the file relative to the backup root. */
    private final String pathName;
    /** Size of the file. */
    private final long size;
    /** XXH3 sum of the file. */
    private final String xxh3;
    /** Optional MD5 sum of the file - only computed/captured for crypt-files. */
    @Nullable private final String md5Checksum;

    private FileInfo(String pathName, long size, long xxh3, @Nullable String md5) {
        this.pathName = pathName;
        this.size = size;
        this.xxh3 = HexFormat.of().toHexDigits(xxh3);
        this.md5Checksum = md5;
    }

    /** {@return the file's XXH3 checksum} */
    public String getXXH3() {
        return xxh3;
    }

    /** {@return the file's MD5 checkum if present, or fail} */
    public String getMd5() {
        return Objects.requireNonNull(md5Checksum, "No MD5 sum captured for " + pathName);
    }

    /**
     * Creates new instance.
     *
     * @param pathName the path of the file relative to the backup root directory
     * @param size     the size of the file
     * @return an instance capturing the file information for the backup
     */
    public static FileInfo of(String pathName, long size, long xxh3) {
        return new FileInfo(pathName, size, xxh3, null);
    }

    /**
     * Creates new instance by examining the file.
     *
     * @param rootDir the backup root directory
     * @param file    the file to examine
     * @return an instance capturing the file information for the backup
     */
    public static FileInfo from(Path rootDir, Path file) {
        return from(rootDir, file, false);
    }

    /**
     * Creates new instance by examining a crypt-file. This includes generating MD5 checksum.
     *
     * @param rootDir the backup root directory
     * @param file    the file to examine
     * @return an instance capturing the file information for the backup
     */
    public static FileInfo fromCryptFile(Path rootDir, Path file) {
        return from(rootDir, file, true);
    }

    private static FileInfo from(Path rootDir, Path file, boolean includeMd5Sum) {
        byte[] buffer = new byte[FILE_SCAN_BUFFER_SIZE];
        HashStream64 hashStream = Hashing.xxh3_64().hashStream();

        try (InputStream is = Files.newInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(is)) {
            MessageDigest digestMd5 = MessageDigest.getInstance("MD5"); // NOSONAR - MD5 used by Jottacloud
            long size = Files.size(file);

            int read;
            while ((read = bis.read(buffer)) > 0) {
                hashStream.putBytes(buffer, 0, read);
                if (includeMd5Sum) {
                    digestMd5.update(buffer, 0, read);
                }
            }
            String relPath = rootDir.relativize(file).getFileName().toString();

            return new FileInfo(relPath, size, hashStream.getAsLong(),
                    includeMd5Sum ? digestToString(digestMd5) : null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No algo", e);
        }
    }

    private static String digestToString(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }

    @Override
    public String toString() {
        return "FileInfo [pathName=" + pathName + ", size=" + size + ", xxh3=" + xxh3 + "]";
    }

    @Override
    public String toBackupSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        sb.append(String.format("% 11d", size));
        sb.append(',');
        sb.append(xxh3);
        if (md5Checksum != null) {
            sb.append(",");
            sb.append(md5Checksum);
        }
        sb.append(',');
        sb.append(ShellEscaper.toSafeShellString(pathName));
        sb.append('"');
        return sb.toString();
    }
}
