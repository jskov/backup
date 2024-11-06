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

import dk.mada.backup.cli.HumanByteCount;
import dk.mada.backup.impl.output.BackupPolicy;
import dk.mada.backup.impl.output.BackupStreamWriter;
import dk.mada.backup.impl.output.InternalBufferStream;
import dk.mada.backup.impl.output.TarContainerBuilder;
import dk.mada.backup.impl.output.TarContainerBuilder.Entry;
import dk.mada.backup.restore.RestoreScriptWriter;
import dk.mada.backup.restore.VariableName;

/**
 * Creates backup using pluggable policies.
 */
public class BackupCreator {
    private static final Logger logger = LoggerFactory.getLogger(BackupCreator.class);
    /** Information about the directories included in the backup. */
    private List<DirInfo> fileElements = new ArrayList<>();
    /** Backup policy. */
    private final BackupPolicy policy;
    /** Total size of the files in the backup (does not include directory sizes). */
    private long totalInputSize;
    /** The internal buffer used for archiving root directories. */
    private final InternalBufferStream dirPackBuffer;

    /**
     * Creates a new instance.
     *
     * @param policy the backup policy
     */
    public BackupCreator(BackupPolicy policy) {
        this.policy = policy;

        dirPackBuffer = new InternalBufferStream(policy.limits().maxRootDirectorySize());
    }

    /**
     * Create a backup according to policy.
     *
     * @return the generated restore script
     */
    public Path create() {
        Path rootDir = policy.rootDirectory();
        if (!Files.isDirectory(rootDir)) {
            throw new IllegalArgumentException("Must be dir, was " + rootDir);
        }
        logger.info("Create backup from {}", rootDir);

        policy.backupPrep();

        Path restoreScript = policy.restoreScript();
        Path targetDir = policy.targetDirectory();

        try {
            Files.createDirectories(targetDir);
        } catch (IOException e1) {
            throw new IllegalStateException("Failed to create target dir", e1);
        }

        // Process root elements
        List<BackupElement> archiveElements;
        Future<List<Path>> outputFilesFuture;
        try (Stream<Path> files = Files.list(rootDir);
                BackupStreamWriter bsw = policy.writer()) {

            archiveElements = files
                    .sorted(pathSorter(rootDir))
                    .map(p -> processRootElement(rootDir, bsw, p))
                    .toList();

            outputFilesFuture = bsw.getOutputFiles();

            logger.info("Waiting for backup streaming to complete...");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed processing", e);
        }

        // Wait for the encrypted output files to settle
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
                .appendPattern("YYYY.MM.dd-HHmm")
                .toFormatter());

        Map<VariableName, String> vars = Map.of(
                VariableName.VERSION, Version.getBackupVersion(),
                VariableName.BACKUP_DATE_TIME, backupTime,
                VariableName.BACKUP_NAME, policy.backupName(),
                VariableName.BACKUP_INPUT_SIZE, HumanByteCount.humanReadableByteCount(totalInputSize),
                VariableName.BACKUP_KEY_ID, policy.gpgInfo().recipientKeyId().id(),
                VariableName.BACKUP_OUTPUT_TYPE, policy.outputType().name());
        new RestoreScriptWriter().write(restoreScript, vars, cryptElements, archiveElements, fileElements);

        policy.completeBackup();

        return restoreScript;
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

        Entry entry = backupsetTarBuilder.addStream(dirPackBuffer, dir.getFileName().toString());
        return FileInfo.of(entry.archiveName(), entry.size(), entry.xxh3().value());
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
