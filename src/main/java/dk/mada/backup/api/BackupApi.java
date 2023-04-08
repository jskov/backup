package dk.mada.backup.api;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import dk.mada.backup.MainExplore;
import dk.mada.backup.types.GpgId;

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
     * @param gpgRecipientKeyId GPG recipient key id.
     * @param gpgEnvOverrides   Environment overrides (for testing).
     * @param maxCryptFileSize  Maximum crypt file output size.
     * @param clearUserGroup flag to clear user and group information from archive entries
     */
    public BackupApi(GpgId gpgRecipientKeyId, Map<String, String> gpgEnvOverrides, long maxCryptFileSize, boolean clearUserGroup) {
        spikeCode = new MainExplore(gpgRecipientKeyId, gpgEnvOverrides, maxCryptFileSize, clearUserGroup);
    }

    /**
     * Prepare backup with default size limit of 1GiB.
     *
     * @param gpgRecipientKeyId GPG recipient key id.
     * @param gpgEnvOverrides   Environment overrides (for testing).
     */
    public BackupApi(GpgId gpgRecipientKeyId, Map<String, String> gpgEnvOverrides) {
        this(gpgRecipientKeyId, gpgEnvOverrides, DEFAULT_MAX_CRYPT_FILE_SIZE, false);
    }

    /**
     * Prepare backup with default size limit of 1GiB and no extra environment
     * settings.
     *
     * @param gpgRecipientKeyId GPG recipient key id.
     */
    public BackupApi(GpgId gpgRecipientKeyId) {
        this(gpgRecipientKeyId, Collections.emptyMap());
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
