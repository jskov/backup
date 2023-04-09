package dk.mada.backup.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

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
    description = "Makes a backup of a file tree. Results in a restore script plus a number of encrypted data files."
)
public final class CliMain implements Runnable {
    /** Name of option for max backup file size. */
    public static final String OPT_MAX_SIZE = "--max-size";
    /** Name of option for GPG recipient identity. */
    public static final String OPT_RECIPIENT = "-r";
    /** Name of option for repository directory. */
    public static final String OPT_REPOSITORY_DIR = "--repository";

    /** The picoCli spec. */
    @Nullable @Spec private CommandSpec spec;

    /** GPG recipient identity option. */
    @Option(names = OPT_RECIPIENT, required = true, converter = GpgRecipientConverter.class,
            description = "GPG recipient key id", paramLabel = "ID")
    @Nullable private GpgId gpgRecipientId;
    /** Backup name option. */
    @Option(names = { "-n", "--name" },
            description = "backup name (default to source folder name)", paramLabel = "NAME")
    @Nullable private String backupName;
    /** GPG home dir option. */
    @Option(names = "--gpg-homedir", description = "alternative GPG home dir", paramLabel = "DIR")
    @Nullable private Path gpgHomeDir;
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
    @SuppressWarnings("UnusedVariable")
    private boolean printVersion;
    /** Flag to print help. */
    @Option(names = "--help", description = "print this help and exit", help = true)
    @SuppressWarnings("UnusedVariable")
    private boolean printHelp;
    /** Repository location. */
    @Option(names = OPT_REPOSITORY_DIR, description = "repository for restore scripts")
    @Nullable private Path repositoryDir;

    /** Backup source directory option. */
    @Parameters(index = "0", description = "backup source directory", paramLabel = "source-dir")
    @Nullable private Path sourceDir;
    /** Backup target directory option. */
    @Parameters(index = "1", description = "target directory", paramLabel = "target-dir")
    @Nullable private Path targetDir;

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
     * Runs backup application with processed CLI arguments.
     */
    @Override
    public void run() {
        backupApp.accept(buildBackupArguments());
    }

    /**
     * Build backup arguments from parsed CLI arguments.
     *
     * @return the backup arguments
     */
    public BackupArguments buildBackupArguments() {
        Path src = Objects.requireNonNull(sourceDir, "Source dir null");
        Path target = Objects.requireNonNull(targetDir, "Target dir null");
        Path realSrcDir = makeRealRelativeToCwd(src);
        if (!Files.isDirectory(realSrcDir)) {
            argumentFail("The source directory must be an existing directory! " + realSrcDir);
        }

        String srcDirName = realSrcDir.getFileName().toString();
        NameAjustment adjustment = ensureBackupName(src, srcDirName);

        backupName = adjustment.name();
        Path relativeTargetDir = makeRealRelativeToCwd(target.resolve(adjustment.targetPath()));
        if (Files.exists(relativeTargetDir) && !Files.isDirectory(relativeTargetDir)) {
            argumentFail("The target directory must either not exist, or be a folder!");
        }

        Map<String, String> envOverrides = Map.of();
        if (gpgHomeDir != null) {
            envOverrides = Map.of("GNUPGHOME", gpgHomeDir.toAbsolutePath().toString());
        }

        Path repositoryScriptPath = adjustment.targetPath().resolve(backupName + ".sh");

        return new BackupArguments(
                Objects.requireNonNull(gpgRecipientId, "GPG recipient id null"),
                envOverrides, backupName,
                realSrcDir, relativeTargetDir,
                Objects.requireNonNull(repositoryDir, "repository dir null"),
                repositoryScriptPath,
                maxFileSize, skipVerify, testingAvoidSystemExit);
    }

    private Path makeRealRelativeToCwd(Path dir) {
        if (dir.isAbsolute()) {
            return toRealPath(dir);
        } else {
            return toRealPath(envInputs.getCurrentWorkingDirectory().resolve(dir));
        }
    }

    /**
     * Name adjustment computed from the source folder.
     *
     * @param name the backup name
     * @param targetPath the extra target path
     */
    record NameAjustment(String name, Path targetPath) { }

    private NameAjustment ensureBackupName(Path relativeSrcDir, String srcDirName) {
        Path noTargetChange = Paths.get("");

        // User specified name always takes precedence
        if (backupName != null) {
            return new NameAjustment(backupName, noTargetChange);
        }

        // Trim initial ./
        if (relativeSrcDir.getNameCount() > 1
                && relativeSrcDir.startsWith(Paths.get("."))) {
            relativeSrcDir = relativeSrcDir.subpath(1, relativeSrcDir.getNameCount());
        }

        // Just use source folder name if
        //  - absolute path
        //  - single-element path
        //  - contains any relative elements
        if (relativeSrcDir.isAbsolute()
                || relativeSrcDir.getNameCount() == 1
                || containsRelativeElements(relativeSrcDir)) {
            return new NameAjustment(srcDirName, noTargetChange);
        }

        // If relative source directory, any extra path elements get included
        // in name and target directory
        String name = relativeSrcDir.toString().replace("/", "-");
        Path targetChange = relativeSrcDir.getParent();
        if (targetChange == null) {
            throw new IllegalArgumentException("Target directory " + relativeSrcDir + " has no parent");
        }
        return new NameAjustment(name, targetChange);
    }

    private boolean containsRelativeElements(Path p) {
        for (Iterator<Path> ix = p.iterator(); ix.hasNext();) {
            String el = ix.next().toString();
            if (".".equals(el) || "..".equals(el) || "~".equals(el)) {
                return true;
            }
        }
        return false;
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
        CommandLine cmdLine = Objects.requireNonNull(spec, "Missing spec").commandLine();
        throw new CommandLine.ParameterException(cmdLine, message);
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
