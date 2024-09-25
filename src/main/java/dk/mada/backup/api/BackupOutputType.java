package dk.mada.backup.api;

/**
 * Defines how the backup output is to be handled.
 */
public enum BackupOutputType {
    /**
     * The folder archives are streamed into one big tar file which is encrypted and split into numbered files of a
     * specified maximal size.
     */
    NUMBERED,
}
