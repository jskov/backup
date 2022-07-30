package dk.mada.backup.digest;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A SHA-256 checksum.
 *
 * @param checksum the checksum, optionally prefixed with "sha256:"
 */
public record Sha256sum(String checksum) {
    /** Pattern for a valid SHA-256 checksum. */
    private static final Pattern CHECKSUM_PATTERN = Pattern.compile("[0-9a-f]{64}");

    /**
     * Constructs a new instance, verifying that the format is valid.
     *
     * @param checksum the checksum, optionally prefixed with "sha256:"
     */
    public Sha256sum(String checksum) {
        String lc = Objects.requireNonNull(checksum).toLowerCase().replaceAll("^sha256:", "");
        if (!CHECKSUM_PATTERN.matcher(lc).matches()) {
            throw new IllegalArgumentException("Invalid SHA256 sum: " + checksum);
        }
        this.checksum = lc;
    }
}
