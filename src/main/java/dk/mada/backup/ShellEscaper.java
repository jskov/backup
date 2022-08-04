package dk.mada.backup;

/**
 * Escapes strings for shell.
 */
public final class ShellEscaper {
    private ShellEscaper() { }

    /**
     * Escape string to be safe for use in string.
     *
     * @param s the string to encode
     * @return s with problematic characters escaped
     */
    public static String toSafeShellString(String s) {
        return s.replace("\"", "\\\"").replace('`', '\'');
    }
}
