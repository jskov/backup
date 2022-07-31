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
    public static final String OPT_MAX_SIZE = "--max-size";
    public static final String OPT_RECIPIENT = "-r";
    private static final Logger logger = LoggerFactory.getLogger(CliMain.class);
    private Map<String, String> envOverrides;

    /** The picoCli spec. */
    @Spec private CommandSpec spec;

    // FIXME: make proper type: validateWith = GpgRecipientValidator.class
    @Option(names = OPT_RECIPIENT, description = "GPG recipient key id", required = true, paramLabel = "ID")
    private String gpgRecipientId;
    @Option(names = { "-n", "--name" }, description = "backup name (default to source folder name)", paramLabel = "NAME")
    private String backupName;
    @Option(names = "--gpg-homedir", description = "alternative GPG home dir", paramLabel = "DIR")
    private Path gpgHomeDir;
    @Option(names = "--skip-verify", description = "skip verification after creating backup")
    private boolean skipVerify;
    @Option(names = OPT_MAX_SIZE, description = "max file size", converter = HumanSizeInputConverter.class, paramLabel = "SIZE")
    private long maxFileSize;
    @Option(names = "--running-tests", description = "used for testing to avoid System.exit")
    private boolean testingAvoidSystemExit;
    @Option(names = { "-V", "--version" }, versionHelp = true, description = "print version information and exit")
    private boolean printVersion;
    @Option(names = "--help", description = "print this help and exit", help = true)
    private boolean printHelp;

    @Parameters(index = "0", description = "backup source directory", paramLabel = "source-dir")
    private Path sourceDir;
    @Parameters(index = "1", description = "target directory", paramLabel = "target-dir")
    private Path targetDir;

    /**
     * Creates new instance for a single invocation from CLI.
     *
     * @param args the command line arguments
     */
    public Integer call() {
        if (!Files.isDirectory(sourceDir)) {
            argumentFail("The source directory must be an existing directory!");
        }
        if (Files.exists(targetDir) && !Files.isDirectory(targetDir)) {
            argumentFail("The target directory must either not exist, or be a folder!");
        }

        if (gpgHomeDir == null) {
            envOverrides = Map.of();
        } else {
            envOverrides = Map.of("GNUPGHOME", gpgHomeDir.toAbsolutePath().toString());
        }
        
        Path restoreScript = makeBackup();

        if (skipVerify) {
            logger.info("Backup *not* verified!");
        } else {
            verifyBackup(restoreScript);
        }
        
        return 0;
    }

    private void argumentFail(String message) {
        throw new CommandLine.ParameterException(spec.commandLine(), message);
    }

    private Path makeBackup() {
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

    private void verifyBackup(Path script) {
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
