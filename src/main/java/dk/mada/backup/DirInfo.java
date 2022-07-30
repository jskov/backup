package dk.mada.backup;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Information about a directory to be included in the backup.
 */
public final class DirInfo implements BackupElement {
    /** Path of the directory, relative to the backup root folder. */
    private final String pathName;
    /** Information about files in the directory. */
    private final List<FileInfo> files;

    private DirInfo(String pathName, List<FileInfo> files) {
        this.pathName = pathName;
        this.files = files;
    }

    /**
     * Create new instance.
     *
     * @param rootDir the backup root directory
     * @param dir the directory this instance represents
     * @param files the file information for the directory
     * @return
     */
    public static DirInfo from(Path rootDir, Path dir, List<FileInfo> files) {
        return new DirInfo(rootDir.relativize(dir).toString(), files);
    }

    /** {@return the path name} */
    public String getPathName() {
        return pathName;
    }

    /** {@return the file information} */
    public List<FileInfo> getFiles() {
        return files;
    }

    @Override
    public String toString() {
        return "DirInfo [pathName=" + pathName + ", files=" + files + "]";
    }

    @Override
    public String toBackupSummary() {
        return files.stream()
                .map(BackupElement::toBackupSummary)
                .collect(Collectors.joining("\n"));
    }
}
