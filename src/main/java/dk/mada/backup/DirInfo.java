package dk.mada.backup;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class DirInfo implements BackupElement {
    private final String pathName;
    private final List<FileInfo> files;

    private DirInfo(String pathName, List<FileInfo> files) {
        this.pathName = pathName;
        this.files = files;
    }

    public static DirInfo from(Path rootDir, Path dir, List<FileInfo> files) {
        return new DirInfo(rootDir.relativize(dir).toString(), files);
    }

    public String getPathName() {
        return pathName;
    }

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
