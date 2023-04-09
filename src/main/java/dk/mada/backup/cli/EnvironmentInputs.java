package dk.mada.backup.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Inputs to the backup application from the environment.
 *
 * Besides making the external interaction more apparent, this class allows overrides from unit tests.
 */
public class EnvironmentInputs {

    /** {@return the environment value for BACKUP_RECIPIENT} */
    public String getBackupRecipient() {
        return System.getenv("BACKUP_RECIPIENT");
    }

    /** {@return the environment value for BACKUP_REPOSITORY_DIR} */
    public String getBackupRepositoryDir() {
        return System.getenv("BACKUP_REPOSITORY_DIR");
    }

    /** {@return the current working directory} */
    public Path getCurrentWorkingDirectory() {
        return Paths.get("").toAbsolutePath();
    }
}
