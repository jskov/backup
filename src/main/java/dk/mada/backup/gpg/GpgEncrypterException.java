package dk.mada.backup.gpg;

import java.io.IOException;

public class GpgEncrypterException extends IOException {
	public GpgEncrypterException(String message) {
		super(message);
	}

	public GpgEncrypterException(String message, Exception cause) {
		super(message, cause);
	}
}
