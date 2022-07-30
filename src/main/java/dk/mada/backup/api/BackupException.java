package dk.mada.backup.api;

/**
 * Parent exception for all application exceptions.
 */
public class BackupException extends RuntimeException {
    private static final long serialVersionUID = -496389113410042025L;

    /**
	 * Constructs new exception.
	 *
	 * @param message the exception message
	 */
    public BackupException(String message) {
		super(message);
	}

    /**
     * Constructs new exception.
     *
     * @param cause the exception cause
     */
	public BackupException(Throwable cause) {
		super(cause);
	}

    /**
     * Constructs new exception.
     *
     * @param message the exception message
     * @param cause the exception cause
     */
	public BackupException(String message, Throwable cause) {
		super(message, cause);
	}
}
