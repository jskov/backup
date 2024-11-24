package dk.mada.backup.api;

/**
 * Defines how the backup output is to be handled.
 */
public enum BackupOutputType {
    /** Unknown - null value, only used by parser. */
    UNKNOWN,
    /**
     * The folder archives are streamed into one big tar file which is encrypted and split into numbered files of a
     * specified maximal size.
     */
    NUMBERED,
    /**
     * The folder archives are individually encrypted and stored in separately named files.
     */
    NAMED;

    /**
     * Creates instance from a name.
     *
     * If no name matches, returns UNKNOWN.
     *
     * @param name the name of an output type
     * @return the matching output type, or UNKNOWN
     */
    public static BackupOutputType from(String name) {
        for (BackupOutputType t : values()) {
            if (t.name().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return UNKNOWN;
    }
}
