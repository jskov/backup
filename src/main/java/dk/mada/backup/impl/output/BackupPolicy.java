package dk.mada.backup.impl.output;

import java.nio.file.Path;

import dk.mada.backup.api.BackupArguments.Limits;
import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.gpg.GpgEncrypterException;
import dk.mada.backup.restore.RestoreScriptWriter;

/**
 * Defines the backup policy.
 */
public interface BackupPolicy {
    /** {@return the backup output type} */
    BackupOutputType outputType();

    /** {@return the backup name} */
    String backupName();

    /** {@return the source root directory} */
    Path rootDirectory();

    /** {@return the path of the restore script} */
    Path restoreScript();

    /** {@return the actual target directory to use} */
    Path targetDirectory();

    /** {@return the backup limits} */
    Limits limits();

    /** {@return the GPG information} */
    GpgStreamInfo gpgInfo();

    /**
     * Called when the backup is starting. This allows the policy implementation to check desired state before starting the
     * backup.
     */
    void backupPrep();

    /**
     * {@return the backup writer to use for the root elements}
     *
     * @throws GpgEncrypterException if GPG operations fail
     */
    BackupStreamWriter writer() throws GpgEncrypterException;

    /**
     * Called when the backup should be completed by writing the restore script.
     *
     * The policy handles the final output of the restore script.
     *
     * @param scriptWriter the prepared restore script writer
     * @return the new backup's restore script
     */
    Path completeBackup(RestoreScriptWriter scriptWriter);
}
