package dk.mada.backup.cli;

/**
 * Provide human readable numbers.
 *
 * From https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
 */
public class HumanByteCount {
    private HumanByteCount() {}

    /**
     * Convert number to value with suffix making it easier to read by
     * humans.
     *
     * @param number the number to convert
     * @return a string representing the number
     */
    public static String humanReadableByteCount(long number) { // NOSONAR - external
        long b = number == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(number);
        return b < 1024L ? number + " B"
                : b <= 0xfffccccccccccccL >> 40 ? String.format("%.1f KiB", number / 0x1p10) // NOSONAR - external
                        : b <= 0xfffccccccccccccL >> 30 ? String.format("%.1f MiB", number / 0x1p20) // NOSONAR - external
                                : b <= 0xfffccccccccccccL >> 20 ? String.format("%.1f GiB", number / 0x1p30) // NOSONAR - external
                                        : b <= 0xfffccccccccccccL >> 10 ? String.format("%.1f TiB", number / 0x1p40) // NOSONAR - external
                                                : b <= 0xfffccccccccccccL // NOSONAR - external
                                                        ? String.format("%.1f PiB", (number >> 10) / 0x1p40)
                                                        : String.format("%.1f EiB", (number >> 20) / 0x1p40);
    }
}
