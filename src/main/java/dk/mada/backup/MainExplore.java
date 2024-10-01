package dk.mada.backup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.api.BackupArguments.Limits;
import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.cli.HumanByteCount;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.gpg.GpgEncrypterException;
import dk.mada.backup.impl.output.BackupStreamWriter;
import dk.mada.backup.impl.output.InternalBufferStream;
import dk.mada.backup.impl.output.OutputBySize;
import dk.mada.backup.impl.output.TarContainerBuilder;
import dk.mada.backup.impl.output.TarContainerBuilder.Entry;
import dk.mada.backup.restore.RestoreScriptWriter;
import dk.mada.backup.restore.VariableName;

/**
 * Code from the original spike exploration of the solution.
 *
 * TODO: Needs to be rewritten/split up.
 */
public class MainExplore {
    private static final Logger logger = LoggerFactory.getLogger(MainExplore.class);
    /** Archive name prefix used to mark directories. */
    public static final String ARCHIVE_DIRECTORY_PREFIX = "./";
    /** Archive name suffix shows wrapped in TAR. */
    public static final String ARCHIVE_DIRECTORY_SUFFIX = ".tar";

    /** Information about the directories included in the backup. */
    private List<DirInfo> fileElements = new ArrayList<>();
    /** Backup limits. */
    private final Limits limits;
    /** Total size of the files in the backup (does not include directory sizes). */
    private long totalInputSize;
    /** Information needed to create GPG stream. */
    private final GpgStreamInfo gpgInfo;
    /** The selected output type for this backup. */
    private final BackupOutputType outputType;
    /** The internal buffer used for archiving root directories. */
    private final InternalBufferStream dirPackBuffer;

    /**
     * Creates a new instance.
     *
     * @param gpgInfo    the GPG stream information
     * @param outputType the desired output type
     * @param limits     the backup process limits
     */
    public MainExplore(GpgStreamInfo gpgInfo, BackupOutputType outputType, Limits limits) {
        this.gpgInfo = gpgInfo;
        this.outputType = outputType;
        this.limits = limits;

        dirPackBuffer = new InternalBufferStream(limits.maxRootDirectorySize());
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
        case NUMBERED -> new OutputBySize(targetDir, name, limits.numberedSplitSize(), gpgInfo);
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
            TarContainerBuilder tos = bsw.processNextRootElement(p.getFileName().toString());
            if (Files.isDirectory(p)) {
                return processDir(rootDir, tos, p);
            } else {
                return processFile(rootDir, tos, p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FileInfo processFile(Path rootDir, TarContainerBuilder backupsetTarBuilder, Path file) {
        return copyToTar(rootDir, file, backupsetTarBuilder);
    }

    /**
     * Processes root directory folder.
     *
     * This is done by creating a tar archive of the folder contents.
     *
     * The archive needs to be created before it can be copied into the output container; tar needs to know the file size
     * before the data is streamed into the archive.
     *
     * @param rootDir             the root directory of the backup source
     * @param backupsetTarBuilder the tar builder for the backup set
     * @param dir                 the directory archive and copy into the backup
     * @return the file information for the added archive
     */
    private FileInfo processDir(Path rootDir, TarContainerBuilder backupsetTarBuilder, Path dir) {
        DirInfo dirInfo = newCreateArchiveFromDir(rootDir, dir);
        fileElements.add(dirInfo);

        String inArchiveName = ARCHIVE_DIRECTORY_PREFIX + dir.getFileName().toString() + ARCHIVE_DIRECTORY_SUFFIX;
        Entry entry = backupsetTarBuilder.addStream(dirPackBuffer, inArchiveName);
        FileInfo res = FileInfo.of(inArchiveName, entry.size(), entry.xxh3().value());
        return res;
    }

    private DirInfo newCreateArchiveFromDir(Path rootDir, Path dir) {
        dirPackBuffer.reset();
        try (TarContainerBuilder tarBuilder = new TarContainerBuilder(dirPackBuffer)) {
            logger.debug("Creating nested archive for {}", dir);

            try (Stream<Path> files = Files.walk(dir)) {
                List<FileInfo> containedFiles = files
                        .sorted(pathSorter(rootDir))
                        .filter(Files::isRegularFile)
                        .map(f -> copyToTar(rootDir, f, tarBuilder))
                        .toList();

                return DirInfo.from(rootDir, dir, containedFiles);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FileInfo copyToTar(Path rootDir, Path file, TarContainerBuilder tarBuilder) {
        String inArchiveName = rootDir.relativize(file).toString();
        Entry entry = tarBuilder.addFile(file, inArchiveName);

        totalInputSize += entry.size();

        return FileInfo.of(inArchiveName, entry.size(), entry.xxh3().value());
    }
}
