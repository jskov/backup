package dk.mada.fixture;

import java.nio.file.Path;

import dk.mada.backup.restore.RestoreExecutor;
import dk.mada.backup.restore.RestoreExecutor.Result;

/**
 * Runs a backup's restore script.
 */
public final class MakeRestore {
    private MakeRestore() {
    }

    /**
     * Run the restore script.
     *
     * @param script the script to run
     * @param args   the restore arguments
     * @return the restore result
     */
    public static Result runRestoreCmd(Path script, String... args) {
        return RestoreExecutor.runRestoreScript(script, TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES, args);
    }
}
