package dk.mada.backup.cli;

/**
 * Provide human readable numbers.
 *
 * From https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
 */
public final class HumanByteCount {
    /** Magic hex float. */
    private static final double F0X1P40 = 0x1p40;
    /** Magic hex float. */
    private static final double F0X1P30 = 0x1p30;
    /** Magic hex float. */
    private static final double F0X1P20 = 0x1p20;
    /** Magic hex float. */
    private static final double F0X1P10 = 0x1p10;
    /** Magic number. */
    private static final int N10 = 10;
    /** Magic number. */
    private static final int N20 = 20;
    /** Magic number. */
    private static final int N30 = 30;
    /** Magic number. */
    private static final int N40 = 40;
    /** Magic number. */
    private static final long N1024L = 1024L;
    /** Magic hex number. */
    private static final long H0XFFFCCCCCCCCCCCC_L = 0xfffccccccccccccL;

    private HumanByteCount() {
    }

    /**
     * Convert number to value with suffix making it easier to read by humans.
     *
     * @param number the number to convert
     * @return a string representing the number
     */
    public static String humanReadableByteCount(long number) { // NOSONAR - external
        long b = number == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(number);
        return b < N1024L ? number + " B"
                : b <= H0XFFFCCCCCCCCCCCC_L >> N40 ? String.format("%.1f KiB", number / F0X1P10) // NOSONAR - external
                        : b <= H0XFFFCCCCCCCCCCCC_L >> N30 ? String.format("%.1f MiB", number / F0X1P20) // NOSONAR - external
                                : b <= H0XFFFCCCCCCCCCCCC_L >> N20 ? String.format("%.1f GiB", number / F0X1P30) // NOSONAR - external
                                        : b <= H0XFFFCCCCCCCCCCCC_L >> N10 ? String.format("%.1f TiB", number / F0X1P40) // NOSONAR
                                                : b <= H0XFFFCCCCCCCCCCCC_L // NOSONAR - external
                                                        ? String.format("%.1f PiB", (number >> N10) / F0X1P40)
                                                        : String.format("%.1f EiB", (number >> N20) / F0X1P40);
    }
}
