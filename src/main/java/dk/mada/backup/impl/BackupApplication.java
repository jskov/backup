package dk.mada.backup.impl;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.api.BackupApi;
import dk.mada.backup.api.BackupArguments;
import dk.mada.backup.api.BackupTargetExistsException;
import dk.mada.backup.cli.Console;
import dk.mada.backup.restore.RestoreExecutor;

/**
 * Backup implementation - drives backup and post-verification.
 */
public class BackupApplication {
    private static final Logger logger = LoggerFactory.getLogger(BackupApplication.class);

    /** The backup arguments. */
    private final BackupArguments args;

    /**
     * Create new instance.
     *
     * @param args the backup arguments
     */
    public BackupApplication(BackupArguments args) {
        this.args = args;
    }

    /**
     * Runs the backup application with the provided arguments.
     *
     * @param args the backup arguments
     */
    public static void run(BackupArguments args) {
        new BackupApplication(args).makeBackup();
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
    }

    private Path createBackup() {
        try {
            BackupApi backupApi = new BackupApi(args.gpgRecipientKeyId(), args.envOverrides(), args.maxFileSize());
            return backupApi.makeBackup(args.name(), args.sourceDir(), args.targetDir());
        } catch (BackupTargetExistsException e) {
            logger.info("Failed to create backup: {}", e.getMessage());
            logger.debug("Failure", e);
            systemExit(1);
            return null; // WTF?
        }
    }

    private void verifyBackup(Path script) {
        try {
            logger.info("Verifying backup...");
            RestoreExecutor.runRestoreScriptExitOnFail(args.testingAvoidSystemExit(), script, args.envOverrides(), "verify");
            RestoreExecutor.runRestoreScriptExitOnFail(args.testingAvoidSystemExit(), script, args.envOverrides(), "verify", "-s");
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

    /**
     * Handle system exit.
     *
     * When running tests, this would kill the Gradle daemon which it dislikes very
     * much. So when test flag is set, throw an exception instead.
     *
     * @param exitCode the code to exit with
     */
    private void systemExit(int exitCode) {
        if (args.testingAvoidSystemExit()) {
            throw new IllegalStateException("Backup/restore failed, would system exit: " + exitCode);
        }
        System.exit(exitCode);
    }
}
