package dk.mada.backup.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import dk.mada.backup.Version;
import dk.mada.backup.api.BackupArguments;
import dk.mada.backup.impl.BackupApplication;
import dk.mada.backup.types.GpgId;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Main class for command line invocation.
 *
 * Contains argument handling, delegates actual
 * application execution to backup.
 */
@Command(
    name = "mba",
    header = "",
    mixinStandardHelpOptions = true,
    versionProvider = Version.class,
//    defaultValueProvider = DefaultArgs.class,
    description = "Makes a backup of a file tree. Results in a restore script plus a number of encrypted data files."
)
public final class CliMain implements Callable<Integer> {
    /** Name of option for max backup file size. */
    public static final String OPT_MAX_SIZE = "--max-size";
    /** Name of option for GPG recipient identity. */
    public static final String OPT_RECIPIENT = "-r";
    /** Name of option for repository directory. */
    public static final String OPT_REPOSITORY_DIR = "--repository";

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
    /** Repository location. */
    @Option(names = OPT_REPOSITORY_DIR, description = "repository for restore scripts")
    private Path repositoryDir;

    /** Backup source directory option. */
    @Parameters(index = "0", description = "backup source directory", paramLabel = "source-dir")
    private Path sourceDir;
    /** Backup target directory option. */
    @Parameters(index = "1", description = "target directory", paramLabel = "target-dir")
    private Path targetDir;

    /** The environment inputs. */
    private final EnvironmentInputs envInputs;
    /** The Backup application to execute after processing arguments. */
    private final Consumer<BackupArguments> backupApp;

    /**
     * Create a new instance.
     *
     * @param backupApp the backup application to execute
     */
    public CliMain(Consumer<BackupArguments> backupApp) {
        this(new EnvironmentInputs(), backupApp);
    }
    
    /**
     * Create a new instance.
     *
     * @param envInputs the environment inputs
     * @param backupApp the backup application to execute
     */
    public CliMain(EnvironmentInputs envInputs, Consumer<BackupArguments> backupApp) {
        this.envInputs = envInputs;
        this.backupApp = backupApp;
    }

    /**
     * Creates new instance for a single invocation from CLI.
     */
    public Integer call() {
        backupApp.accept(buildBackupArguments());
        return 0;
    }

    public BackupArguments buildBackupArguments() {
        Path relativeSrcDir = sourceDir;

        sourceDir = makeRelativeToCwd(sourceDir);
        targetDir = makeRelativeToCwd(targetDir);

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

        return new BackupArguments(gpgRecipientId, envOverrides, backupName, sourceDir, targetDir, maxFileSize, skipVerify, testingAvoidSystemExit);
    }
    
    private Path makeRelativeToCwd(Path dir) {
        if (dir.isAbsolute()) {
            return toRealPath(dir);
        } else {
            return toRealPath(envInputs.getCurrentWorkingDirectory().resolve(dir));
        }
    }

    private void ensureBackupName() {
        if (backupName == null) {
            backupName = toRealPath(sourceDir).getFileName().toString();
        }
    }

    private Path toRealPath(Path p) {
        try {
            if (Files.exists(p)) {
                return p.toRealPath();
            } else {
                return p;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to convert path " + p + " to real path", e);
        }
    }
    
    private void argumentFail(String message) {
        throw new CommandLine.ParameterException(spec.commandLine(), message);
    }

    /**
     * CLI main entry.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new CliMain(BackupApplication::run))
                .setDefaultValueProvider(new DefaultArgs())
                .execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
        // otherwise just fall through
    }
}
