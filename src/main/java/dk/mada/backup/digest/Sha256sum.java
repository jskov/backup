package dk.mada.backup.digest;

import java.util.Objects;
import java.util.regex.Pattern;

public record Sha256sum(String checksum) {
    public static final Pattern CHECKSUM_PATTERN = Pattern.compile("[0-9a-f]{64}");
    
    public Sha256sum(String checksum) {
        String lc = Objects.requireNonNull(checksum).toLowerCase().replaceAll("^sha256:", "");
        if (!CHECKSUM_PATTERN.matcher(lc).matches()) {
            throw new IllegalArgumentException("Invalid SHA256 sum: " + checksum);
        }
        this.checksum = lc;
    }
}
