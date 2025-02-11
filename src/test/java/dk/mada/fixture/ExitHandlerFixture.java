package dk.mada.fixture;

import dk.mada.backup.impl.ExitHandler;
import org.jspecify.annotations.Nullable;

/**
 * Handles system exit by throwing an exception that can be caught by tests.
 *
 * When running tests, this would kill the Gradle daemon which it dislikes very much. So when test flag is set, throw an
 * exception instead.
 */
public final class ExitHandlerFixture {
    private ExitHandlerFixture() {}

    /** {@return a fixture for handling system exit in a testing context} */
    public static ExitHandler exitForTesting() {
        return new ExitHandler() {
            @Override
            public void systemExit(int exitCode, @Nullable Throwable cause) {
                String message = "";
                if (cause != null) {
                    message = cause.getMessage();
                }
                if (exitCode != 0) {
                    throw new TestFailedWithException(
                            "Backup/restore failed, would system exit: " + exitCode + " with message: " + message,
                            cause);
                }
            }

            @Override
            public void systemExitMessage(int exitCode, String message) {
                if (exitCode != 0) {
                    throw new TestFailedWithMessage(
                            "Backup/restore failed, would system exit: " + exitCode + " with message: " + message,
                            message);
                }
            }
        };
    }

    /**
     * An exception describing backup failure throwing exception.
     */
    public static class TestFailedWithException extends RuntimeException {
        @java.io.Serial
        static final long serialVersionUID = 42L;

        public TestFailedWithException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * An exception describing backup failure with just exit message.
     */
    public static class TestFailedWithMessage extends RuntimeException {
        @java.io.Serial
        static final long serialVersionUID = 42L;

        private final String exitMessage;

        public TestFailedWithMessage(String message, String exitMessage) {
            super(message);
            this.exitMessage = exitMessage;
        }

        /** {@return the exit message} */
        public String exitMessage() {
            return exitMessage;
        }
    }
}
