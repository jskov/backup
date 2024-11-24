package dk.mada.backup.api;

import java.nio.file.Path;

import dk.mada.backup.BackupCreator;
import dk.mada.backup.api.BackupArguments.Limits;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.impl.output.BackupPolicy;
import dk.mada.backup.impl.output.NamedBackupPolicy;
import dk.mada.backup.impl.output.NumberedBackupPolicy;

/**
 * API for the backup operation.
 */
public class BackupApi {
    /** The backup output type. */
    private BackupOutputType outputType;
    /** The GPG information. */
    private GpgStreamInfo gpgInfo;
    /** The backup limits. */
    private Limits limits;

    /**
     * Prepare backup with full configuration.
     *
     * @param gpgStreamInfo the GPG stream information.
     * @param outputType    the desired backup output type
     * @param limits        the backup limits
     */
    public BackupApi(GpgStreamInfo gpgStreamInfo, BackupOutputType outputType, Limits limits) {
        this.gpgInfo = gpgStreamInfo;
        this.outputType = outputType;
        this.limits = limits;
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
        BackupPolicy policy = switch (outputType) {
        case UNKNOWN -> throw new IllegalStateException("Need a valid type");
        case NUMBERED -> new NumberedBackupPolicy(backupName, gpgInfo, limits, sourceDir, targetDir);
        case NAMED -> new NamedBackupPolicy(backupName, gpgInfo, limits, sourceDir, targetDir);
        };

        return new BackupCreator(policy).create();
    }
}
