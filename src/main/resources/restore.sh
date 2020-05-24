#!/bin/bash -e

crypts=(
#BEGIN_CRYPTS#
#END_CRYPTS#
)

archives=(
#BEGIN_ARCHIVES#
#END_ARCHIVES#
)


files=(
#BEGIN_FILES#
#END_FILES#
)


fail() {
    local msg="$1"

    >&2 echo -e $msg
    exit 1
}

expect_file() {
    local size="$1"
    local sha2="$2"
    local file="$3"
    local prefix="$4"

    echo -n " $prefix$file... "

    if [ ! -f "$file" ]; then
	fail "\nDid not find expected file $file"
    fi

    local actual_size=$(/usr/bin/stat -c "%s" "$file")
    if [ "$actual_size" -ne "$size" ]; then
	fail "\nFile $file has size $actual_size, but expected $size"
    fi

    local actual_sha2=$(/usr/bin/sha256sum "$file" | /usr/bin/cut -d' ' -f1)
    if [ "$actual_sha2" != "$sha2" ]; then
	fail "\nFile $file has sha256sum '$actual_sha2', but expected '$sha2'"
    fi

    echo "ok"
}


info_and_exit() {
	local sel=""
    if [ "$1" == "-c" ]; then
    	sel="crypts"
    elif [ "$1" == "-a" ]; then
    	sel="archives"
    elif [ "$1" == "-f" ]; then
    	sel="files"
    fi

    if [ "$sel" == "crypts" -o "$sel" == "archives" -o "$sel" == "files" ]; then
	    local name=$sel[@]
	    local array=("${!name}")
		for l in "${array[@]}"; do
		    local file=${l:77}
		    local size=${l:0:11}
		    local sha2=${l:12:64}
		    echo "${file} ${sha2} ${size}"
		done
	else
	    echo "Backup '@@BACKUP_NAME@@'"
	    echo " made with backup version @@VERSION@@"
	    echo " created on @@BACKUP_DATE_TIME@@"
	    echo " original size @@BACKUP_INPUT_SIZE@@"
	    echo " encrypted with key id @@BACKUP_KEY_ID@@"
	    echo " ${#crypts[@]} crypted archive(s) contains ${#files[@]} files in ${#archives[@]} nested archives"
    fi

    exit 0
}

usage_and_exit() {
    # usage
    echo "Usage:"
    echo " restore [cmd]"
    echo
    echo "With cmd being one of:"
    echo
    echo "  info               information about backup"
    echo "  info -c            information about crypted backup files"
    echo "  info -a            information about archive files"
    echo "  info -f            information about the original files"
    echo
    echo "  unpack dir         unpacks all files to dir"
    echo "  unpack -a dir      unpacks (only) archives to dir"
    echo
    echo "  verify             verifies crypted backup files"
    echo "  verify -a dir      verifies decrypted archive files in dir"
    echo "  verify -f dir      verifies decrypted and unpacked files in dir"
    echo "  verify -s          decrypts and verifies files via streaming - prompts password"

    exit 1
}

verify_files() {
    local name=$1[@]

    if [ $# -ne 2 ]; then
	fail "Verify archives expects one argument, the archives directory"
    fi
    local files_dir="$2"

    if [ ! -d "$files_dir" ]; then
	fail "Specified archives directory $files_dir is not a directory"
    fi

    local array=("${!name}")
    local len=${#array[@]}
    echo "Verifying integrity of archives in $files_dir..."

    local i=1
    for l in "${array[@]}"; do
	local size=${l:0:11}
	local sha2=${l:12:64}
	local file=${l:77}

	(cd $files_dir; expect_file "$size" "$sha2" "$file" "- ($i/$len) ")
	if [ "$?" != "0" ]; then
	    exit 1
	fi
	i=$((i + 1))
    done

    echo "Success!"
}

unpack() {
    local onlyArchives=false
    if [ "$1" == "-a" ]; then
	onlyArchives=true
	shift
    fi
    
    if [ $# -ne 1 ]; then
	fail "Unpack expects one argument, the destination directory"
    fi
    local target=$1

    if [ -e "$target" ]; then
	fail "Will not unpack to existing target $target"
    fi

    local crypt_files=
    for l in "${crypts[@]}"; do
	local size=${l:0:11}
	local sha2=${l:12:64}
	local file=${l:77}
	crypt_files="$crypt_files $file"
    done

    /bin/mkdir "$target"

    local gpg_cmd="/usr/bin/gpg -q --no-permission-warning -d"
    if $onlyArchives; then
	echo "Unpacking directory archives..."
	/bin/cat $crypt_files | $gpg_cmd | (cd "$target" && /bin/tar -x -f -)
	verify_files "archives" "$target"
    else
	echo "Unpacking full backup..."
	/bin/cat $crypt_files | $gpg_cmd | (cd "$target" && /bin/tar -x -f - --to-command='/bin/bash -c "[[ \"$TAR_FILENAME\" == *.tar ]] && /bin/tar -x -f - || /bin/cat > \"$TAR_FILENAME\""')
	verify_files "files" "$target"
    fi
    
}


verify_stream() {
    # Make a file with file/checksum lines for all (nested) files and root files
    local file_checksums=
    for l in "${files[@]}"; do
	local size=${l:0:11}
	local sha2=${l:12:64}
	local file=${l:77}
	file_checksums="$file_checksums$sha2,$file\n"
    done
    for l in "${archives[@]}"; do
	local size=${l:0:11}
	local sha2=${l:12:64}
	local file=${l:77}
	echo $file | /bin/grep -q -e ".tar$"
	if [ $? -ne 0 ];then
	    file_checksums="$file_checksums$sha2,$file\n"
	fi
    done
    echo -e "$file_checksums" > /tmp/valid-input.txt

    # Make script to test each stream's checksum
    # Called with filename as argument, stream via stdin
    /bin/cat >/tmp/verify.sh <<EOF
#!/bin/bash -e
filename="\$1"

a=\$(/usr/bin/sha256sum -b - | echo "\$(/bin/sed -e "s/ \*-/,/;")\$filename")

/bin/grep -F -q "\$a" /tmp/valid-input.txt

res=\$?
if [ \$res -ne 0 ]; then
  echo "Did not find matching checksum for file '\$filename'"
  exit 1
fi
EOF

    local crypt_files=
    for l in "${crypts[@]}"; do
	local size=${l:0:11}
	local sha2=${l:12:64}
	local file=${l:77}
	crypt_files="$crypt_files $file"
    done
    local gpg_cmd="/usr/bin/gpg -q --no-permission-warning -d"

    /bin/cat $crypt_files | $gpg_cmd | (/bin/tar -x -f - --to-command='/bin/bash -c "set -e && [[ \"$TAR_FILENAME\" == *.tar ]] && /bin/tar -x -f - --to-command=\"/bin/bash /tmp/verify.sh \\\"\\\$TAR_FILENAME\\\"\" || /bin/bash /tmp/verify.sh \"$TAR_FILENAME\""')

    local res=$?
    if [ $res -eq 0 ]; then
	echo "All files verified ok."
    fi
    
    return $res
}

if [ "$1" == "verify" ]; then
    shift

    if [ "$1" == "-s" ]; then
	verify_stream
	exit $?
    fi

    if [ "$1" == "-a" ]; then
	shift

	verify_files "archives" $*
	exit 0
    fi

    if [ "$1" == "-f" ]; then
	shift

	verify_files "files" $*
	exit 0
    fi

    if [ $# -eq 0 ]; then
	verify_files "crypts" "."
	exit 0
    fi

    usage_and_exit
    
elif [ "$1" == "unpack" ]; then
    shift
    
    unpack $*
    exit 0
elif [ "$1" == info ]; then
    shift
    
    info_and_exit $*
else
    usage_and_exit
fi
    
echo $#

