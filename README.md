![](https://github.com/jskov/backup/workflows/Build%20and%20run%20tests/badge.svg)

# backup

Application that makes a secure backup of photo/music files.

The output is crypted with GPG and includes a restore script that can verify backup integrity.


# Azure Build Pipeline

The hosted Azure build pipeline uses an ancient Ubuntu 16.04.

To reproduce the pain locally:

````
./gradlew makeAzureDockerImage
docker run -i -t azure-backup
````
