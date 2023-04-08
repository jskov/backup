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
    /** SHA-256 sum of the file. */
    private final String checksum;
    /** Optional MD5 sum of the file - only computed/captured for crypt-files. */
    @Nullable private final String md5Checksum;

    private FileInfo(String pathName, long size, String checksum, @Nullable String md5) {
        this.pathName = pathName;
        this.size = size;
        this.checksum = checksum;
        this.md5Checksum = md5;
    }

    /** {@return the file's SHA-256 checksum} */
    public String getChecksum() {
        return checksum;
    }

    /** {@return the file's MD5 checkum if present, or fail} */
    public String getMd5() {
        return Objects.requireNonNull(md5Checksum, "No MD5 sum captured for " + pathName);
    }

    /**
     * Creates new instance.
     *
     * @param pathName the path of the file relative to the backup root directory
     * @param size the size of the file
     * @param digest the digest computed for the file
     * @return an instance capturing the file information for the backup
     */
    public static FileInfo of(String pathName, long size, MessageDigest digest) {
        return new FileInfo(pathName, size, digestToString(digest), null);
    }

    /**
     * Creates new instance by examining the file.
     *
     * @param rootDir the backup root directory
     * @param file the file to examine
     * @return an instance capturing the file information for the backup
     */
    public static FileInfo from(Path rootDir, Path file) {
        return from(rootDir, file, false);
    }

    /**
     * Creates new instance by examining a crypt-file. This includes
     * generating MD5 checksum.
     *
     * @param rootDir the backup root directory
     * @param file the file to examine
     * @return an instance capturing the file information for the backup
     */
    public static FileInfo fromCryptFile(Path rootDir, Path file) {
        return from(rootDir, file, true);
    }

    private static FileInfo from(Path rootDir, Path file, boolean includeMd5Sum) {
        byte[] buffer = new byte[FILE_SCAN_BUFFER_SIZE];

        try (InputStream is = Files.newInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(is)) {
            MessageDigest digestMd5 = MessageDigest.getInstance("MD5"); // NOSONAR - MD5 used by Jottacloud
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = Files.size(file);

            int read;
            while ((read = bis.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
                if (includeMd5Sum) {
                    digestMd5.update(buffer, 0, read);
                }
            }
            String checksum = digestToString(digest);

            String relPath = rootDir.relativize(file).getFileName().toString();

            return new FileInfo(relPath, size, checksum,
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
        return "FileInfo [pathName=" + pathName + ", size=" + size + ", checksum=" + checksum + "]";
    }

    @Override
    public String toBackupSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        sb.append(String.format("% 11d", size));
        sb.append(',');
        sb.append(checksum);
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
