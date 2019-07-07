package dk.mada.backup.cli;

import java.util.Collections;
import java.util.Map;

import com.beust.jcommander.JCommander;

import dk.mada.backup.BackupApi;
import fixture.TestCertificateInfo;

/**
 * Main method for CLI invocation.
 */
public class Main {
	private CliArgs cliArgs;

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
	}

	private void run() {
		Map<String, String> envOverrides = cliArgs.getAlternativeGpgHome()
			.map(TestCertificateInfo::makeEnvOverrideForGnuPgpHome)
			.orElse(Collections.emptyMap());
		
		BackupApi backupApi = new BackupApi(cliArgs.getGpgRecipientId(), envOverrides);
		backupApi.makeBackup(cliArgs.getBackupName(), cliArgs.getSourceDir(), cliArgs.getTargetDir());
	}
	
	public static void main(String[] args) {
		new Main(args).run();
	}
}
