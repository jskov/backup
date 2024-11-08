package dk.mada.backup.impl.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.api.BackupArguments.Limits;
import dk.mada.backup.api.BackupOutputType;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.gpg.GpgEncrypterException;
import dk.mada.backup.restore.RestoreScriptReader;
import dk.mada.backup.restore.RestoreScriptWriter;
import dk.mada.backup.restore.RestoreScriptReader.RestoreScriptData;

/**
 * Policy for a output per (named) root-element.
 *
 * Allows making incremental backups easier.
 */
public final class NamedBackupPolicy implements BackupPolicy {
    private static final Logger logger = LoggerFactory.getLogger(NamedBackupPolicy.class);
    /** Time to wait for backup validation. */
    private static final int BACKUP_VALIDATION_TIMEOUT_SECONDS = 30;
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
    /** Data from backup being updated. */
    @SuppressWarnings("unused")
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
        return targetDir.resolve(name + ".sh");
    }

    @Override
    public Path targetDirectory() {
        return targetDir;
    }

    @Override
    public BackupStreamWriter writer() throws GpgEncrypterException {
        throw new UnsupportedOperationException("Implementation not ready yet");
    }

    @Override
    public void backupPrep() {
        assertExistingBackupIsValid();

        try {
            Files.createDirectories(targetDir);
        } catch (IOException e1) {
            throw new IllegalStateException("Failed to create target dir", e1);
        }
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

    @Override
    public Path completeBackup(RestoreScriptWriter scriptWriter) {
        scriptWriter.write(restoreScript());
        // TODO: move hardlinks around
        return restoreScript();
    }
}
