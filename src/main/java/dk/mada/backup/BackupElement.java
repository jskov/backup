package dk.mada.backup;

@FunctionalInterface
public interface BackupElement {
	String toBackupSummary();
}
