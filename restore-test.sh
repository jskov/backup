#!/bin/bash

src=src/main/java/dk/mada/Restore.java
data=~/Documents/backup-restore-test
out=/tmp/big.java

cat $src | head -n $(cat $src | grep -n "@@DATA@@" | sed 's/:.*//') > $out

echo 'private static final List<String> CRYPTS = List.of(' >> $out
cat $data/crypts.txt | sed 's/"$/",/g' >> $out
echo '"");' >> $out

echo 'private static final List<String> ARCHIVES = List.of(' >> $out
cat $data/archives.txt | sed 's/"$/",/g' >> $out
echo '"");' >> $out

echo 'private static final List<String> FILES = List.of(' >> $out
cat $data/files.txt | sed 's/"$/",/g' >> $out
echo '"");' >> $out

echo '}' >> $out

