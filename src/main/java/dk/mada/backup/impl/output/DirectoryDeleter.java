package dk.mada.backup.impl.output;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import dk.mada.backup.api.BackupException;

/**
 * Deletes a directory.
 */
public final class DirectoryDeleter {
    private DirectoryDeleter() {
        // empty
    }

    /**
     * Deletes directory if it exists.
     *
     * @param dir directory to delete
     */
    public static void delete(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }

        try {
            Files.walkFileTree(dir, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new BackupException("Failed to delete directory " + dir, e);
        }
    }
}
