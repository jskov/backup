package dk.mada.backup.types;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An MD5 128 bit hex value.
 *
 * @param value the MD5 hash value in hex form
 * @see <a href="https://en.wikipedia.org/wiki/MD5">https://en.wikipedia.org/wiki/MD5</a>
 */
public record Md5(String value) {
    /** Pattern for a valid MD5 hash in string format. */
    private static final Pattern HASH_STRING_PATTERN = Pattern.compile("[0-9a-f]{32}");

    /**
     * Validate the format.
     */
    public Md5 {
        String lc = Objects.requireNonNull(value).toLowerCase(Locale.ROOT);
        if (!HASH_STRING_PATTERN.matcher(lc).matches()) {
            throw new IllegalArgumentException("Invalid MD5 hash: " + value);
        }
    }

    /**
     * Constructs a new instance from a hex string, verifying that the format is valid.
     *
     * @param hex the MD5 hash in hex form
     * @return the new Md5 instance
     */
    public static Md5 ofHex(String hex) {
        return new Md5(hex);
    }

    /** {@return the MD5 hash as a hexadecimal string} */
    public String hex() {
        return value;
    }

    @Override
    public final String toString() {
        return "md5[" + hex() + "]";
    }
}
