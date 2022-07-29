package dk.mada.backup.gpg;

import java.io.IOException;

/**
 * Wraps failures caused by the external GPG process.
 */
public class GpgEncrypterException extends IOException {
	private static final long serialVersionUID = 2738638113041439082L;

    /**
     * Constructs new exception.
     *
     * @param message the exception message
     */
    public GpgEncrypterException(String message) {
		super(message);
	}

    /**
     * Constructs new exception.
     *
     * @param message the exception message
     * @param cause the exception cause
     */
	public GpgEncrypterException(String message, Exception cause) {
		super(message, cause);
	}
}
