package dk.mada.backup.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.Version;
import dk.mada.backup.api.BackupApi;
import dk.mada.backup.api.BackupTargetExistsException;
import dk.mada.backup.restore.RestoreExecutor;
import dk.mada.backup.types.GpgId;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Main class for command line invocation.
 */
@Command(
    name = "mba",
    header = "",
    mixinStandardHelpOptions = true,
    versionProvider = Version.class,
    defaultValueProvider = DefaultArgs.class,
    description = "Makes a backup of a file tree. Results in a restore script plus a number of encrypted data files."
)
public final class CliMain implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(CliMain.class);

    /** Name of option for max backup file size. */
    public static final String OPT_MAX_SIZE = "--max-size";
    /** Name of option for GPG recipient identity. */
    public static final String OPT_RECIPIENT = "-r";

    /** The picoCli spec. */
    @Spec private CommandSpec spec;

    /** GPG recipient identity option. */
    @Option(names = OPT_RECIPIENT, required = true, converter = GpgRecipientConverter.class,
            description = "GPG recipient key id", paramLabel = "ID")
    private GpgId gpgRecipientId;
    /** Backup name option. */
    @Option(names = { "-n", "--name" },
            description = "backup name (default to source folder name)", paramLabel = "NAME")
    private String backupName;
    /** GPG home dir option. */
    @Option(names = "--gpg-homedir", description = "alternative GPG home dir", paramLabel = "DIR")
    private Path gpgHomeDir;
    /** Flag to skip verification after backup has been created. */
    @Option(names = "--skip-verify", description = "skip verification after creating backup")
    private boolean skipVerify;
    /** Maximum backup file size option. */
    @Option(names = OPT_MAX_SIZE, converter = HumanSizeInputConverter.class,
            description = "max file size", paramLabel = "SIZE")
    private long maxFileSize;
    /** Flag to signal invocation from tests. */
    @Option(names = "--running-tests", hidden = true, description = "used for testing to avoid System.exit")
    private boolean testingAvoidSystemExit;
    /** Flag to print version. */
    @Option(names = { "-V", "--version" }, versionHelp = true, description = "print version information and exit")
    private boolean printVersion;
    /** Flag to print help. */
    @Option(names = "--help", description = "print this help and exit", help = true)
    private boolean printHelp;

    /** Backup source directory option. */
    @Parameters(index = "0", description = "backup source directory", paramLabel = "source-dir")
    private Path sourceDir;
    /** Backup target directory option. */
    @Parameters(index = "1", description = "target directory", paramLabel = "target-dir")
    private Path targetDir;

    /**
     * Creates new instance for a single invocation from CLI.
     */
    public Integer call() {
        if (!Files.isDirectory(sourceDir)) {
            argumentFail("The source directory must be an existing directory!");
        }
        if (Files.exists(targetDir) && !Files.isDirectory(targetDir)) {
            argumentFail("The target directory must either not exist, or be a folder!");
        }
        ensureBackupName();

        Map<String, String> envOverrides = Map.of();
        if (gpgHomeDir != null) {
            envOverrides = Map.of("GNUPGHOME", gpgHomeDir.toAbsolutePath().toString());
        }

        Path restoreScript = makeBackup(envOverrides);

        if (skipVerify) {
            logger.info("Backup *not* verified!");
        } else {
            verifyBackup(envOverrides, restoreScript);
        }

        return 0;
    }

    private void ensureBackupName() {
        if (backupName == null) {
            backupName = sourceDir.getFileName().toString();
        }
    }

    private void argumentFail(String message) {
        throw new CommandLine.ParameterException(spec.commandLine(), message);
    }

    private Path makeBackup(Map<String, String> envOverrides) {
        try {
            BackupApi backupApi = new BackupApi(gpgRecipientId, envOverrides, maxFileSize);
            return backupApi.makeBackup(backupName, sourceDir, targetDir);
        } catch (BackupTargetExistsException e) {
            logger.info("Failed to create backup: {}", e.getMessage());
            logger.debug("Failure", e);
            systemExit(1);
            return null; // WTF?
        }
    }

    private void verifyBackup(Map<String, String> envOverrides, Path script) {
        try {
            logger.info("Verifying backup...");
            RestoreExecutor.runRestoreScriptExitOnFail(testingAvoidSystemExit, script, envOverrides, "verify");
            RestoreExecutor.runRestoreScriptExitOnFail(testingAvoidSystemExit, script, envOverrides, "verify", "-s");
            logger.info("Backup verified.");
        } catch (Exception e) {
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
        if (testingAvoidSystemExit) {
            throw new IllegalStateException("Backup/restore failed, would system exit: " + exitCode);
        }
        System.exit(exitCode);
    }

    /**
     * CLI main entry.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new CliMain()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
        // otherwise just fall through
    }
}
