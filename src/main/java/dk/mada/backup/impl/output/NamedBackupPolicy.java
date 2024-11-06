package dk.mada.backup.impl.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.api.BackupArguments.Limits;
import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.gpg.GpgEncrypterException;
import dk.mada.backup.restore.RestoreScriptReader;
import dk.mada.backup.restore.RestoreScriptReader.RestoreScriptData;

/**
 * Policy for a output per (named) root-element.
 *
 * Allows making incremental backups easier.
 *
 * The new backup is created in three steps:
 *
 * 1) A sub-directory is created, named after the current (if any)
 *    backup set in the target directory. In this folder, hard links
 *    are made to the files of the current backup set. A valid-marker
 *    file is created to signal success.
 *
 * 2) A temporary sub-directory is made for the new backup set. To
 *    this folder, new backup files are written; if the match the old
 *    backup set, in the form of hard links. Otherwise as new files.
 *    This folder ensures that the old backup set remains valid should
 *    the new backup fail.
 *
 * 3) Finally, files in the target directory are deleted and the files
 *    of the new backup set are moved there (and the temporary directory
 *    deleted).
 *
 * This leaves a new backup set, with a sub-directory of named previous
 * states.
 */
public final class NamedBackupPolicy implements BackupPolicy {
    private static final Logger logger = LoggerFactory.getLogger(NamedBackupPolicy.class);
    /** Time to wait for backup validation. */
    private static final int BACKUP_VALIDATION_TIMEOUT_SECONDS = 30;
    /** The backup name. */
    private final String name;
    /** The target directory. The final location of the new backup set. */
    private final Path targetDir;
    /** The working target directory - where the new backup set is being constructed. */
    private final Path newTargetDir;
    /** The backup limits. */
    private final Limits limits;
    /** The GPG information. */
    private final GpgStreamInfo gpgInfo;
    /** The source root directory. */
    private final Path rootDir;
    /** Data from backup being updated. */
    @Nullable private RestoreScriptData oldBackupData;

    /**
     * Creates a new instance.
     *
     * @param name      the backup name
     * @param gpgInfo   the GPG information
     * @param limits    the backup limits
     * @param rootDir   the backup source root directory
     * @param targetDir the backup target directory
     */
    public NamedBackupPolicy(String name, GpgStreamInfo gpgInfo, Limits limits, Path rootDir, Path targetDir) {
        this.name = name;
        this.gpgInfo = gpgInfo;
        this.limits = limits;
        this.rootDir = rootDir;
        this.targetDir = targetDir;
        
        newTargetDir = targetDir.resolve(".new-set");
    }

    @Override
    public BackupOutputType outputType() {
        return BackupOutputType.NAMED;
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
        return targetDirectory().resolve(name + ".sh");
    }

    @Override
    public Path targetDirectory() {
        return targetDir;
    }

    @Override
    public BackupStreamWriter writer() throws GpgEncrypterException {
        RestoreScriptData oldData = Objects.requireNonNull(oldBackupData);
        return new OutputByName(oldData, targetDir, name, gpgInfo, restoreScript());
    }

    @Override
    public void backupPrep() {
        DirectoryDeleter.delete(newTargetDir);
        assertExistingBackupIsValid();
        createBackupClone();
    }

    private void assertExistingBackupIsValid() {
        Path restoreScript = restoreScript();
        if (Files.isRegularFile(restoreScript)) {
            try {
                Process p = new ProcessBuilder("bash", restoreScript.toString(), "verify")
                        .directory(targetDir.toFile())
                        .redirectErrorStream(true)
                        .start();
                p.waitFor(BACKUP_VALIDATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (p.exitValue() != 0) {
                    logger.warn("Validation failed:\n{}", output);
                    throw new IllegalStateException("Validation of old backup failed");
                }

                logger.info("Old backup validated OK\n{}", output);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to run verification of old backup " + restoreScript, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while running verification of old backup " + restoreScript, e);
            }

            oldBackupData = new RestoreScriptReader().readRestoreScriptData(restoreScript);
        } else {
            oldBackupData = RestoreScriptData.empty();
        }
    }

    private void createBackupClone() {
        // TODO: create clone folder, check for marker|delete, clone, set marker
    }
    
    @Override
    public void completeBackup() {
        // TODO: move hardlinks around
    }
}
