package dk.mada.backup.api;

import dk.mada.backup.types.GpgId;
import java.nio.file.Path;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Arguments needed to create a backup.
 *
 * @param gpgRecipientKeyId    the GPG recipient key id used to encrypt the backup
 * @param envOverrides         the environment overrides to use when executing GPG
 * @param name                 the name of the backup (base name for all files)
 * @param sourceDir            the source directory of the backup (root of the backup)
 * @param targetDir            the folder to write backup output files to
 * @param repositoryDir        the folder to write an extra copy of the restore script to
 * @param repositoryScriptPath the path of the restore script in the repository
 * @param outputType           the backup output type
 * @param skipVerify           flag to skip verification of backup after its creation
 * @param limits               the backup limits
 */
public record BackupArguments(
        GpgId gpgRecipientKeyId,
        Map<String, String> envOverrides,
        String name,
        Path sourceDir,
        Path targetDir,
        @Nullable Path repositoryDir,
        Path repositoryScriptPath,
        BackupOutputType outputType,
        boolean skipVerify,
        Limits limits) {

    /**
     * Limits for the backup operation.
     *
     * @param maxRootElementSize the maximal archived size of a root element
     * @param numberedSplitSize  the split size for numbered backups
     */
    public record Limits(long maxRootElementSize, long numberedSplitSize) {}
}
