package dk.mada.backup.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.beust.jcommander.IDefaultProvider;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.converters.PathConverter;

import dk.mada.backup.api.BackupApi;

/**
 * Argument definition for CLI invocation.
 */
public class CliArgs {
    private static final String OPT_MAX_SIZE = "--max-size";
    private static final String OPT_RECIPIENT = "-r";

    @Parameter(description = "source-dir target-dir", required = true)
    private List<String> inputOutput = new ArrayList<>();
    @Parameter(names = OPT_RECIPIENT, description = "GPG recipient key id", required = true, validateWith = GpgRecipientValidator.class)
    private String gpgRecipientId;
    @Parameter(names = "--help", description = "Print this help", help = true)
    private boolean help;
    @Parameter(names = { "-n", "--name" }, description = "Backup name (default to source folder name)")
    private String name;
    @Parameter(names = "--gpg-homedir", description = "Alternative GPG home dir", converter = PathConverter.class)
    private Path gpgHomeDir;
    @Parameter(names = "--skip-verify", description = "Skip verification after creating backup")
    private boolean skipVerify;
    @Parameter(names = OPT_MAX_SIZE, description = "Max file size", converter = HumanSizeInputConverter.class)
    private long maxFileSize;
    @Parameter(names = "--running-tests", description = "Used for testing to avoid System.exit")
    private boolean testingAvoidSystemExit;

    static class Defaults implements IDefaultProvider {
        @Override
        public String getDefaultValueFor(String optionName) {
            switch (optionName) {
            case OPT_RECIPIENT:
                return System.getenv("BACKUP_RECIPIENT");
            case OPT_MAX_SIZE:
                return Long.toString(BackupApi.DEFAULT_MAX_FILE_SIZE);
            default:
                return null;
            }
        }
    }

    /**
     * Validates the input/output directory arguments. These cannot be properly
     * validated via IParameterValidator
     */
    public void assertPositionalInput() {
        assertInputOutputCount();
        assertSourceDirValid();
        assertTargetValid();
    }

    public Path getSourceDir() {
        return Paths.get(inputOutput.get(0));
    }

    public Path getTargetDir() {
        return Paths.get(inputOutput.get(1));
    }

    public Optional<Path> getAlternativeGpgHome() {
        return Optional.ofNullable(gpgHomeDir);
    }

    public String getBackupName() {
        return name != null ? name : getSourceDir().getFileName().toString();
    }

    public String getGpgRecipientId() {
        return gpgRecipientId;
    }

    public boolean isSkipVerify() {
        return skipVerify;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public boolean isRunningTests() {
        return testingAvoidSystemExit;
    }

    private void assertInputOutputCount() {
        if (inputOutput.size() < 2) {
            throw new ParameterException("Both source-dir and target-dir must be specified!");
        }
        if (inputOutput.size() > 2) {
            throw new ParameterException("Only two positional arguments must be provided!");
        }
    }

    private void assertSourceDirValid() {
        if (!Files.isDirectory(getSourceDir())) {
            throw new ParameterException("The source-dir must be an existing directory!");
        }
    }

    private void assertTargetValid() {
        Path targetDir = getTargetDir();
        if (Files.notExists(targetDir) || Files.isDirectory(targetDir)) {
            return;
        }
        throw new ParameterException("target-dir must either not exist, or be a folder!");
    }
}
