package dk.mada.backup.api;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import dk.mada.backup.MainExplore;

public class BackupApi {
	private final MainExplore spikeCode;

	public BackupApi(String gpgRecipientKeyId, Map<String, String> gpgEnvOverrides) {
		spikeCode = new MainExplore(gpgRecipientKeyId, gpgEnvOverrides);
	}
	
	public BackupApi(String recipientKeyId) {
		this(recipientKeyId, Collections.emptyMap());
	}

	public Path makeBackup(String backupName, Path sourceDir, Path targetDir) {
		Path restoreScript = targetDir.resolve(backupName + ".sh");
		Path archive = targetDir.resolve(backupName + ".tar");
		
		spikeCode.packDir(sourceDir, archive, restoreScript);
		
		return restoreScript;
	}
}
