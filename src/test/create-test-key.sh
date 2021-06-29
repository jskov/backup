#!/bin/bash

set -e

rm -rf src/test/data/gpghome
mkdir -p src/test/data/gpghome

gpg --batch --homedir src/test/data/gpghome --passphrase '' --quick-generate-key testKey default default 1y
gpg --batch --homedir src/test/data/gpghome --output src/test/data/gpghome/exported-public-key.asc --armor --export testKey
gpg --batch --homedir src/test/data/gpghome --output src/test/data/gpghome/exported-secret-key.asc --armor --export-secret-key testKey
gpg --batch --homedir src/test/data/gpghome --export-ownertrust > src/test/data/gpghome/exported-owner-trust.asc

keyId=$(gpg --batch --homedir src/test/data/gpghome --with-colons -k | grep fpr | head -n1 | sed 's/:$//; s/.*://;')

sed -i "s/KEYID.*KEYID/KEYID\*\/\"${keyId}\"\/\*KEYID/;" src/test/java/dk/mada/fixture/TestCertificateInfo.java
