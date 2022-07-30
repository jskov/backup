package dk.mada.fixture;

import java.util.List;

import dk.mada.backup.digest.Sha256sum;

/**
 * Code to parse lines of info output from script.
 */
public class InfoParser {
    /** Number of parts expected to be extracted from an info line. */
    private static final int INFO_PARTS = 3;

    /**
     * Backup entry information from the 'info' command output.
     *
     * @param filename the name of the file
     * @param checksum the checksum of the file
     * @param size     the size of the file
     **/
    public record Info(String filename, Sha256sum checksum, long size) { }

    /**
     * Parses 'info' command output into structured data.
     *
     * @param txt the command output text
     * @return a list of info for the backup entries
     */
    public List<Info> parse(String txt) {
        return txt.lines()
                .map(this::parseLine)
                .toList();
    }

    private Info parseLine(String l) {
        String[] parts = l.split(" +", INFO_PARTS);
        if (parts.length != 3) {
            throw new IllegalStateException("Failed to parse line: '" + l + "'");
        }
        String filename = parts[0];
        Sha256sum checksum = new Sha256sum(parts[1]);
        long size = Long.parseLong(parts[2]);
        return new Info(filename, checksum, size);
    }
}
