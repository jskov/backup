package dk.mada.backup;

/**
 * An element of a backup.
 */
@FunctionalInterface
public interface BackupElement {
    /** {@return a summary about the element} */
    String toBackupSummary();
}
