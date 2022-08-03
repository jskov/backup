package dk.mada.backup;

/**
 * Escapes strings for shell.
 */
public class ShellEscaper {
    private ShellEscaper() { }

    /**
     * Escape string to be safe for use in string.
     *
     * @param s the string to encode
     * @return s with " escaped
     */
    public static String toSafeShellString(String s) {
        return s.replace("\"", "\\\"");
    }
}
