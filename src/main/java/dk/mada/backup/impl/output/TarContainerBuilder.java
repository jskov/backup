package dk.mada.backup.impl.output;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;

import dk.mada.backup.cli.HumanByteCount;
import dk.mada.backup.types.Xxh3;

/**
 * Tar container builder.
 */
public class TarContainerBuilder implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TarContainerBuilder.class);
    /** File reading buffer size. */
    private static final int FILE_READ_BUFFER_SIZE = 8192;
    /** The tar's output stream. */
    private final TarArchiveOutputStream taos;
    /** The entries added to the tar container. */
    private final List<Entry> entries = new ArrayList<>();

    /**
     * Describes an entry in the created tar container.
     *
     * @param archiveName the name in the archive
     * @param size        the size
     * @param xxh3        the xxh3 checksum
     */
    public record Entry(String archiveName, long size, Xxh3 xxh3) {

        /** Archive name prefix used to mark directories. */
        public static final String ARCHIVE_DIRECTORY_PREFIX = "./";
        /** Archive name suffix shows wrapped in TAR. */
        public static final String ARCHIVE_DIRECTORY_SUFFIX = ".tar";

        /** {@return the unwrapped folder name} */
        public String unwrappedFolderName() {
            return unwrapFolderName(archiveName);
        }

        /**
         * Wraps file system folder name to the in-archive form.
         *
         * @param folderName the file system name of the folder
         * @return the in-archive form
         */
        public static String wrapFolderName(String folderName) {
            return ARCHIVE_DIRECTORY_PREFIX + folderName + ARCHIVE_DIRECTORY_SUFFIX;
        }

        /**
         * Checks if an in-archive name represents an original folder.
         *
         * @param archiveName the in-archive name
         * @return true if the name was a folder on the file system
         */
        public static boolean isWrappedFolderName(String archiveName) {
            return archiveName.startsWith(ARCHIVE_DIRECTORY_PREFIX)
                    && archiveName.endsWith(ARCHIVE_DIRECTORY_SUFFIX);
        }

        /**
         * Unwraps an in-archive name to its file system equivalent.
         *
         * @param archiveName the in-archive name
         * @return the file system equivalent of the name
         */
        public static String unwrapFolderName(String archiveName) {
            if (isWrappedFolderName(archiveName)) {
                return archiveName.substring(ARCHIVE_DIRECTORY_PREFIX.length(),
                        archiveName.length() - ARCHIVE_DIRECTORY_SUFFIX.length());
            } else {
                return archiveName;
            }
        }
    }

    /**
     * Create new instance.
     *
     * @param sink the output stream the tar should be written to
     */
    public TarContainerBuilder(OutputStream sink) {
        taos = new TarArchiveOutputStream(sink);
        taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
    }

    /**
     * Add contents of an internal buffer to the tar container.
     *
     * @param buffer     the buffer
     * @param folderName the origin folder's name
     * @return the information for the created container entry
     */
    public Entry addStream(InternalBufferStream buffer, String folderName) {
        String inArchiveName = Entry.wrapFolderName(folderName);

        try {
            TarArchiveEntry tae = new TarArchiveEntry(inArchiveName);
            int size = buffer.size();
            tae.setSize(size);

            FileTime zeroTime = FileTime.fromMillis(0);
            tae.setLastAccessTime(zeroTime);
            tae.setCreationTime(zeroTime);
            tae.setLastModifiedTime(zeroTime);
            tae.setUserName("");

            startEntryStreaming(tae);

            buffer.writeTo(taos);

            Entry entry = new Entry(inArchiveName, size, buffer.xxh3());
            completeEntryStreaming(entry);
            return entry;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Adds a file from the file system to the container.
     *
     * @param file          the file to add
     * @param inArchiveName the in-archive name to give the file
     * @return the information for the created container entry
     */
    public Entry addFile(Path file, String inArchiveName) {
        byte[] buffer = new byte[FILE_READ_BUFFER_SIZE];

        try (InputStream is = Files.newInputStream(file); BufferedInputStream bis = new BufferedInputStream(is)) {
            long size = Files.size(file);

            String humanSize = HumanByteCount.humanReadableByteCount(size);
            logger.info(" {} {}", inArchiveName, humanSize);
            TarArchiveEntry tae = taos.createArchiveEntry(file, inArchiveName);

            logger.info("E {}", tae);

            startEntryStreaming(tae);

            HashStream64 hashStream = Hashing.xxh3_64().hashStream();
            int read;
            while ((read = bis.read(buffer)) > 0) {
                hashStream.putBytes(buffer, 0, read);
                taos.write(buffer, 0, read);
            }

            Entry entry = new Entry(inArchiveName, size, Xxh3.of(hashStream.getAsLong()));
            completeEntryStreaming(entry);
            return entry;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@return the first entry} */
    public Entry firstEntry() {
        if (entries.isEmpty()) {
            throw new IllegalStateException("Archive contains no entries!");
        }
        return entries.get(0);
    }

    private void startEntryStreaming(TarArchiveEntry tae) throws IOException {
        // Apache commons 1.21+ includes user and group information that was
        // not present before. Clear it, similar to the time information.
        tae.setUserId(0);
        tae.setGroupId(0);
        tae.setGroupName("");
        tae.setUserName("");

        taos.putArchiveEntry(tae);
    }

    private void completeEntryStreaming(Entry newEntry) throws IOException {
        taos.closeArchiveEntry();
        entries.add(newEntry);
    }

    @Override
    public void close() throws IOException {
        taos.close();
    }
}
