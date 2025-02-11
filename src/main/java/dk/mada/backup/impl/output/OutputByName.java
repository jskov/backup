package dk.mada.backup.impl.output;

import dk.mada.backup.FileInfo;
import dk.mada.backup.api.BackupException;
import dk.mada.backup.api.BackupTargetExistsException;
import dk.mada.backup.gpg.GpgEncryptedOutputStream;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.impl.output.TarContainerBuilder.Entry;
import dk.mada.backup.restore.RestoreScriptReader.DataArchive;
import dk.mada.backup.restore.RestoreScriptReader.DataRootFile;
import dk.mada.backup.restore.RestoreScriptReader.RestoreScriptData;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Write backup into separate files. Each file is an encrypted archive of a root file from the source tree.
 *
 * The root file is archived and the archive's checksum is compared to the matching archive file from the previous
 * backup set (if available). If the archive files match, the encryption is skipped (and the encrypted file from the
 * previous set is used).
 *
 * The archive is built in memory, so size is limited.
 */
public final class OutputByName implements BackupStreamWriter {
    private static final Logger logger = LoggerFactory.getLogger(OutputByName.class);
    /** Characters allowed in crypt file names. */
    private static final Pattern ALLOWED_FS_CHARS = Pattern.compile("[a-zA-Z0-9æøåÆØÅ.-]");

    /** Accruing list of files created from the stream. */
    private final List<Path> outputFiles = new ArrayList<>();
    /** Future for handing over the list of created files to the caller. */
    private CompletableFuture<List<FileInfo>> outputFilesFuture = new CompletableFuture<>();
    /** Target directory for the files split from the stream. */
    private final Path targetDir;
    /** The information needed to build a GPG stream. */
    private final GpgStreamInfo gpgInfo;

    /** Data about the previous backup. */
    private final RestoreScriptData prevBackupData;
    /** Memory buffer holding each backup file as it is created. */
    private final MemorySegmentOutputStream inMemoryBufferStream;
    /** The name of the file currently being created. */
    @Nullable private String workingOnFileName;

    /** The tar container builder. */
    @Nullable private TarContainerBuilder tarBuilder;

    /** Total amount of time spent encrypting data. */
    private Duration totalEncryptionTime = Duration.ZERO;
    /** Total amount of reading data into tar archive. */
    private Duration totalTarTime = Duration.ZERO;
    /** Start time for current tar creation. */
    private Instant tarCollectionStart = Instant.EPOCH;

    /**
     * Construct new instance.
     *
     * @param maxRootElementSize the maximal size of an archived root element
     * @param prevBackupData     data about the previous backup
     * @param targetDir          the target directory of the new backup
     * @param gpgInfo            the GPG information
     */
    public OutputByName(
            long maxRootElementSize, RestoreScriptData prevBackupData, Path targetDir, GpgStreamInfo gpgInfo) {
        this.targetDir = targetDir;
        this.gpgInfo = gpgInfo;
        this.prevBackupData = prevBackupData;

        inMemoryBufferStream = new MemorySegmentOutputStream(maxRootElementSize);
    }

    @Override
    public TarContainerBuilder processNextRootElement(String name) throws IOException {
        closeCurrentFileAndEncrypt();

        inMemoryBufferStream.reset();
        workingOnFileName = name;

        tarBuilder = new TarContainerBuilder(inMemoryBufferStream);
        tarCollectionStart = Instant.now();
        return tarBuilder;
    }

    private void closeCurrentFileAndEncrypt() throws IOException {
        if (tarBuilder == null || workingOnFileName == null) {
            return;
        }

        tarBuilder.close();
        Entry rootElementEntry = tarBuilder.firstEntry();
        tarBuilder = null;

        Duration tarTime = Duration.between(tarCollectionStart, Instant.now());
        totalTarTime = totalTarTime.plus(tarTime);

        logger.info("Relative for {}", workingOnFileName);
        logger.info("Current input count: {}", inMemoryBufferStream.count());
        logger.info("Current input xxh3: {}", inMemoryBufferStream.xxh3());

        String rootElementName = rootElementEntry.unwrappedFolderName();

        // Find the matching entry in the old backup set (if available)
        // If the old archive data matches the newly created archive data,
        // the encrypted file can be reused. Note that the encrypted data
        // cannot be used for comparison, because there is time variance
        // in these (even for the same input data).
        DataRootFile oldRootFile = prevBackupData.rootFilesV2().stream()
                .filter(da -> rootElementName.equals(da.name()))
                .findFirst()
                .orElse(null);
        if (oldRootFile != null && prevBackupData.gpgKeyId().equals(gpgInfo.recipientKeyId())) {

            logger.info("Existing backup has entry for root element {}", rootElementName);
            DataArchive oldArchive = oldRootFile.archive();
            if (oldArchive.size() == rootElementEntry.size()
                    && oldArchive.xxh3().equals(rootElementEntry.xxh3())) {
                logger.info(" - keeping");
                Path oldSetCryptFile = oldRootFile.crypt().file();
                Path newSetCryptFile = targetDir.resolve(oldSetCryptFile.getFileName());
                createHardLink(newSetCryptFile, oldSetCryptFile);
                outputFiles.add(newSetCryptFile);
                return;
            }
        }

        logger.info("No prior data for root element {}", rootElementName);

        Instant start = Instant.now();
        try (OutputStream output = openNextFile(workingOnFileName);
                var eos = new GpgEncryptedOutputStream(output, gpgInfo)) {
            inMemoryBufferStream.writeTo(eos);
        }
        Duration time = Duration.between(start, Instant.now());
        logger.info("------- Crypted archive to {} in {}", workingOnFileName, time);
        totalEncryptionTime = totalEncryptionTime.plus(time);
    }

    @Override
    public void close() throws IOException {
        closeCurrentFileAndEncrypt();

        List<FileInfo> fileInfos = outputFiles.stream()
                .map(f -> FileInfo.fromCryptFile(targetDir, f))
                .toList();

        outputFilesFuture.complete(fileInfos);

        logger.info("Tar archiving time total: {}", totalTarTime);
        logger.info("Encryption time total: {}", totalEncryptionTime);
    }

    @Override
    public Future<List<FileInfo>> getOutputFiles() {
        return outputFilesFuture;
    }

    /**
     * Prepare for streaming into the next encrypted file.
     *
     * Get expected target checksum from target restore file. o file hash o hash of content filenames + file contents
     *
     * @param name the name of the file
     * @return the stream to write data to
     * @throws IOException if IO fails
     */
    private OutputStream openNextFile(String name) throws IOException {
        Path outputFile = targetDir.resolve(nameSafeFsName(name) + ".crypt");

        if (Files.exists(outputFile)) {
            // close before failing - or wrapping streams will fail when they try to flush
            // to this stream
            close();
            throw new BackupTargetExistsException("Target file " + outputFile + " already exists");
        }

        outputFiles.add(outputFile);

        OutputStream fileOutput =
                Files.newOutputStream(outputFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new BufferedOutputStream(fileOutput);
    }

    /**
     * Makes a safe file system name for a backup crypt element.
     *
     * The restore script does not handle crypt filenames in a way to handle weird characters (or even just spaces). So for
     * now convert to something safe.
     *
     * @param name the input name
     * @return a name that will work with the script
     */
    private String nameSafeFsName(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (ALLOWED_FS_CHARS.matcher(Character.toString(c)).matches()) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private void createHardLink(Path link, Path existing) {
        try {
            Files.createLink(link, existing);
        } catch (IOException e) {
            throw new BackupException("Failed to create hard link from " + existing + " to " + link, e);
        }
    }
}
