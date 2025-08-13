package dk.mada.backup.cli;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;

/**
 * Converts sizes with suffixes to actual number.
 */
public final class HumanSizeInputConverter implements ITypeConverter<Long> {
    /** One binary kilo. */
    private static final long ONE_K = 1024L;
    /** Accepted input patterns. */
    private static final Pattern VALID_INPUT_PATTERN = Pattern.compile("(\\d+)([kmgKMG]?)");

    /** Creates new instance. */
    public HumanSizeInputConverter() {
        // silence sonarcloud
    }

    @Override
    public Long convert(String inValue) {
        String value = inValue.replace("_", "");
        long multiplier = 1;

        Matcher m = VALID_INPUT_PATTERN.matcher(value);
        if (!m.matches()) {
            throw new IllegalArgumentException("'" + value + "' is not valid input for pattern " + VALID_INPUT_PATTERN);
        }
        long base = Long.parseLong(m.group(1));
        String mod = m.group(2);
        if (mod != null) {
            multiplier = switch (mod.toUpperCase(Locale.ROOT)) {
                case "K" -> ONE_K;
                case "M" -> ONE_K * ONE_K;
                case "G" -> ONE_K * ONE_K * ONE_K;
                case "" -> 1;
                default -> throw new IllegalStateException("Unexpected modifier: '" + mod + "'");
            };
        }
        return base * multiplier;
    }
}
