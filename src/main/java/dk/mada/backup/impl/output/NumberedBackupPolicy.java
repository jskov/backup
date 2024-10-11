package dk.mada.backup.impl.output;

import java.nio.file.Path;

import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.api.BackupArguments.Limits;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.gpg.GpgEncrypterException;

/**
 * Policy for a split (and numbered) output.
 */
public final class NumberedBackupPolicy implements BackupPolicy {
    /** The backup name. */
    private final String name;
    /** The target directory. */
    private final Path targetDir;
    /** The backup limits. */
    private final Limits limits;
    /** The GPG information. */
    private final GpgStreamInfo gpgInfo;
    /** The source root directory. */
    private final Path rootDir;

    /**
     * Creates a new instance.
     *
     * @param name      the backup name
     * @param gpgInfo   the GPG information
     * @param limits    the backup limits
     * @param rootDir   the backup source root directory
     * @param targetDir the backup target directory
     */
    public NumberedBackupPolicy(String name, GpgStreamInfo gpgInfo, Limits limits, Path rootDir, Path targetDir) {
        this.name = name;
        this.gpgInfo = gpgInfo;
        this.limits = limits;
        this.rootDir = rootDir;
        this.targetDir = targetDir;
    }

    @Override
    public BackupOutputType outputType() {
        return BackupOutputType.NUMBERED;
    }

    @Override
    public GpgStreamInfo gpgInfo() {
        return gpgInfo;
    }

    @Override
    public String backupName() {
        return name;
    }

    @Override
    public Limits limits() {
        return limits;
    }

    @Override
    public Path rootDirectory() {
        return rootDir;
    }

    @Override
    public Path restoreScript() {
        return targetDir.resolve(name + ".sh");
    }

    @Override
    public Path targetDirectory() {
        return targetDir;
    }

    @Override
    public BackupStreamWriter writer() throws GpgEncrypterException {
        return new OutputBySize(targetDir, name, limits.numberedSplitSize(), gpgInfo);
    }

    @Override
    public void backupPrep() {
        // Nothing to prep
    }

    @Override
    public void completeBackup() {
        // Nothing to complete
    }
}
