package dk.mada.backup.api;

import java.nio.file.Path;

import dk.mada.backup.MainExplore;
import dk.mada.backup.api.BackupArguments.Limits;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;

/**
 * API for the backup operation.
 */
public class BackupApi {
    /** The current implementation of the code. */
    private final MainExplore spikeCode;

    /**
     * Prepare backup with full configuration.
     *
     * @param gpgStreamInfo the GPG stream information.
     * @param outputType    the desired backup output type
     * @param limits        the backup limits
     */
    public BackupApi(GpgStreamInfo gpgStreamInfo, BackupOutputType outputType, Limits limits) {
        spikeCode = new MainExplore(gpgStreamInfo, outputType, limits);
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
