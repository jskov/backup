package dk.mada.backup;

/**
 * An element of a backup.
 */
public interface BackupElement {
    /** {@return the path for the element} */
    String path();
    /** {@return a summary about the element} */
    String toBackupSummary();
}
