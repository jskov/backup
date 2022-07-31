package dk.mada.backup.cli;

import dk.mada.backup.api.BackupApi;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Default arguments provider.
 */
public final class DefaultArgs implements IDefaultValueProvider {
    @Override
    public String defaultValue(ArgSpec arg) {
        if (isNamedOption(arg, CliMain.OPT_RECIPIENT)) {
            return System.getenv("BACKUP_RECIPIENT");
        }
        if (isNamedOption(arg, CliMain.OPT_MAX_SIZE)) {
            return Long.toString(BackupApi.DEFAULT_MAX_CRYPT_FILE_SIZE);
        }
        return null;
    }
        
    /**
     * Determine if argument spec has the specified name.
     *
     * @param argSpec spec to look for names in
     * @param name argument name to look for
     * @return true if the spec matches the name.
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
