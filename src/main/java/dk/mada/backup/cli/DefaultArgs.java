package dk.mada.backup.cli;

import org.jspecify.annotations.Nullable;

import dk.mada.backup.api.BackupApi;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Default arguments provider.
 */
public final class DefaultArgs implements IDefaultValueProvider {
    /** The environment inputs. */
    private final EnvironmentInputs envInputs;

    /**
     * Constructs a new instance with default
     * environment inputs.
     */
    public DefaultArgs() {
        this(new EnvironmentInputs());
    }

    /**
     * Constructs a new instance.
     *
     * @param envInputs the environment inputs
     */
    public DefaultArgs(EnvironmentInputs envInputs) {
        this.envInputs = envInputs;
    }

    @Override
    @Nullable public String defaultValue(ArgSpec arg) {
        if (isNamedOption(arg, CliMain.OPT_RECIPIENT)) {
            return envInputs.getBackupRecipient();
        }
        if (isNamedOption(arg, CliMain.OPT_MAX_SIZE)) {
            return Long.toString(BackupApi.DEFAULT_MAX_CRYPT_FILE_SIZE);
        }
        if (isNamedOption(arg, CliMain.OPT_REPOSITORY_DIR)) {
            return envInputs.getBackupRepositoryDir();
        }
        return null;
    }

    /**
     * Determine if argument specification has the specified name.
     *
     * @param argSpec specification to look for names in
     * @param name argument name to look for
     * @return true if the specification matches the name.
     */
    private static boolean isNamedOption(ArgSpec argSpec, String name) {
        if (argSpec instanceof OptionSpec os) {
            for (String a : os.names()) {
                if (name.equals(a)) {
                    return true;
                }
            }
        }
        return false;
    }
}
