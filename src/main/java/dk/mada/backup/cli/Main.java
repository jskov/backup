package dk.mada.backup.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.beust.jcommander.JCommander;

import dk.mada.backup.BackupApi;

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
			runRestoreCmd(script, "verify");
			runRestoreCmd(script, "verify", "-s");
			System.out.println("Backup verified.");
		} catch (Exception e) {
			throw new IllegalStateException("Failed to run verify script " + script, e);
		}
	}
	
	private void runRestoreCmd(Path script, String... args) throws IOException, InterruptedException {
		List<String> cmd = new ArrayList<>(List.of("/bin/bash", script.toAbsolutePath().toString()));
		cmd.addAll(List.of(args));
		ProcessBuilder pb = new ProcessBuilder(cmd)
				.directory(script.getParent().toFile())
				.redirectErrorStream(true);
		
		pb.environment().putAll(envOverrides);
		
		Process p = pb.start();
		
		String output = readOutput(p);
		
		if (p.waitFor() != 0) {
			System.out.println("Failed to verify backup");
			System.out.println(output);
			System.exit(1);
		}
	}

	private String readOutput(Process p) throws IOException {
         try (InputStream in = p.getInputStream()) {
             return new String(in.readAllBytes());
         }
	}
	
	public static void main(String[] args) {
		new Main(args).run();
	}
}
