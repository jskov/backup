package dk.mada.backup.api;

/**
 * Thrown if the backup operation needs to write to any existing files.
 */
public class BackupTargetExistsException extends BackupException {
	public BackupTargetExistsException(String message) {
		super(message);
	}
	
	public BackupTargetExistsException(Throwable cause) {
		super(cause);
	}

	public BackupTargetExistsException(String message, Throwable cause) {
		super(message, cause);
	}
}
