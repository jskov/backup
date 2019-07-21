package dk.mada.backup.api;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.mada.backup.MainExplore;

/**
 * API for the backup operation.
 */
public class BackupApi {
	private static final Logger logger = LoggerFactory.getLogger(BackupApi.class);
	private static final long DEFAULT_TAR_SIZE = 1*1024*1024*1024;
	private final MainExplore spikeCode;

	/**
	 * Prepare backup with full configuration.
	 * 
	 * @param gpgRecipientKeyId GPG recipient key id.
	 * @param gpgEnvOverrides Environment overrides (for testing).
	 * @param maxTarSize Maximum tar file output size.
	 */
	public BackupApi(String gpgRecipientKeyId, Map<String, String> gpgEnvOverrides, long maxTarSize) {
		spikeCode = new MainExplore(gpgRecipientKeyId, gpgEnvOverrides, maxTarSize);
	}

	/**
	 * Prepare backup with default size limit of 1GiB.
	 * 
	 * @param gpgRecipientKeyId GPG recipient key id.
	 * @param gpgEnvOverrides Environment overrides (for testing).
	 */
	public BackupApi(String gpgRecipientKeyId, Map<String, String> gpgEnvOverrides) {
		this(gpgRecipientKeyId, gpgEnvOverrides, DEFAULT_TAR_SIZE);
	}

	/**
	 * Prepare backup with default size limit of 1GiB and no extra environment settings.
	 * 
	 * @param gpgRecipientKeyId GPG recipient key id.
	 */
	public BackupApi(String recipientKeyId) {
		this(recipientKeyId, Collections.emptyMap());
	}

	/**
	 * Makes an encrypted backup.
	 * 
	 * @param backupName Name of backup
	 * @param sourceDir Source directory
	 * @param targetDir Destination directory
	 * @return Path of the restore script
	 * 
	 * @throws BackupException, or any of its subclasses, on failure
	 */
	public Path makeBackup(String backupName, Path sourceDir, Path targetDir) {
		try {
			return spikeCode.packDir(sourceDir, targetDir, backupName);
		} catch (Exception e) {
			logger.warn("Backup failed!", e);
			throw e;
		}
	}
}
