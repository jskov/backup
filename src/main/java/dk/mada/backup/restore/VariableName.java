package dk.mada.backup.restore;

/**
 * The variables used in the restore script.
 */
public enum VariableName {
    /** The creation time of the backup. */
    BACKUP_DATE_TIME,
    /** The GPG key used for encryption of the backup. */
    BACKUP_KEY_ID,
    /** The name of the backup. */
    BACKUP_NAME,
    /** The (original) size of the backed up data. */
    BACKUP_INPUT_SIZE,
    /** The version of the application that created the backup. */
    VERSION
}
