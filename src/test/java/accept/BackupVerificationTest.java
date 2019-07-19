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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.examples.Expander;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;

import dk.mada.backup.cli.Main;
import fixture.DisplayNameCamelCase;
import fixture.TestCertificateInfo;

/**
 * Makes a backup, runs multiple checks on the restore of this backup.
 */
@DisplayNameGeneration(DisplayNameCamelCase.class)
class BackupVerificationTest {
	private static final FileTime ARBITRARY_KNOWN_TIME = FileTime.fromMillis(1561574109070L);
	private static Path restoreScript;

	@BeforeAll
	static void makeBackup() throws IOException, ArchiveException {
		Path srcDir = prepareTestInputTree("simple-input-tree");
		Path targetDir = Paths.get("build/backup-dest");
		
		org.assertj.core.util.Files.delete(targetDir.toFile());

		restoreScript = targetDir.resolve("test.sh");
		Main.main(new String[] {
					"-n", "test", 
					"-r", TestCertificateInfo.TEST_RECIPIEND_KEY_ID,
					"--gpg-homedir", TestCertificateInfo.ABS_TEST_GNUPG_HOME,
					srcDir.toAbsolutePath().toString(),
					targetDir.toAbsolutePath().toString()
				});
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
	 * Encrypted archive checksums is time-dependent. But content is constant.
	 */
	@Test
	void cryptContentStableOverTime() throws IOException, InterruptedException {
		Process p = runRestoreCmd("info", "crypts");
		String output = readOutput(p);
		
		assertThat(p.waitFor())
			.isEqualTo(0);
		assertThat(output)
			.contains("test.tar");
	}

	/**
	 * Tests that the contained archives can be decrypted and verified.
	 */
	@Test
	void backupArchivesCanBeRestored() throws IOException, InterruptedException {
		Path restoreDir = Paths.get("build/backup-restored");
		org.assertj.core.util.Files.delete(restoreDir.toFile());

		Process p = runRestoreCmd("unpack", "-a", restoreDir.toAbsolutePath().toString());
		String output = readOutput(p);
		
		assertThat(p.waitFor())
			.isEqualTo(0);
		assertThat(output)
			.contains(" - (1/8) dir-a.tar... ok",
					" - (2/8) dir-b.tar... ok",
					" - (3/8) dir-c.tar... ok",
					" - (4/8) dir-d with space.tar... ok",
					" - (5/8) dir-e.tar... ok",
					" - (6/8) dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.tar... ok",
					" - (7/8) file-root1.bin... ok",
					" - (8/8) file-root2 with space.bin... ok",
					"Success!");
	}

	/**
	 * Tests that the full backup can be decrypted and verified.
	 */
	@Test
	void backupFilesCanBeRestored() throws IOException, InterruptedException {
		Path restoreDir = Paths.get("build/backup-restored");
		org.assertj.core.util.Files.delete(restoreDir.toFile());

		Process p = runRestoreCmd("unpack", restoreDir.toAbsolutePath().toString());
		String output = readOutput(p);
		
		assertThat(output)
			.contains(" - (1/7) dir-a/file-a1.bin... ok",
					" - (2/7) dir-a/file-a2.bin... ok",
					" - (3/7) dir-b/file-b1.bin... ok",
					" - (4/7) dir-c/file-c-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.bin... ok",
					" - (5/7) dir-d with space/file-d1.bin... ok",
					" - (6/7) dir-e/file-e with space.bin... ok",
					" - (7/7) dir-long-name-1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890/file-long1.bin... ok",
					"Success!");

		assertThat(p.exitValue())
			.isEqualTo(0);
	}

	/**
	 * Tests that the files in the archive can be verified by streaming.
	 */
	@Test
	void backupFilesCanBeVerifiedByStream() throws IOException, InterruptedException {
		Process p = runRestoreCmd("verify", "-s");
		String output = readOutput(p);
		
		assertThat(output)
			.contains("All files verified ok.");
		
		assertThat(p.exitValue())
			.isEqualTo(0);
	}

	/**
	 * Tests that the a faulty file in the backup set
	 * can be found by the streaming verifier.
	 * 
	 * Done by breaking the checksum in the restore script before running
	 * verify.
	 */
	@Test
	void brokenBackupFilesCanBeFoundByStreamVerifier() throws IOException, InterruptedException {
		// replace last 4 chars of checksum with "dead"
		Path badRestoreScript = restoreScript.getParent().resolve("bad.sh");
		String withBrokenChecksum = Files.readAllLines(restoreScript).stream()
			.map(s -> s.replaceAll("....,dir-b/file-b1.bin", "dead,dir-b/file-b1.bin"))
			.collect(Collectors.joining("\n"));
		Files.writeString(badRestoreScript, withBrokenChecksum);
		
		Process p = runRestoreCmd(badRestoreScript, "verify", "-s");
		String output = readOutput(p);
		
		assertThat(output)
			.contains("Did not find matching checksum for file 'dir-b/file-b1.bin'");

		assertThat(p.exitValue())
			.isNotEqualTo(0);
	}

	private Process runRestoreCmd(String... args) throws IOException {
		return runRestoreCmd(restoreScript, args);
	}
	
	private Process runRestoreCmd(Path script, String... args) throws IOException {
		List<String> cmd = new ArrayList<>(List.of("/bin/bash", script.toAbsolutePath().toString()));
		cmd.addAll(List.of(args));
		ProcessBuilder pb = new ProcessBuilder(cmd)
				.directory(restoreScript.getParent().toFile())
				.redirectErrorStream(true);
		
		pb.environment().putAll(TestCertificateInfo.TEST_KEY_ENVIRONMENT_OVERRIDES);
		
		return pb.start();
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