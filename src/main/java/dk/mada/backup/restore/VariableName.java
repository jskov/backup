package dk.mada.backup.restore;

/**
 * The variables used in the restore script.
 */
public enum VariableName {
    /** The creation time of the backup. */
    BACKUP_DATE_TIME,
    /** The (original) size of the backed up data. */
    BACKUP_INPUT_SIZE,
    /** The GPG key used for encryption of the backup. */
    BACKUP_KEY_ID,
    /** The name of the backup. */
    BACKUP_NAME,
    /** Backup output type. Takes on values NAMED or NUMBERED. */
    BACKUP_OUTPUT_TYPE,
    /** The version of the format used for file data. */
    DATA_FORMAT_VERSION,
    /** Variable indexers for normal data lines. */
    VARS,
    /** Variable indexers for data lines containing MD5 information. */
    VARS_MD5,
    /** The version of the application that created the backup. */
    VERSION,
}
