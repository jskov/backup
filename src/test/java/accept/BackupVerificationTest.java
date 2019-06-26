package accept;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.examples.Expander;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import dk.mada.backup.BackupApi;
import fixture.DisplayNameCamelCase;

/**
 * Makes a backup, runs multiple checks on the restore of this backup.
 */
@DisplayNameGeneration(DisplayNameCamelCase.class)
class BackupVerificationTest {
	private static final FileTime ARBITRARY_KNOWN_TIME = FileTime.fromMillis(1561574109070L);
	private static Path restoreScript;

	@BeforeAll
	static void makeBackup() throws IOException, ArchiveException {
		BackupApi backupApi = new BackupApi();
		
		Path srcDir = prepareTestInputTree("simple-input-tree");
		Path targetDir = Paths.get("build/backup-dest");
		restoreScript = backupApi.makeBackup("test", srcDir, targetDir);
	}

	/**
	 * Tests that the verification of the encrypted archive(s) works.
	 */
	@Test
	void backupCryptFilesCanBeVerified() throws IOException, InterruptedException {
		Process p = runRestoreCmd("verify");
		String output = readOutput(p);
		
		assertThat(p.waitFor())
			.isEqualTo(0);
		assertThat(output)
			.contains("(1/1) test.tar... ok");
	}

	/**
	 * (Middle) archive checksums should be unchanged over time, as long as
	 * the input (backup) files are not touched. I.e. wall clock time when
	 * the backup is made should not affect checksums.
	 * 
	 * The entire backup checksum should be stable over time.
	 */
	@Test
	void archiveChecksumsStableOverTime() throws IOException, InterruptedException {
		Process p = runRestoreCmd("info", "archives");
		String output = readOutput(p);
		
		assertThat(p.waitFor())
			.isEqualTo(0);
		assertThat(output)
			.contains(
					"dir-a.tar e42fa7a5806b41d4e1646ec1885e1f43bdbd9488465fa7022c1aa541ead9348f        2560",
					"dir-b.tar 628b2ef22626e6a2d74c4bf441cf6394d5db0bf149a4a98ee048b51d9ce69374        2048");
	}

	/**
	 * Encrypted archive checksums should be unchanged over time, as long as
	 * the input (backup) files are not touched. I.e. wall clock time when
	 * the backup is made should not affect checksums.
	 * 
	 * The entire backup checksum should be stable over time.
	 */
	@Test
	void cryptChecksumsStableOverTime() throws IOException, InterruptedException {
		Process p = runRestoreCmd("info", "crypts");
		String output = readOutput(p);
		
		assertThat(p.waitFor())
			.isEqualTo(0);
		assertThat(output)
			.contains("test.tar debe314578a07d713ffd56bb8cc029cc95622f7a5e821d866ebc118a164ff724        6656");
	}

	private Process runRestoreCmd(String... args) throws IOException {
		List<String> cmd = new ArrayList<>(List.of("/bin/bash", restoreScript.toAbsolutePath().toString()));
		cmd.addAll(List.of(args));
		return new ProcessBuilder(cmd)
				.directory(restoreScript.getParent().toFile())
				.redirectErrorStream(true)
				.start();
	}

	private String readOutput(Process p) throws IOException {
         try (InputStream in = p.getInputStream()) {
                 return new String(in.readAllBytes());
         }
	}

	private static Path prepareTestInputTree(String name) throws IOException, ArchiveException {
		Path srcDir = Paths.get("build/backup-src");
		Files.createDirectories(srcDir);
		
		Path tar = Paths.get("src/test/data").resolve(name+".tar");
		new Expander().expand(tar.toFile(), srcDir.toFile());
		setTimeOfTestFiles(srcDir);

		return srcDir.resolve(name);
	}
	
	private static void setTimeOfTestFiles(Path srcDir) throws IOException {
		try (Stream<Path> files = Files.walk(srcDir)) {
			files.forEach(p -> {
				try {
					Files.setLastModifiedTime(p, ARBITRARY_KNOWN_TIME);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}
}