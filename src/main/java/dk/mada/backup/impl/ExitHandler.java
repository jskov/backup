package dk.mada.backup.impl;

import dk.mada.backup.cli.Console;

/**
 * Handles system exit.
 *
 * Can be replaced by mock for testing.
 */
public class ExitHandler {
    /**
     * Handle system exit.
     *
     * @param exitCode the code to exit with
     */
    public void systemExit(int exitCode) {
        System.exit(exitCode);
    }

    /**
     * Handle system exit, printing message to console.
     *
     * @param exitCode the code to exit with
     * @param message  the message to print on the console
     */
    public void systemExit(int exitCode, String message) {
        Console.println(message);
        System.exit(exitCode);
    }
}
