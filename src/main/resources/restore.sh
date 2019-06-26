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
    echo "Backup of directory >>2004<<"
    echo " created on >>2019.06.11 14:45<<"
    echo " original size >>1.3GB<<"
    echo " encrypted with key id >>CA2DE6A3<<"
    echo " ${#crypts[@]} crypted archive(s) contains ${#files[@]} files in ${#archives[@]} nested archives"

    local name=$1[@]
    local array=("${!name}")
    if [ "$1" == "crypts" -o "$1" == "files" -o "$1" == "archives" ]; then
	for l in "${array[@]}"; do
	    local file=${l:77}
	    local size=${l:0:11}
	    local sha2=${l:12:64}
	    echo "  ${file} ${sha2} ${size}"
	done
    fi

    exit 0
}

usage_and_exit() {
    # usage
    echo "Usage:"
    echo " restore [cmd]"
    echo "With cmd being one of:"
    echo "  verify             verifies crypted backup files"
    echo "  verify -a dir      verifies decrypted archive files in dir"
    echo "  verify -f dir      verifies decrypted and unpacked files in dir"

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
    echo "Verifying integrity of archives in $files_dir"

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
    if [ $# -ne 1 ]; then
	fail "Unpack expects one argument, the destination directory"
    fi
    local target=$1

    if [ -e "$target" ]; then
	fail "Will not unpack to existing target $target"
    fi

    echo "Unpacking directory archives"
    mkdir "$target"
    tar -x -C "$target" -f _backup.tar

    verify_files "archives" "$target"
}


if [ "$1" == "verify" ]; then
    shift

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

