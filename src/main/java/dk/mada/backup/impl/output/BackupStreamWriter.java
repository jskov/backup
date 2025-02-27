package dk.mada.backup.impl.output;

import dk.mada.backup.FileInfo;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Backup stream writer.
 *
 * Opened once for the entire backup set. A tar output stream is provided for each root file element in the backup set.
 * At backup completion, a future with the resulting output files can be returned.
 */
public interface BackupStreamWriter extends AutoCloseable {

    /**
     * Process next root element in the backup set.
     *
     * @param name the name of the file or directory
     * @return the tar container builder to stream contents into
     * @throws IOException if IO fails
     */
    TarContainerBuilder processNextRootElement(String name) throws IOException;

    @Override
    void close() throws IOException;

    /** {@return a future containing information about the encrypted output files} */
    Future<List<FileInfo>> getOutputFiles();
}
