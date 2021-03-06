package dk.mada.backup.cli;

// From https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
public class HumanByteCount {
	public static String humanReadableByteCount(long bytes) {
		return humanReadableByteCountBin(bytes);

	}
	
	public static String humanReadableByteCountBin(long bytes) {
	    long b = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
	    return b < 1024L ? bytes + " B"
	            : b <= 0xfffccccccccccccL >> 40 ? String.format("%.1f KiB", bytes / 0x1p10)
	            : b <= 0xfffccccccccccccL >> 30 ? String.format("%.1f MiB", bytes / 0x1p20)
	            : b <= 0xfffccccccccccccL >> 20 ? String.format("%.1f GiB", bytes / 0x1p30)
	            : b <= 0xfffccccccccccccL >> 10 ? String.format("%.1f TiB", bytes / 0x1p40)
	            : b <= 0xfffccccccccccccL ? String.format("%.1f PiB", (bytes >> 10) / 0x1p40)
	            : String.format("%.1f EiB", (bytes >> 20) / 0x1p40);
	}
}
