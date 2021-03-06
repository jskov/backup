package dk.mada.backup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.cli.HumanByteCount;
import dk.mada.backup.gpg.GpgEncryptedOutputStream;
import dk.mada.backup.restore.RestoreScriptWriter;
import dk.mada.backup.restore.VariableName;
import dk.mada.backup.splitter.SplitterOutputStream;

public class MainExplore {
	private static final Logger logger = LoggerFactory.getLogger(MainExplore.class);
	private Path rootDir;
	
	private List<DirInfo> fileElements = new ArrayList<>();
	private final String recipientKeyId;
	private final Map<String, String> gpgEnvOverrides;
	private final long maxTarSize;
	
	private long totalInputSize;
	
	public MainExplore(String recipientKeyId, Map<String, String> gpgEnvOverrides, long maxTarSize) {
		this.recipientKeyId = recipientKeyId;
		this.gpgEnvOverrides = gpgEnvOverrides;
		this.maxTarSize = maxTarSize;
	}
	
	public Path packDir(Path srcDir, Path targetDir, String name) {
		rootDir = srcDir;
		if (!Files.isDirectory(rootDir)) {
			throw new IllegalArgumentException("Must be dir, was " + rootDir);
		}
		logger.info("Create backup from {}", srcDir);
		
		try {
			Files.createDirectories(targetDir);
		} catch (IOException e1) {
			throw new IllegalStateException("Failed to create target dir", e1);
		}
		
		List<BackupElement> archiveElements;
		Future<List<Path>> outputFilesFuture;
		try (Stream<Path> files = Files.list(rootDir);
			 SplitterOutputStream sos = new SplitterOutputStream(targetDir, name, ".crypt", maxTarSize);
			 GpgEncryptedOutputStream eos = new GpgEncryptedOutputStream(sos, recipientKeyId, gpgEnvOverrides);
			 TarArchiveOutputStream tarOs = makeTarOutputStream(eos)) {

			archiveElements = files
				.sorted(filenameSorter())
				.map(p -> processRootElement(tarOs, p))
				.collect(Collectors.toList());

			outputFilesFuture = sos.getOutputFiles();
			
			logger.info("Waiting for backup streaming to complete...");
		} catch (IOException e) {
			logger.warn("BAD", e);
			throw new IllegalStateException("Failed processing", e);
		}

		List<FileInfo> cryptElements;
		try {
			cryptElements = outputFilesFuture.get().stream()
				.map(archiveFile -> FileInfo.fromCryptFile(targetDir, archiveFile))
				.collect(Collectors.toList());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to lazy get output files", e);
		}
		
		String backupTime = LocalDateTime.now().format(new DateTimeFormatterBuilder()
															.appendPattern("Y.M.d H:m")
															.toFormatter());
		
		Path restoreScript = targetDir.resolve(name + ".sh");
		Map<VariableName, String> vars = Map.of(
				VariableName.VERSION, Version.getBackupVersion(),
				VariableName.BACKUP_DATE_TIME, backupTime,
				VariableName.BACKUP_NAME, name,
				VariableName.BACKUP_INPUT_SIZE, HumanByteCount.humanReadableByteCount(totalInputSize),
				VariableName.BACKUP_KEY_ID, recipientKeyId
				
		);
		new RestoreScriptWriter().write(restoreScript, vars, cryptElements, archiveElements, fileElements);
	
		return restoreScript;
	}

	private Comparator<? super Path> filenameSorter() {
		return (a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
	}
	
	private BackupElement processRootElement(TarArchiveOutputStream tarOs, Path p) {
		logger.debug("Process {}", p);
		if (Files.isDirectory(p)) {
			return processDir(tarOs, p);
		} else {
			return processFile(tarOs, p);
		}
	}

	private FileInfo processFile(TarArchiveOutputStream tarOs, Path file) {
		return copyToTar(file, tarOs);
		
	}
	private FileInfo processDir(TarArchiveOutputStream tarOs, Path dir) {
		try {
			Path tempArchiveFile = Files.createTempFile("backup", "tmp");
			DirInfo dirInfo = createArchiveFromDir(dir, tempArchiveFile);
			fileElements.add(dirInfo);
			
			String inArchiveName = dir.getFileName().toString() + ".tar";
			FileInfo res = copyTarBundleToTarResettingFileTime(tempArchiveFile, inArchiveName, tarOs);
			
			Files.delete(tempArchiveFile);
			return res;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// Note: destructs target - which is a temp file, so is OK
	private DirInfo createArchiveFromDir(Path dir, Path archive) {
		try (OutputStream os = Files.newOutputStream(archive, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
				BufferedOutputStream bos = new BufferedOutputStream(os);
				TarArchiveOutputStream tarForDirOs = makeTarOutputStream(bos)) {
			
			logger.debug("Creating nested archive for {}", dir);
			
			try (Stream<Path> files = Files.walk(dir)) {
				List<FileInfo> containedFiles = files
					.sorted(filenameSorter())
					.filter(Files::isRegularFile)
					.map(f -> copyToTar(f, tarForDirOs))
					.collect(Collectors.toList());
				
				return DirInfo.from(rootDir, dir, containedFiles);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static TarArchiveOutputStream makeTarOutputStream(OutputStream sink) {
		TarArchiveOutputStream taos = new TarArchiveOutputStream(sink);
		taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
		return taos;
	}
	
	private FileInfo copyToTar(Path file, TarArchiveOutputStream tos) {
		String archivePath = rootDir.relativize(file).toString();
		return copyToTar(file, archivePath, tos, true);
	}

	private FileInfo copyTarBundleToTarResettingFileTime(Path file, String inArchiveName, TarArchiveOutputStream tos) throws IOException {
		Files.setLastModifiedTime(file, FileTime.fromMillis(0));
		return copyToTar(file, inArchiveName, tos, false);
	}
	
	private FileInfo copyToTar(Path file, String inArchiveName, TarArchiveOutputStream tos, boolean countsTowardsSize) {
		byte[] buffer = new byte[8192];

		try (InputStream is = Files.newInputStream(file); BufferedInputStream bis = new BufferedInputStream(is)) {
			long size = Files.size(file);
			
			if (countsTowardsSize) {
				totalInputSize += size;
			}
			
			String type = inArchiveName.endsWith(".tar") ? "=>" : "-";
			String humanSize = HumanByteCount.humanReadableByteCount(size);
			logger.info(" {} {} {}", type, inArchiveName, humanSize);
			ArchiveEntry archiveEntry = tos.createArchiveEntry(file.toFile(), inArchiveName);
			tos.putArchiveEntry(archiveEntry);
			
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			int read;
			while ((read = bis.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
				tos.write(buffer, 0, read);
			}
			
			tos.closeArchiveEntry();
			
			return FileInfo.of(inArchiveName, size, digest);
			
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("No algo", e);
		}
	}
}
