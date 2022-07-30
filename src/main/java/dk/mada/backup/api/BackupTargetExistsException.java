package dk.mada.backup.api;

/**
 * Thrown if the backup operation needs to write to any existing files.
 */
public class BackupTargetExistsException extends BackupException {
    private static final long serialVersionUID = 6249807297965412874L;

    /**
     * Constructs new exception.
     *
     * @param message the exception message
     */
    public BackupTargetExistsException(String message) {
        super(message);
    }

    /**
     * Constructs new exception.
     *
     * @param cause the exception cause
     */
    public BackupTargetExistsException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs new exception.
     *
     * @param message the exception message
     * @param cause   the exception cause
     */
    public BackupTargetExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
