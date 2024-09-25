package dk.mada.backup.api;

import java.nio.file.Path;

import dk.mada.backup.MainExplore;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;

/**
 * API for the backup operation.
 */
public class BackupApi {
    /** Default backup file output size limit. */
    public static final long DEFAULT_MAX_CRYPT_FILE_SIZE = 1 * 1024 * 1024 * 1024L;
    /** The current implementation of the code. */
    private final MainExplore spikeCode;

    /**
     * Prepare backup with full configuration.
     *
     * @param gpgStreamInfo    the GPG stream information.
     * @param outputType       The desired backup output type
     * @param maxCryptFileSize Maximum crypt file output size.
     */
    public BackupApi(GpgStreamInfo gpgStreamInfo, BackupOutputType outputType, long maxCryptFileSize) {
        spikeCode = new MainExplore(gpgStreamInfo, outputType, maxCryptFileSize);
    }

    /**
     * Prepare backup with default size limit of 1GiB.
     *
     * @param gpgStreamInfo the GPG stream information.
     * @param outputType    The desired backup output type
     */
    public BackupApi(GpgStreamInfo gpgStreamInfo, BackupOutputType outputType) {
        this(gpgStreamInfo, outputType, DEFAULT_MAX_CRYPT_FILE_SIZE);
    }

    /**
     * Makes an encrypted backup.
     *
     * @param backupName Name of backup
     * @param sourceDir  Source directory
     * @param targetDir  Destination directory
     * @return Path of the restore script
     *
     * @throws BackupException or any of its subclasses, on failure
     */
    public Path makeBackup(String backupName, Path sourceDir, Path targetDir) {
        return spikeCode.packDir(sourceDir, targetDir, backupName);
    }
}
