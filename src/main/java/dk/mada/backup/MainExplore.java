package dk.mada.backup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.cli.HumanByteCount;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.gpg.GpgEncrypterException;
import dk.mada.backup.impl.output.BackupStreamWriter;
import dk.mada.backup.impl.output.OutputBySize;
import dk.mada.backup.restore.RestoreScriptWriter;
import dk.mada.backup.restore.VariableName;

/**
 * Code from the original spike exploration of the solution.
 *
 * TODO: Needs to be rewritten/split up.
 */
public class MainExplore {
    private static final Logger logger = LoggerFactory.getLogger(MainExplore.class);
    /** File scanning buffer size. */
    private static final int FILE_SCAN_BUFFER_SIZE = 8192;
    /** File permissions used for temporary files used while creating backup. */
    private static final FileAttribute<Set<PosixFilePermission>> ATTR_PRIVATE_TO_USER = PosixFilePermissions
            .asFileAttribute(PosixFilePermissions.fromString("rwx------"));

    /** Information about the directories included in the backup. */
    private List<DirInfo> fileElements = new ArrayList<>();
    /** Size limit for crypt-files. */
    private final long maxCryptFileSize;
    /** Total size of the files in the backup (does not include directory sizes). */
    private long totalInputSize;
    /** Information needed to create GPG stream. */
    private final GpgStreamInfo gpgInfo;
    /** The selected output type for this backup. */
    private final BackupOutputType outputType;

    /**
     * Creates a new instance.
     *
     * @param gpgInfo          the GPG stream information
     * @param outputType       the desired output type
     * @param maxCryptFileSize the size limit for crypt-files
     */
    public MainExplore(GpgStreamInfo gpgInfo, BackupOutputType outputType, long maxCryptFileSize) {
        this.gpgInfo = gpgInfo;
        this.outputType = outputType;
        this.maxCryptFileSize = maxCryptFileSize;
    }

    /**
     * Create a backup from a directory.
     *
     * @param rootDir   the backup root directory
     * @param targetDir the target directory for the encrypted backup files
     * @param name      the backup name
     * @return the generated restore script
     */
    public Path packDir(Path rootDir, Path targetDir, String name) {
        if (!Files.isDirectory(rootDir)) {
            throw new IllegalArgumentException("Must be dir, was " + rootDir);
        }
        logger.info("Create backup from {}", rootDir);

        Path restoreScript = targetDir.resolve(name + ".sh");

        try {
            Files.createDirectories(targetDir);
        } catch (IOException e1) {
            throw new IllegalStateException("Failed to create target dir", e1);
        }

        // This variant makes a tar archive containing all the packaged folder tars.
        // The archive is crypted and then split into numbered output files.

        List<BackupElement> archiveElements;
        Future<List<Path>> outputFilesFuture;
        try (Stream<Path> files = Files.list(rootDir);
                BackupStreamWriter bsw = defineOutputStream(targetDir, name)) {

            archiveElements = files
                    .sorted(pathSorter(rootDir))
                    .map(p -> processRootElement(rootDir, bsw, p))
                    .toList();

            outputFilesFuture = bsw.getOutputFiles();

            logger.info("Waiting for backup streaming to complete...");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed processing", e);
        }

        List<FileInfo> cryptElements;
        try {
            cryptElements = outputFilesFuture.get().stream()
                    .map(archiveFile -> FileInfo.fromCryptFile(targetDir, archiveFile))
                    .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted getting output files", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to lazy get output files", e);
        }

        String backupTime = LocalDateTime.now().format(new DateTimeFormatterBuilder()
                .appendPattern("Y.M.d H:m")
                .toFormatter());

        Map<VariableName, String> vars = Map.of(
                VariableName.VERSION, Version.getBackupVersion(),
                VariableName.BACKUP_DATE_TIME, backupTime,
                VariableName.BACKUP_NAME, name,
                VariableName.BACKUP_INPUT_SIZE, HumanByteCount.humanReadableByteCount(totalInputSize),
                VariableName.BACKUP_KEY_ID, gpgInfo.recipientKeyId().id(),
                VariableName.BACKUP_OUTPUT_TYPE, outputType.name());
        new RestoreScriptWriter().write(restoreScript, vars, cryptElements, archiveElements, fileElements);

        return restoreScript;
    }

    private BackupStreamWriter defineOutputStream(Path targetDir, String name) throws GpgEncrypterException {
        return switch (outputType) {
        case NUMBERED -> new OutputBySize(targetDir, name, maxCryptFileSize, gpgInfo);
        };
    }

    /**
     * Sorts paths relative to a given directory.
     *
     * Explodes if called with a path not below relativeToDir.
     *
     * @param relativeToDir the root dir of the comparison
     * @return a sorter
     */
    private static Comparator<? super Path> pathSorter(Path relativeToDir) {
        return (a, b) -> {
            String pathA = relativeToDir.relativize(a).toString();
            String pathB = relativeToDir.relativize(b).toString();
            return pathA.compareToIgnoreCase(pathB);
        };
    }

    private BackupElement processRootElement(Path rootDir, BackupStreamWriter bsw, Path p) {
        logger.info("Process {}", p);
        try {
            TarArchiveOutputStream tos = bsw.processNextElement(p.getFileName().toString());
            if (Files.isDirectory(p)) {
                return processDir(rootDir, tos, p);
            } else {
                return processFile(rootDir, tos, p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FileInfo processFile(Path rootDir, TarArchiveOutputStream tarOs, Path file) {
        return copyToTar(rootDir, file, tarOs);
    }

    private FileInfo processDir(Path rootDir, TarArchiveOutputStream tarOs, Path dir) {
        try {
            // FIXME: writing to a temp file here!? And then copy to tarOs later.
            Path tempArchiveFile = Files.createTempFile("backup", "tmp", ATTR_PRIVATE_TO_USER);
            DirInfo dirInfo = createArchiveFromDir(rootDir, dir, tempArchiveFile);
            fileElements.add(dirInfo);

            String inArchiveName = dir.getFileName().toString() + ".tar";
            FileInfo res = copyTarBundleToTarResettingFileTime(tempArchiveFile, inArchiveName, tarOs);

            Files.delete(tempArchiveFile);
            return res;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Note: destroys target - which is a temporary file, so is OK
    private DirInfo createArchiveFromDir(Path rootDir, Path dir, Path archive) {
        try (OutputStream os = Files.newOutputStream(archive, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                BufferedOutputStream bos = new BufferedOutputStream(os);
                TarArchiveOutputStream tarForDirOs = makeTarOutputStream(bos)) {

            logger.debug("Creating nested archive for {}", dir);

            try (Stream<Path> files = Files.walk(dir)) {
                List<FileInfo> containedFiles = files
                        .sorted(pathSorter(rootDir))
                        .filter(Files::isRegularFile)
                        .map(f -> copyToTar(rootDir, f, tarForDirOs))
                        .toList();

                return DirInfo.from(rootDir, dir, containedFiles);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static TarArchiveOutputStream makeTarOutputStream(OutputStream sink) {
        TarArchiveOutputStream taos = new TarArchiveOutputStream(sink);
        taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        return taos;
    }

    private FileInfo copyToTar(Path rootDir, Path file, TarArchiveOutputStream tos) {
        String archivePath = rootDir.relativize(file).toString();
        return copyToTar(file, archivePath, tos, true);
    }

    private FileInfo copyTarBundleToTarResettingFileTime(Path file, String inArchiveName, TarArchiveOutputStream tos)
            throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(0));
        return copyToTar(file, inArchiveName, tos, false);
    }

    private FileInfo copyToTar(Path file, String inArchiveName, TarArchiveOutputStream tos, boolean countsTowardsSize) {
        byte[] buffer = new byte[FILE_SCAN_BUFFER_SIZE];

        try (InputStream is = Files.newInputStream(file); BufferedInputStream bis = new BufferedInputStream(is)) {
            long size = Files.size(file);

            if (countsTowardsSize) {
                totalInputSize += size;
            }

            String type = inArchiveName.endsWith(".tar") ? "=>" : "-";
            String humanSize = HumanByteCount.humanReadableByteCount(size);
            logger.info(" {} {} {}", type, inArchiveName, humanSize);
            TarArchiveEntry tae = tos.createArchiveEntry(file.toFile(), inArchiveName);

            // Apache commons 1.21+ includes user and group information that was
            // not present before. Clear it, similar to the time information.
            tae.setUserId(0);
            tae.setGroupId(0);
            tae.setGroupName("");
            tae.setUserName("");

            tos.putArchiveEntry(tae);

            HashStream64 hashStream = Hashing.xxh3_64().hashStream();

            int read;
            while ((read = bis.read(buffer)) > 0) {
                hashStream.putBytes(buffer, 0, read);
                tos.write(buffer, 0, read);
            }

            tos.closeArchiveEntry();

            return FileInfo.of(inArchiveName, size, hashStream.getAsLong());

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
