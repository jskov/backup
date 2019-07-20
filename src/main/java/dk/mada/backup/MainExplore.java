package dk.mada.backup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.api.BackupTargetExistsException;
import dk.mada.backup.cli.HumanByteCount;
import dk.mada.backup.gpg.GpgEncryptedOutputStream;
import dk.mada.backup.restore.RestoreScriptWriter;

public class MainExplore {
	private static final Logger logger = LoggerFactory.getLogger(MainExplore.class);
	private Path rootDir;
	
	private List<DirInfo> fileElements = new ArrayList<>();
	private final String recipientKeyId;
	private final Map<String, String> gpgEnvOverrides;
	
	public MainExplore(String recipientKeyId, Map<String, String> gpgEnvOverrides) {
		this.recipientKeyId = recipientKeyId;
		this.gpgEnvOverrides = gpgEnvOverrides;
	}
	
	public void packDir(Path dir, Path archive, Path restoreScript) {
		rootDir = dir;
		if (!Files.isDirectory(rootDir)) {
			throw new IllegalArgumentException("Must be dir, was " + rootDir);
		}
		
		pack(archive, restoreScript);
	}
	
	private void pack(Path archive, Path restoreScript) {
		if (Files.exists(archive)) {
			throw new BackupTargetExistsException("Archive file " + archive + " already exists!");
		}
		logger.info("Create archive {}", archive);
		
		try {
			Files.createDirectories(archive.getParent());
			Files.createDirectories(restoreScript.getParent());
		} catch (IOException e1) {
			throw new IllegalStateException("Failed to create target dir", e1);
		}
		
		List<BackupElement> archiveElements;
		try (Stream<Path> files = Files.list(rootDir);
				 OutputStream os = Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
				 BufferedOutputStream bos = new BufferedOutputStream(os);
				GpgEncryptedOutputStream eos = new GpgEncryptedOutputStream(bos, recipientKeyId, gpgEnvOverrides);
				TarArchiveOutputStream tarOs = makeTarOutputStream(eos)) {

			archiveElements = files
				.sorted(filenameSorter())
				.map(p -> processRootElement(tarOs, p))
				.collect(Collectors.toList());
			
			logger.info("Waiting for backup streaming to complete...");
		} catch (IOException e) {
			throw new IllegalStateException("Failed processing", e);
		}

		List<FileInfo> cryptElements = List.of(FileInfo.from(archive.getParent(), archive));
				
		new RestoreScriptWriter().write(restoreScript, cryptElements, archiveElements, fileElements);
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
			FileInfo res = copyToTarResettingFileTime(tempArchiveFile, inArchiveName, tarOs);
			
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
		return copyToTar(file, archivePath, tos);
	}

	private FileInfo copyToTarResettingFileTime(Path file, String inArchiveName, TarArchiveOutputStream tos) throws IOException {
		Files.setLastModifiedTime(file, FileTime.fromMillis(0));
		return copyToTar(file, inArchiveName, tos);
	}
	
	private FileInfo copyToTar(Path file, String inArchiveName, TarArchiveOutputStream tos) {
		byte[] buffer = new byte[8192];

		try (InputStream is = Files.newInputStream(file); BufferedInputStream bis = new BufferedInputStream(is)) {
			long size = Files.size(file);
			
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
			
			byte[] hash = digest.digest();
			BigInteger bigInt = new BigInteger(1, hash);
			StringBuilder sb = new StringBuilder(bigInt.toString(16));
			while (sb.length() < 64) {
				sb.insert(0, '0');
			}
			String checksum = sb.toString();

			return new FileInfo(inArchiveName, size, checksum);
			
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("No algo", e);
		}
	}
}
