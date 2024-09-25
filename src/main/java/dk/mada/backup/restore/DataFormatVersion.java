package dk.mada.backup.restore;

/**
 * The data formats used in the restore script.
 */
public enum DataFormatVersion {
    /** The data format is unknown/invalid. */
    VERSION_INVALID("0"),
    /** Uses SHA256 hash */
    VERSION_1("1"),
    /** Uses HHX3 hash */
    VERSION_2("2");

    private final String id;

    private DataFormatVersion(String id) {
        this.id = id;
    }

    /** {@return the id of this version} */
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }

    public static DataFormatVersion parse(String id) {
        for (DataFormatVersion e : DataFormatVersion.values()) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid data format version '" + id + "'");
    }
}
