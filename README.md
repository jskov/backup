![](https://github.com/jskov/backup/workflows/Build%20and%20run%20tests/badge.svg)

# backup

Application that makes a secure backup of photo/music files.

The output is encrypted with GPG and includes a restore script that can verify backup integrity.


# Actions Build Pipeline

The hosted Actions build pipeline uses an Ubuntu 18.04.

To reproduce the Actions environment locally:

	$ ./gradlew makeActionsDockerImage
	$ podman run -i -t actions-backup
