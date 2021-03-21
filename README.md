![](https://github.com/jskov/backup/workflows/Build%20and%20run%20tests/badge.svg)

# backup

Application that makes a secure backup of photo/music files.

The output is encrypted with GPG and includes a restore script that can verify backup integrity.


# Actions Build Pipeline

The hosted Actions build pipeline uses an Ubuntu 18.04.

To reproduce the Actions environment locally:

	$ ./gradlew makeActionsDockerImage
	$ podman run -i -t actions-backup
	

# Jotta Verification

With [Jotta](https://www.jottacloud.com/en/) it is possible to get MD5 sums of stored files like this:

```console
$ jotta-cli ls -l Archive/backup/music
Name                           Size                          Checksum                LastModified  
-------------------------  --------  --------------------------------  --------------------------  
music-2021.03.07-01.crypt   1.00GiB  7fcf5071496b4d2a6aa981caf9adbec8  2021-03-07-T12:37:20Z+0100  
...
```

The `verify -j path` option allows the backup info in the cloud to be verified by comparing the file checksums.

