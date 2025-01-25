#!/bin/bash

# @name: @@BACKUP_NAME@@
# @version: @@VERSION@@
# @data_format_version: @@DATA_FORMAT_VERSION@@
# @gpg_key_id: @@BACKUP_KEY_ID@@
# @time: @@BACKUP_DATE_TIME@@
# @output_type: @@BACKUP_OUTPUT_TYPE@@

set -e

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

output_type=@@BACKUP_OUTPUT_TYPE@@
gpg_cmd="/bin/gpg -q --no-permission-warning -d"

fail() {
    local msg="$1"

    >&2 echo -e $msg
    exit 1
}

expect_file() {
    local size="$1"
    local xxh3="$2"
    local file="$3"
    local prefix="$4"

    if [[ ! -f "$file" ]]; then
        fail "\nDid not find expected file '$file'"
    fi

    local actual_size=$(/bin/stat -c "%s" "$file")
    if [[ "$actual_size" -ne "$size" ]]; then
        fail "\nFile $file has size $actual_size, but expected $size"
    fi

    local xxh3_output=$(/bin/xxhsum -H3 "$file")
    # This output used to be extraced by " -16", but the format changed to
    # 'XXH3_hex16 name' hence this extraction
    local actual_xxh3=${xxh3_output:5:16}
    if [[ "$actual_xxh3" != "$xxh3" ]]; then
        fail "\nFile $file has xxh3 '$actual_xxh3', but expected '$xxh3'"
    fi

    echo " $prefix$file... ok"
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
            if [[ $sel == "crypts" ]]; then
                @@VARS_MD5@@
            else
                @@VARS@@
            fi
            echo "${file} ${xxh3} ${size}"
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
    echo >/dev/stderr "Usage:"
    echo >/dev/stderr " restore [cmd]"
    echo >/dev/stderr
    echo >/dev/stderr "With cmd being one of:"
    echo >/dev/stderr
    echo >/dev/stderr "  info               information about backup"
    echo >/dev/stderr "  info -c            information about crypted backup files"
    echo >/dev/stderr "  info -a            information about archive files"
    echo >/dev/stderr "  info -f            information about the original files"
    echo >/dev/stderr
    echo >/dev/stderr "  unpack dir         unpacks all files to dir"
    echo >/dev/stderr "  unpack -a dir      unpacks (only) archives to dir"
    echo >/dev/stderr
    echo >/dev/stderr "  verify             verifies crypted backup files (locally)"
    echo >/dev/stderr "  verify -c dir      verifies crypted backup files in dir"
    echo >/dev/stderr "  verify -a dir      verifies decrypted archive files in dir"
    echo >/dev/stderr "  verify -f dir      verifies decrypted and unpacked files in dir"
    echo >/dev/stderr "  verify -s          decrypts and verifies files via streaming - prompts password"
    echo >/dev/stderr "  verify -j path     verifies MD5 checksum of backup files at Jotta path"

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
    echo "Verifying integrity of archives in '$files_dir'..."

    local i=1
    for l in "${array[@]}"; do
        if [[ $name == "crypts[@]" ]]; then
            @@VARS_MD5@@
        else
            @@VARS@@
        fi

        if ! (cd $files_dir; expect_file "$size" "$xxh3" "$file" "- ($i/$len) ") ; then
            exit 1
        fi
        i=$((i + 1))
    done

    echo "Success!"
}

unpack_encrypted_files() {
    local onlyArchives=$1
    shift
    local target="$1"
    shift
    local crypt_files="$@"

    if $onlyArchives; then
        /bin/cat $crypt_files | $gpg_cmd | (cd "$target" && /bin/tar -x -f -)
    else
        /bin/cat $crypt_files | $gpg_cmd | (cd "$target" && /bin/tar -x -f - --to-command='/bin/bash -c "[[ \"$TAR_FILENAME\" == ./* ]] && /bin/tar -x -f - || /bin/cat > \"$TAR_FILENAME\""')
    fi
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
    local target="$1"

    if [ -e "$target" ]; then
        fail "Will not unpack to existing target $target"
    fi

    /bin/mkdir "$target"

    if $onlyArchives; then
        echo "Unpacking directory archives..."
    else
        echo "Unpacking full backup..."
    fi

    if [[ $output_type == "NAMED" ]]; then
        # Unpack files individually
        for l in "${crypts[@]}"; do
            @@VARS_MD5@@
            echo "See $l"
            unpack_encrypted_files $onlyArchives "$target" "$file"
        done
    elif [[ $output_type == "NUMBERED" ]]; then
        # Unpack as one big file
        local crypt_files=
        for l in "${crypts[@]}"; do
            @@VARS_MD5@@
            crypt_files="$crypt_files $file"
        done
        unpack_encrypted_files $onlyArchives "$target" "$crypt_files"
    else
        echo >/dev/stderr "Unexpected output type: $output_type"
        exit 1
    fi

    if $onlyArchives; then
        verify_files "archives" "$target"
    else
        verify_files "files" "$target"
    fi
}


match_jotta() {
    local file="$1"
    local md5="$2"
    local jotta_state="$3"

    /bin/cat $jotta_state | /bin/grep -E -q "$file.*$md5"
}

verify_jotta() {
    local jotta_path="$1"

    local ok="\xE2\x9C\x94"
    local bad="\xE2\x9D\x8C"

    local success=1
    
    echo -e "Checking backup files at Jotta cloud path $jotta_path\n"
    
    local jotta_state=$(mktemp)
    /bin/jotta-cli ls -l "$jotta_path" > $jotta_state

    local file=$(basename $0)
    if match_jotta $file $(/bin/md5sum $0 | /bin/cut -f 1 -d' ') $jotta_state; then
        echo -e " $ok $file"
    else
        echo -e " $bad $file"
        success=0
    fi

    for l in "${crypts[@]}"; do
        @@VARS_MD5@@

        if match_jotta "$file" "$md5" "$jotta_state"; then
            echo -e " $ok $file"
        else
            echo -e " $bad $file"
            success=0
        fi
    done

    if [[ $success == 1 ]]; then
        echo -e "\nAll files ok!"
        /bin/rm -f $jotta_state
    else
        echo >/dev/stderr -e "\nSome files did not match Jotta listing:\n"
        /bin/cat >/dev/stderr $jotta_state
        /bin/rm -f $jotta_state
        exit 1
    fi
}

verify_crypted_files() {
    local files="$@"
    /bin/cat $files | $gpg_cmd | (/bin/tar -x -f - --to-command='/bin/bash -c "set -e && [[ \"$TAR_FILENAME\" == ./* ]] && /bin/tar -x -f - --to-command=\"/bin/bash /tmp/verify.sh \\\"\\\$TAR_FILENAME\\\"\" || /bin/bash /tmp/verify.sh \"$TAR_FILENAME\""')
}

verify_stream() {
    # Make a file with file/checksum lines for all (nested) files and root files
    local file_checksums=
    for l in "${files[@]}"; do
        @@VARS@@
        file_checksums="$file_checksums$xxh3,$file\n"
    done
    for l in "${archives[@]}"; do
        @@VARS@@

        if ! (echo $file | /bin/grep -q -e "^[.]/") ; then
            file_checksums="${file_checksums}${xxh3},${file}\n"
        fi
    done
    echo -e "$file_checksums" > /tmp/valid-input.txt

    # Make script to test each stream's checksum
    # Called with filename as argument, stream via stdin
    /bin/cat >/tmp/verify.sh <<EOF
#!/bin/bash

set -e

filename="\$1"

a=\$(/bin/xxhsum -H3 - | echo "\$(/bin/cut -c 6-21),\$filename")

if ! (/bin/grep -F -q "\$a" /tmp/valid-input.txt) ; then
  echo >/dev/stderr "Did not find matching checksum for file '\$filename'"
  exit 1
fi
EOF

    if [[ $output_type == "NAMED" ]]; then
        # Verify encrypted files individually
        for l in "${crypts[@]}"; do
            @@VARS_MD5@@
            verify_crypted_files $file
        done
    elif [[ $output_type == "NUMBERED" ]]; then
        # Verify encrypted files as one big file
        local crypt_files=
        for l in "${crypts[@]}"; do
            @@VARS_MD5@@
            crypt_files="$crypt_files $file"
        done
        verify_crypted_files $crypt_files
    else
        echo >/dev/stderr "Unexpected output type: $output_type"
        exit 1
    fi
    echo "All files verified ok."
}

# Excludes coreutils binaries
expected_tools="/bin/xxhsum /bin/tar /bin/gpg /bin/grep"
for t in $expected_tools; do
    if [[ ! -x $t ]]; then
        echo >/dev/stderr "Script requires tool: $t"
        exit 1
    fi
done

if [ "$1" == "verify" ]; then
    shift

    if [ "$1" == "-s" ]; then
        verify_stream
        exit 0
    fi

    if [ "$1" == "-j" ]; then
        shift

        if [[ ! -x /bin/jotta-cli ]]; then
            echo >/dev/stderr "Script requires tool: $t"
            exit 1
        fi

        verify_jotta "$@"
        exit 0
    fi

    if [ "$1" == "-a" ]; then
        shift

        verify_files "archives" "$@"
        exit 0
    fi

    if [ "$1" == "-f" ]; then
        shift

        verify_files "files" "$@"
        exit 0
    fi

    if [ "$1" == "-c" ]; then
        shift

        verify_files "crypts" "$@"
        exit 0
    fi

    if [ $# -eq 0 ]; then
        verify_files "crypts" "."
        exit 0
    fi

    usage_and_exit

elif [ "$1" == "unpack" ]; then
    shift

    unpack "$@"
    exit 0
elif [ "$1" == info ]; then
    shift

    info_and_exit "$@"
else
    usage_and_exit
fi
    
echo $#
