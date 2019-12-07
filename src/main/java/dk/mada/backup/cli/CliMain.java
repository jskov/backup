package dk.mada.backup.cli;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import dk.mada.backup.Version;
import dk.mada.backup.api.BackupApi;
import dk.mada.backup.api.BackupTargetExistsException;
import dk.mada.backup.restore.RestoreExecutor;

/**
 * Main method for CLI invocation.
 */
public class CliMain {
	private static final Logger logger = LoggerFactory.getLogger(CliMain.class);
	private CliArgs cliArgs;
	private Map<String, String> envOverrides;

	public CliMain(String[] args) {
		for (String a : args) {
			if ("--version".equals(a)) {
				System.out.println("Backup version " + Version.getBackupVersion());
				System.exit(0);
			}
		}

		cliArgs = new CliArgs();
		JCommander jc = JCommander.newBuilder()
			.addObject(cliArgs)
			.defaultProvider(new CliArgs.Defaults())
			.build();
		jc.setProgramName("backup");
		
		try {
			jc.parse(args);
			cliArgs.assertPositionalInput();
		} catch (ParameterException pe) {
			System.out.println("Bad input: " + pe.getMessage());
			jc.usage();
			System.exit(1);
		}
	
		envOverrides = cliArgs.getAlternativeGpgHome()
				.map(home -> Map.of("GNUPGHOME", home.toAbsolutePath().toString()))
				.orElse(Collections.emptyMap());
	}

	private void run() {
		Path restoreScript = makeBackup();
		
		if (cliArgs.isSkipVerify()) {
			logger.info("Backup *not* verified!");
		} else {
			verifyBackup(restoreScript);
		}
	}

	private Path makeBackup() {
		try {
			BackupApi backupApi = new BackupApi(cliArgs.getGpgRecipientId(), envOverrides, cliArgs.getMaxFileSize());
			return backupApi.makeBackup(cliArgs.getBackupName(), cliArgs.getSourceDir(), cliArgs.getTargetDir());
		} catch (BackupTargetExistsException e) {
			logger.info("Failed to create backup: {}", e.getMessage());
			logger.debug("Failure", e);
			System.exit(1);
			return null; // WTF?
		}
	}

	private void verifyBackup(Path script) {
		try {
			logger.info("Verifying backup...");
			RestoreExecutor.runRestoreScriptExitOnFail(script, envOverrides, "verify");
			RestoreExecutor.runRestoreScriptExitOnFail(script, envOverrides, "verify", "-s");
			logger.info("Backup verified.");
		} catch (Exception e) {
			throw new IllegalStateException("Failed to run verify script " + script, e);
		}
	}

	public static void main(String[] args) {
		new CliMain(args).run();
	}
}
