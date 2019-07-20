package dk.mada.backup.api;

/**
 * Parent exception for all backup application exceptions.
 */
public class BackupException extends RuntimeException {

	public BackupException(String message) {
		super(message);
	}

	public BackupException(Throwable cause) {
		super(cause);
	}

	public BackupException(String message, Throwable cause) {
		super(message, cause);
	}
}
