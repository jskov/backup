package dk.mada.backup.gpg;

public class GpgEncrypterException extends RuntimeException {
	public GpgEncrypterException(String message, Exception cause) {
		super(message, cause);
	}
}
