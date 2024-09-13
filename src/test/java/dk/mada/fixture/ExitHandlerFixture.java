package dk.mada.fixture;

import dk.mada.backup.impl.ExitHandler;

/**
 * Handles system exit by throwing an exception that can be caught by tests.
 *
 * When running tests, this would kill the Gradle daemon which it dislikes very much. So when test flag is set, throw an
 * exception instead.
 */
public final class ExitHandlerFixture {
    private ExitHandlerFixture() {
    }

    /** {@return a fixture for handling system exit in a testing context} */
    public static ExitHandler exitForTesting() {
        return new ExitHandler() {
            @Override
            public void systemExit(int exitCode, Throwable cause) {
                if (exitCode != 0) {
                    throw new IllegalStateException("Backup/restore failed, would system exit: " + exitCode, cause);
                }
            }

            @Override
            public void systemExitMessage(int exitCode, String message) {
                if (exitCode != 0) {
                    throw new IllegalStateException("Backup/restore failed, would system exit: " + exitCode + " with message: " + message);
                }
            }
        };
    }
}
