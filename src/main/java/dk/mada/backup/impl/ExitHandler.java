package dk.mada.backup.impl;

import dk.mada.backup.cli.Console;
import org.jspecify.annotations.Nullable;

/**
 * Handles system exit.
 *
 * Can be replaced by mock for testing.
 */
public class ExitHandler {
    /** Creates new instance. */
    public ExitHandler() {
        // silence sonarcloud
    }

    /**
     * Handle system exit.
     *
     * @param exitCode the code to exit with
     * @param cause    the cause of the exit, or null
     */
    public void systemExit(int exitCode, @Nullable Throwable cause) {
        System.exit(exitCode);
    }

    /**
     * Handle system exit, printing message to console.
     *
     * @param exitCode the code to exit with
     * @param message  the message to print on the console
     */
    public void systemExitMessage(int exitCode, String message) {
        Console.println(message);
        System.exit(exitCode);
    }
}
