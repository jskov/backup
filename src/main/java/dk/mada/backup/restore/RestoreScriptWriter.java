package dk.mada.backup.restore;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dk.mada.backup.BackupElement;

/**
 * Copies out the restore script, replacing backup information as it goes.
 */
public class RestoreScriptWriter {

	public void write(Path script, List<? extends BackupElement> crypts, List<? extends BackupElement> tars, List<? extends BackupElement> files) {
		try (InputStream is = getClass().getResourceAsStream("/restore.sh");
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				BufferedWriter bw = Files.newBufferedWriter(script)) {
			String line;
			boolean ignoringSection = false;
			while ((line = br.readLine()) != null) {
				String addLine = line;
				if (line.startsWith("#BEGIN_")) {
					ignoringSection = true;
				} else if (line.startsWith("#END_CRYPTS")) {
					addLine = elementsToText(crypts);
					ignoringSection = false;
				} else if (line.startsWith("#END_ARCHIVES")) {
					addLine = elementsToText(tars);
					ignoringSection = false;
				} else if (line.startsWith("#END_FILES")) {
					addLine = elementsToText(files);
					ignoringSection = false;
				}
				
				if (!ignoringSection) {
					bw.write(addLine);
					bw.append('\n');
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to create restore script " + script, e);
		}
		
		makeScriptExecutable(script);
	}
	
	private void makeScriptExecutable(Path script) {
		try {
			Set<PosixFilePermission> perms = Files.getPosixFilePermissions(script);
			perms.add(PosixFilePermission.OTHERS_EXECUTE);
			perms.add(PosixFilePermission.GROUP_EXECUTE);
			perms.add(PosixFilePermission.OWNER_EXECUTE);
			Files.setPosixFilePermissions(script, perms);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to make script executable " + script, e);
		}
	}
	
	private String elementsToText(List<? extends BackupElement> elements) {
		return elements.stream()
				.map(e -> e.toBackupSummary())
				.collect(Collectors.joining("\n"));
	}
}
