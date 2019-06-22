package dk.mada.backup;

import java.nio.file.Path;

public class BackupApi {

	public Path makeBackup(String backupName, Path sourceDir, Path targetDir) {
		Path restoreScript = targetDir.resolve(backupName + ".sh");
		Path archive = targetDir.resolve(backupName + ".tar");
		
		new MainExplore().packDir(sourceDir, archive, restoreScript);
		
		return restoreScript;
	}

}
