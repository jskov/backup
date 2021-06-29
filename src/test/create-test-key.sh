#!/bin/bash

set -e

rm -rf src/test/data/gpghome
mkdir -p src/test/data/gpghome

#/openpgp* src/test/data/gpghome/private-keys* src/test/data/gpghome/pubring* src/test/data/gpghome/trust*
keyId=$(gpg --batch --homedir src/test/data/gpghome --passphrase '' --quick-generate-key testKey default default 1y 2>&1 | grep "gpg: key " | sed 's/gpg: key //; s/ .*//;')



sed -i "s/KEYID.*KEYID/KEYID\*\/\"${keyId}\"\/\*KEYID/;" src/test/java/dk/mada/fixture/TestCertificateInfo.java