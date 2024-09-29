package dk.mada.backup.types;

import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A XXH3 64 bit hex value.
 *
 * @param value the XXHash checksum
 * @see <a href="https://github.com/Cyan4973/xxHash">https://github.com/Cyan4973/xxHash</a>
 */
public record Xxh3(long value) {
    /** Pattern for a valid XXH3 hash in string format. */
    private static final Pattern HASH_STRING_PATTERN = Pattern.compile("[0-9a-f]{16}");

    /**
     * Constructs a new instance from a hex string, verifying that the format is valid.
     *
     * @param hex the xxh3 in hex form
     * @return the new xxh3hash instance
     */
    public static Xxh3 ofHex(String hex) {
        String lc = Objects.requireNonNull(hex).toLowerCase(Locale.ROOT);
        if (!HASH_STRING_PATTERN.matcher(lc).matches()) {
            throw new IllegalArgumentException("Invalid XXH3 hash: " + hex);
        }
        return new Xxh3(HexFormat.fromHexDigitsToLong(lc));
    }

    /**
     * Constructs a new instance from a long.
     *
     * @param value the xxh3 in long form
     * @return the new xxh3hash instance
     */
    public static Xxh3 of(long value) {
        return new Xxh3(value);
    }

    @Override
    public final String toString() {
        return "xxh3[" + HexFormat.of().toHexDigits(value) + "]";
    }
}
