package dk.mada.backup.api;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import dk.mada.backup.MainExplore;

/**
 * API for the backup operation.
 */
public class BackupApi {
	private final MainExplore spikeCode;

	public BackupApi(String gpgRecipientKeyId, Map<String, String> gpgEnvOverrides) {
		spikeCode = new MainExplore(gpgRecipientKeyId, gpgEnvOverrides);
	}
	
	public BackupApi(String recipientKeyId) {
		this(recipientKeyId, Collections.emptyMap());
	}

	/**
	 * Makes an encrypted backup.
	 * 
	 * @param backupName Name of backup
	 * @param sourceDir Source directory
	 * @param targetDir Destination directory
	 * @return Path of the restore script
	 * 
	 * @throws BackupException, or any of its subclasses, on failure
	 */
	public Path makeBackup(String backupName, Path sourceDir, Path targetDir) {
		Path restoreScript = targetDir.resolve(backupName + ".sh");
		Path archive = targetDir.resolve(backupName + ".tar");
		
		spikeCode.packDir(sourceDir, archive, restoreScript);
		
		return restoreScript;
	}
}
