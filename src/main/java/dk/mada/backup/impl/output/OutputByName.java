package dk.mada.backup.impl.output;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.FileInfo;
import dk.mada.backup.api.BackupTargetExistsException;
import dk.mada.backup.gpg.GpgEncryptedOutputStream;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.impl.output.TarContainerBuilder.Entry;
import dk.mada.backup.restore.RestoreScriptReader.DataArchiveV2;
import dk.mada.backup.restore.RestoreScriptReader.RestoreScriptData;

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
    private final InternalBufferStream inMemoryBufferStream;
    /** The name of the file currently being created. */
    @Nullable private String workingOnFileName;

//    /** The current file being written to. */
//    @Nullable private OutputStream currentFileStream = null;
//    /** The GPG stream writing to the file. */
//    @Nullable private GpgEncryptedOutputStream eos;
    /** The tar container builder. */
    @Nullable private TarContainerBuilder tarBuilder;

    /**
     * Construct new instance.
     *
     * @param prevBackupData data about the previous backup
     * @param targetDir      the target directory of the new backup
     * @param gpgInfo        the GPG information
     */
    public OutputByName(RestoreScriptData prevBackupData, Path targetDir, GpgStreamInfo gpgInfo) {
        this.targetDir = targetDir;
        this.gpgInfo = gpgInfo;
        this.prevBackupData = prevBackupData;

        int TODO_maxContainerSize = 200 * 1024 * 1024;
        inMemoryBufferStream = new InternalBufferStream(TODO_maxContainerSize);

        System.out.println("PREV: " + prevBackupData);
    }

    @Override
    public TarContainerBuilder processNextRootElement(String name) throws IOException {
        closeCurrentFileAndEncrypt();

        inMemoryBufferStream.reset();
        workingOnFileName = name;

        tarBuilder = new TarContainerBuilder(inMemoryBufferStream);
        return tarBuilder;
    }

    // XXXX: next

    // $ rm -rf /tmp/xx ; ./backup-t --skip-verify /opt/ebooks /tmp/xx
    // real 0m16,599s
    // user 0m18,932s
    // sys 0m2,673s

    // FIXME:NEXT: test writing tar just to internal buffer and see what speed change there is - if any

    // TODO: javadoc
    // TODO:
    // close current tar archive
    // compare captured checksum with existing backup info
    // iff same, skip encrypt && replace
    private void closeCurrentFileAndEncrypt() throws IOException {
        if (tarBuilder == null || workingOnFileName == null) {
            return;
        }

        tarBuilder.close();
        Entry rootElementEntry = tarBuilder.firstEntry();
        tarBuilder = null;

        logger.info("Relative for {}", workingOnFileName);
        logger.info("Current input count: {}", inMemoryBufferStream.count());
        logger.info("Current input xxh3: {}", inMemoryBufferStream.xxh3());

//        logger.info("GOT: {}", rootElementEntry);
        String rootElementName = rootElementEntry.unwrappedFolderName();

        // Find the matching entry in the old backup set (if available)
        // If the old archive data matches the newly created archive data,
        // the encrypted file can be reused. Note that the encrypted data
        // cannot be used for comparison, because there is time variance
        // in these (even for the same input data).
        DataArchiveV2 oldArchive = prevBackupData.archivesV2().stream()
//                .peek(da -> logger.info(" see {}", da))
                .filter(da -> rootElementName.equals(da.name()))
                .findFirst()
                .orElse(null);
        if (oldArchive != null) {
            logger.info("Existing backup has entry for root element {}", rootElementName);
            if (oldArchive.size() == rootElementEntry.size()
                    && oldArchive.xxh3().equals(rootElementEntry.xxh3())) {
                logger.info(" - keeping");
                
                
                // FIXME: make new entry from old
                return;
            } else {
                logger.info(" - changed");
                // FIXME: update
            }
            // check prev archive xxh3 vs current
            logger.info("Old archive {}:{}:{}", oldArchive.name(), oldArchive.xxh3(), oldArchive.size());

//            if (oldArchive.name().equals(rootElementEntry.archiveName())
        } else {
            logger.info("No prior data for root element {}", rootElementName);
            // TODO:
            // Write out crypted container
        }

        // TODO:
        // Handle:
        // * adding new folder
        // * removing existing folder
        // * ensure reused encrypted archives use same certificate!

        // For output replacing:
        // 1. make new .update_temp folder
        // 2. new/changed files created there
        // reused files hardlinked to old archives (Files#createLink)
        // ...
        // N. move all current backup files to .prev-YYYYMMDD-HHMMSS
        // move all files from .update_temp to parent folder

        // HOWTO?
        // if updating single crypted files, and aborting the backup, the entire previous backup
        // will be compromised.
        // store replacements as _new_xxx and only replace at completion when writing new restore script?

        //

        // FIXME: NEXT: restore must iterate over named archives, not cat them

        // See above: write files to holders, move into place when all succeeds

        logger.info("------- Crypting archive to {}", workingOnFileName);

        try (OutputStream output = openNextFile(workingOnFileName);
                var eos = new GpgEncryptedOutputStream(output, gpgInfo)) {
            inMemoryBufferStream.writeTo(eos);
        }
    }

    @Override
    public void close() throws IOException {
        closeCurrentFileAndEncrypt();

        List<FileInfo> fileInfos = outputFiles.stream()
                .map(f -> FileInfo.fromCryptFile(targetDir, f))
                .toList();

        outputFilesFuture.complete(fileInfos);
    }

    @Override
    public Future<List<FileInfo>> getOutputFiles() {
        return outputFilesFuture;
    }

    /**
     * Prepare for streaming into the next encrypted file.
     *
     * FIXME: The data is written to a temporary file (in memory?) and only copied to the target iff: o target is missing o
     * target would be changed
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

        logger.debug("OPENING {}", outputFile);

        OutputStream fileOutput = Files.newOutputStream(outputFile, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
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
}
