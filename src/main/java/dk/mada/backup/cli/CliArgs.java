package dk.mada.backup.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.PathConverter;

/**
 * Argument definition for CLI invocation.
 */
public class CliArgs {
	@Parameter(names = "-r", description = "GPG recipient key id", required = true)
	private String gpgRecipientId;
	@Parameter(description = "source-dir target-dir", required = true)
	private List<String> inputOutput = new ArrayList<>();
	@Parameter(names = "--help", description = "Print this help", help = true)
	private boolean help;
	@Parameter(names = { "-n", "--name" }, description = "Backup name (default to source folder name)")
	private String name;
	@Parameter(names = "--gpg-homedir", description = "Alternative GPG home dir", converter = PathConverter.class)
	private Path gpgHomeDir;
	@Parameter(names = "--skip-verify")
	private boolean skipVerify;

	public boolean isInputOutputValid() {
		return isSourceDirValid() && isTargetValid();
	}
	
	private boolean isSourceDirValid() {
		return inputOutput.size() == 2
					&& Files.isDirectory(getSourceDir());
	}

	public Path getSourceDir() {
		return Paths.get(inputOutput.get(0));
	}
	
	private boolean isTargetValid() {
		Path targetDir = getTargetDir();
		return inputOutput.size() == 2
					&& (Files.isDirectory(targetDir) || Files.notExists(targetDir));
	}

	public Path getTargetDir() {
		return Paths.get(inputOutput.get(1));
	}
	
	public Optional<Path> getAlternativeGpgHome() {
		return Optional.ofNullable(gpgHomeDir);
	}
	
	public String getBackupName() {
		return name != null ? name : getSourceDir().getFileName().toString();
	}
	
	public String getGpgRecipientId() {
		return gpgRecipientId;
	}
	
	public boolean isSkipVerify() {
		return skipVerify;
	}
}