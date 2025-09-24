//#!/bin/env -S  bash -c 'j="$(mktemp -t backup-XXX)".java; export BACKUP_DATA="${j}.data"; m=$(grep -E -n "^//EOI" "$0" | sed 's/:.*//') ; tail -n $m "$0" | tail -n +2 > "$j"; tail -n +$m "$0" > "$BACKUP_DATA"; java --source 25 "$j" "$@"'
//-
//- Lines starting with //- will be trimmed from the output.
//-
//- The shebang allows splitting the script file into a .java-named script for execution (top of the script)
//- and .java.data-named file containing the data (the bottom lines of the script).
//-
//- Note that it needs to be shorted than 255 chars!

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Restore {
    String BACKUP_NAME = "@@BACKUP_NAME@@";
    String VERSION = "@@VERSION@@";
    String DATA_FORMAT_VERSION = "@@DATA_FORMAT_VERSION@@";
    String BACKUP_KEY_ID = "@@BACKUP_KEY_ID@@";
    String BACKUP_DATE_TIME = "@@BACKUP_DATE_TIME@@";
    String BACKUP_OUTPUT_TYPE = "@@BACKUP_OUTPUT_TYPE@@";

    Data data;
    
    Restore(Path datafile) {
        data = parseData(datafile);
    }
    
    public void run(List<String> args) throws Exception {
        System.out.println(": " + args);
        if (args.size() == 0) {
            usage();
        }
        
        switch(args.removeFirst()) {
        case "info" -> cmdInfo(args);
        }
        
        Path dir = Paths.get("/var/home/jskov/git/_java_restore_ebooks");
        
        data.crypts().stream()
            .forEach(c -> System.out.println(md5Sum(dir.resolve(c.name()))));
    }
    
    private void cmdInfo(List<String> args) {
        exit("Backup " + BACKUP_NAME + "\n"
                + "made with backup version " + VERSION + "\n"
                + "created on " + BACKUP_DATE_TIME + "\n"
                + "original size @@BACKUP_INPUT_SIZE@@" + "\n"
                + "encrypted with key id " + BACKUP_KEY_ID + "\n"
                + data.crypts().size() + " crypted archive(s) contains " + data.files().size() + " files in " + data.archives().size() + " nested archives\n");
    }
    
    private void usage() {
        exit("""
Usage:
 restore [cmd]

With cmd being one of:

  info               information about backup
  info -c            information about crypted backup files
  info -a            information about archive files
  info -f            information about the original files

  unpack dir         unpacks all files to dir
  unpack -a dir      unpacks (only) archives to dir

  verify             verifies crypted backup files (locally)
  verify -c dir      verifies crypted backup files in dir
  verify -a dir      verifies decrypted archive files in dir
  verify -f dir      verifies decrypted and unpacked files in dir
  verify -s          decrypts and verifies files via streaming - prompts password
  verify -j path     verifies MD5 checksum of backup files at Jotta path""");
    }
    
    
    private void exit(String msg) {
        System.out.println(msg);
        System.exit(0);
    }

    private void exit() {
        System.exit(0);
    }

    private Data parseData(Path datafile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(datafile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading data " + datafile, e);
        }

        List<Crypt> crypts = new ArrayList<>();
        List<Archive> archives = new ArrayList<>();
        List<File> files = new ArrayList<>();
        int iCrypts = lines.indexOf("##crypts##");
        int iArchives = lines.indexOf("##archives##");
        int iFiles = lines.indexOf("##files##");
        for (int i = iCrypts + 1; i < iArchives; i++) {
            String l = lines.get(i);
            crypts.add(new Crypt(Long.valueOf(l.substring(0, 11).trim()), l.substring(12,28), l.substring(29, 61), l.substring(62)));
        }            
        for (int i = iArchives + 1; i < iFiles; i++) {
            String l = lines.get(i);
            archives.add(new Archive(Long.valueOf(l.substring(0, 11).trim()), l.substring(12,28), l.substring(29)));
        }            
        for (int i = iFiles + 1; i < lines.size(); i++) {
            String l = lines.get(i);
            files.add(new File(Long.valueOf(l.substring(0, 11).trim()), l.substring(12,28), l.substring(29)));
        }            

        System.out.println(" " + crypts.stream()
                    .map(Crypt::toString)
                    .collect(Collectors.joining("\n ")));

        System.out.println(" " + archives.stream()
        .map(Archive::toString)
        .collect(Collectors.joining("\n ")));
        System.out.println(" " + files.stream()
            .map(File::toString)
            .collect(Collectors.joining("\n ")));
        
        return new Data(crypts, archives, files);
    }
        
    private String xxhSum(Path f) {
        String out = runExternalCmd(List.of("xxhsum", "-H3", f.toAbsolutePath().toString()));
        return out.substring(out.indexOf(" = ") + 3);
    }

    private String md5Sum(Path f) {
        String out = runExternalCmd(List.of("md5sum", f.toAbsolutePath().toString()));
        return out.substring(0, 31);
    }

    private String runExternalCmd(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
            return p.inputReader().lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed running cmd: " + cmd, e);
        }
    }
    
    public static final void main(String[] args) {
        String data = System.getenv("BACKUP_DATA");
        if (data == null) {
            err("The variable BACKUP_DATA must point to a data file!");
        }
        try {
            new Restore(Paths.get(data)).run(new ArrayList<>(List.of(args)));
        } catch (Exception e) {
            err("Failed processing " + data + ": " + e.getMessage());
        }
    }

    private static void err(String msg) {
        System.err.println(msg);
        System.exit(1);
    }
    
    record Crypt(long size, String xxh, String md5, String name) {}
    record Archive(long size, String xxh, String name) {}
    record File(long size, String xxh, String name) {}
    record Data(List<Crypt> crypts, List<Archive> archives, List<File> files) {}
}

//EOI - java parser stops after this line (due to byte 0x1a, end-of-input) 
