package dk.mada.backup.cli;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import com.beust.jcommander.JCommander;

import dk.mada.backup.api.BackupApi;
import dk.mada.backup.restore.RestoreExecutor;

/**
 * Main method for CLI invocation.
 */
public class Main {
	private CliArgs cliArgs;
	private Map<String, String> envOverrides;

	public Main(String[] args) {
		cliArgs = new CliArgs();
		JCommander jc = JCommander.newBuilder()
			.addObject(cliArgs)
			.build();
		jc.setProgramName("backup");
		jc.parse(args);
		
		if (!cliArgs.isInputOutputValid()) {
			jc.usage();
			System.exit(1);
		}

		envOverrides = cliArgs.getAlternativeGpgHome()
				.map(home -> Map.of("GNUPGHOME", home.toAbsolutePath().toString()))
				.orElse(Collections.emptyMap());
	}

	private void run() {
		BackupApi backupApi = new BackupApi(cliArgs.getGpgRecipientId(), envOverrides);
		Path restoreScript = backupApi.makeBackup(cliArgs.getBackupName(), cliArgs.getSourceDir(), cliArgs.getTargetDir());
		
		if (cliArgs.isSkipVerify()) {
			System.out.println("Backup *not* verified!");
		} else {
			verifyBackup(restoreScript);
		}
	}

	private void verifyBackup(Path script) {
		try {
			System.out.println("Verifying backup...");
			RestoreExecutor.runRestoreScriptExitOnFail(script, envOverrides, "verify");
			RestoreExecutor.runRestoreScriptExitOnFail(script, envOverrides, "verify", "-s");
			System.out.println("Backup verified.");
		} catch (Exception e) {
			throw new IllegalStateException("Failed to run verify script " + script, e);
		}
	}

	public static void main(String[] args) {
		new Main(args).run();
	}
}
