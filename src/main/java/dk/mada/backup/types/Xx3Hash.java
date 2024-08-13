package dk.mada.backup.types;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A XXHash 64 bit hex value.
 *
 * @param hash the checksum, optionally prefixed with "sha256:"
 */
public record Xx3Hash(String hash) {
    /** Pattern for a valid XXH3 hash. */
    private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-f]{16}");

    /**
     * Constructs a new instance, verifying that the format is valid.
     *
     * @param hash the checksum, optionally prefixed with "sha256:"
     */
    public Xx3Hash(String hash) {
        String lc = Objects.requireNonNull(hash).toLowerCase(Locale.ROOT);
        if (!HASH_PATTERN.matcher(lc).matches()) {
            throw new IllegalArgumentException("Invalid XXH3 hash: " + hash);
        }
        this.hash = lc;
    }
}
