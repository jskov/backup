package dk.mada.backup.types;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A GPG identity.
 *
 * @param id the GPG identity
 */
public record GpgId(String id) {
    /** Pattern for a valid GPG id. */
    private static final Pattern ID_PATTERN = Pattern.compile("[0-9a-f]{40}");

    /**
     * Constructs a new instance, verifying that the id form is valid.
     *
     * @param id the GPG identity
     */
    public GpgId(String id) {
        String lc = Objects.requireNonNull(id).toLowerCase();
        if (!ID_PATTERN.matcher(lc).matches()) {
            throw new IllegalArgumentException("Invalid GPG id: " + id);
        }
        this.id = lc;
    }
}
