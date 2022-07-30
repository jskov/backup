package dk.mada.backup.cli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.beust.jcommander.IStringConverter;

public class HumanSizeInputConverter implements IStringConverter<Long> {
    private static final Pattern VALID_INPUT_PATTERN = Pattern.compile("(\\d+)([kmgKMG]?)");

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
            switch (mod.toUpperCase()) {
            case "K":
                multiplier = 1024;
                break;
            case "M":
                multiplier = 1024 * 1024L;
                break;
            case "G":
                multiplier = 1024 * 1024 * 1024L;
                break;
            case "":
                break;
            default:
                throw new IllegalStateException("Unexpected modifier: '" + mod + "'");
            }
        }
        return base * multiplier;
    }

}
