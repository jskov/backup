The java parser stops reading from a file when it sees the character 0x1a (EOI, end-of-input).

This could be used to combine java program and data into one file, separated by this character.

But since shebang hacks are needed to get the path of the file anyway, might as well just split it two (by a proper string, not a special character). 

//#!/bin/env -S bash -x -c 'j="$(mktemp --suffix=.java)"; export BACKUP_DATA="$(mktemp)"; m=$(grep -E -n "^//EOI" "$0" | sed 's/:.*//') ; tail -n $m $0 | tail -n +2 > $j; tail -n +$m $0 > $BACKUP_DATA ;ls -l $d $j; java --source 25 $f "$@"'


//#!/bin/env -S bash -x -c 'j="$(mktemp --suffix=.java)"; export BACKUP_DATA="$(mktemp)"; m=$(grep -E -n "^//EOI" "$0" | sed "s/:.*//") ; tail -n $m "$0" | tail -n +2 > "$j"; tail -n +$m "$0" > "$BACKUP_DATA"; ls -l "$BACKUP_DATA" "$j"; java --source 25 "$j" "$@"'
