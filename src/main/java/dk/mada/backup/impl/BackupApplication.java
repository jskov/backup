package dk.mada.backup.impl;

import dk.mada.backup.api.BackupApi;
import dk.mada.backup.api.BackupArguments;
import dk.mada.backup.cli.Console;
import dk.mada.backup.gpg.GpgEncryptedOutputStream.GpgStreamInfo;
import dk.mada.backup.restore.RestoreExecutor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backup implementation - drives backup and post-verification.
 */
public class BackupApplication {
    private static final Logger logger = LoggerFactory.getLogger(BackupApplication.class);

    /** The backup arguments. */
    private final BackupArguments args;
    /** The system exit handler. */
    private final ExitHandler exitHandler;

    /**
     * Create new instance.
     *
     * @param exitHandler the exit handler to use
     * @param args        the backup arguments
     */
    public BackupApplication(ExitHandler exitHandler, BackupArguments args) {
        this.exitHandler = exitHandler;
        this.args = args;
    }

    /**
     * Runs the backup application with the provided arguments.
     *
     * @param exitHandler the exit handler to use
     * @param args        the backup arguments
     */
    public static void run(ExitHandler exitHandler, BackupArguments args) {
        new BackupApplication(exitHandler, args).makeBackup();
    }

    /**
     * Makes backup from the provided arguments.
     */
    public void makeBackup() {
        Path restoreScript = createBackup();

        if (args.skipVerify()) {
            logger.info("Backup *not* verified!");
        } else {
            verifyBackup(restoreScript);
        }

        makeRepositoryCopy(restoreScript);
    }

    private void makeRepositoryCopy(Path restoreScript) {
        if (args.repositoryDir() != null) {
            Path repoFile = args.repositoryDir().resolve(args.repositoryScriptPath());
            logger.info("Writing restore script to {}", repoFile);
            try {
                Files.createDirectories(repoFile.getParent());
                Files.copy(restoreScript, repoFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to copy restore script to " + repoFile, e);
            }
        }
    }

    private Path createBackup() {
        try {
            GpgStreamInfo gpgStreamInfo = new GpgStreamInfo(args.gpgRecipientKeyId(), args.envOverrides());
            BackupApi backupApi = new BackupApi(gpgStreamInfo, args.outputType(), args.limits());
            return backupApi.makeBackup(args.name(), args.sourceDir(), args.targetDir());
        } catch (Exception e) {
            logger.info("Failed to create backup: {}", e.getMessage());
            logger.debug("Failure", e);
            exitHandler.systemExit(1, e);
            throw new IllegalStateException("Should not be necessary after systemExit?!");
        }
    }

    private void verifyBackup(Path script) {
        try {
            logger.info("Verifying backup...");
            String cryptVerifyOutput =
                    RestoreExecutor.runRestoreScriptExitOnFail(exitHandler, script, args.envOverrides(), "verify");
            logger.debug("encrypted files:\n{}", cryptVerifyOutput);
            String contentVerifyOutput = RestoreExecutor.runRestoreScriptExitOnFail(
                    exitHandler, script, args.envOverrides(), "verify", "-s");
            logger.debug("content files:\n{}", contentVerifyOutput);
            logger.info("Backup verified.");
        } catch (Exception e) {
            Console.println("");
            Console.println("**********************************************");
            Console.println("**  WARNING WARNING WARNING WARNING WARNING **");
            Console.println("**                                          **");
            Console.println("**   !Backup restore verification failed!   **");
            Console.println("**                                          **");
            Console.println("**  WARNING WARNING WARNING WARNING WARNING **");
            Console.println("**********************************************");
            Console.println("");
            throw new IllegalStateException("Failed to run verify script " + script, e);
        }
    }
}
