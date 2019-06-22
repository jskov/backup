package dk.mada.backup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.restore.RestoreScriptWriter;

public class MainExplore {
	private static final Logger logger = LoggerFactory.getLogger(MainExplore.class);
	private Path rootDir;
	
	private List<DirInfo> fileElements = new ArrayList<>();
	
	
	private void runCmdLine(List<String> dirs) {
		if (dirs.size() != 1) {
			System.out.println("One arg: dir");
			System.exit(1);
		}
		packTestOut(Paths.get(dirs.get(0)));
	}
	
	private void packTestOut(Path dir) {
		Path archive = Paths.get("/opt/work-photos/_backup.tar");
		Path restoreScript = Paths.get("/opt/work-photos/_backup_restore.sh");
		packDir(dir, archive, restoreScript);
	}
	
	public void packDir(Path dir, Path archive, Path restoreScript) {
		rootDir = dir;
		if (!Files.isDirectory(rootDir)) {
			throw new IllegalArgumentException("Must be dir, was " + rootDir);
		}
		
		pack(archive, restoreScript);
	}
	
	private void pack(Path archive, Path restoreScript) {
		logger.info("Create archive {}", archive);
		
		try {
			Files.createDirectories(archive.getParent());
			Files.createDirectories(restoreScript.getParent());
		} catch (IOException e1) {
			throw new IllegalStateException("Failed to create target dir", e1);
		}
		
		List<BackupElement> archiveElements;
		try (Stream<Path> files = Files.list(rootDir);
				 OutputStream os = Files.newOutputStream(archive);
				 BufferedOutputStream bos = new BufferedOutputStream(os);
				TarArchiveOutputStream tarOs = new TarArchiveOutputStream(bos)) {

			archiveElements = files
				.sorted(filenameSorter())
				.map(p -> processRootElement(tarOs, p))
				.collect(Collectors.toList());
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
		logger.info("Process {}", p);
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
			
			FileInfo res = copyToTar(tempArchiveFile, inArchiveName, tarOs);
			
			Files.delete(tempArchiveFile);
			
			return res;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
		
	private DirInfo createArchiveFromDir(Path dir, Path archive) {
		try (OutputStream os = Files.newOutputStream(archive);
				BufferedOutputStream bos = new BufferedOutputStream(os);
				TarArchiveOutputStream tarForDirOs = new TarArchiveOutputStream(bos)) {
			
			logger.info("Creating nested archive for {}", dir);
			
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
	
	private FileInfo copyToTar(Path file, TarArchiveOutputStream tos) {
		String archivePath = rootDir.relativize(file).toString();
		return copyToTar(file, archivePath, tos);
	}
	
	private FileInfo copyToTar(Path file, String inArchiveName, TarArchiveOutputStream tos) {
		byte[] buffer = new byte[8192];

		try (InputStream is = Files.newInputStream(file); BufferedInputStream bis = new BufferedInputStream(is)) {
			long size = Files.size(file);
			
			logger.info("Adding entry - {} {} bytes", inArchiveName, size);
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

	public static void main(String[] args) {
		new MainExplore().runCmdLine(Arrays.asList(args));
	}
}
