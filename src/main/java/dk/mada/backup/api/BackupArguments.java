package dk.mada.backup.api;

import java.nio.file.Path;
import java.util.Map;

import dk.mada.backup.types.GpgId;

/**
 * Arguments needed to create a backup.
 *
 * @param gpgRecipientKeyId      the GPG recipient key id used to encrypt the backup
 * @param envOverrides           the environment overrides to use when executing GPG
 * @param name                   the name of the backup (base name for all files)
 * @param sourceDir              the source directory of the backup (root of the backup)
 * @param targetDir              the folder to write backup output files to
 * @param repositoryDir          the folder to write an extra copy of the restore script to
 * @param repositoryScriptPath   the path of the restore script in the repository
 * @param maxFileSize            the maximum backup output file size
 * @param skipVerify             flag to skip verification of backup after its creation
 * @param testingAvoidSystemExit flag to skip system exit - used in testing
 */
public record BackupArguments(
        GpgId gpgRecipientKeyId,
        Map<String, String> envOverrides,
        String name,
        Path sourceDir,
        Path targetDir,
        Path repositoryDir,
        Path repositoryScriptPath,
        long maxFileSize,
        boolean skipVerify,
        boolean testingAvoidSystemExit) {
}
