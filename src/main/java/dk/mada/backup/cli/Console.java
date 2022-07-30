package dk.mada.backup.cli;

/**
 * Prints text to the user via the console.
 */
public final class Console {
    private Console() { }

    /**
     * Print a message with newline to the console.
     *
     * @param message the message to print.
     */
    public static void println(String message) {
        System.out.println(message); // NOSONAR - this is intentional
    }
}
