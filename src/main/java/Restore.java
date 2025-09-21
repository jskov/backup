//#!/bin/env -S  bash -c 'j="$(mktemp -t backup-XXX)".java; export BACKUP_DATA="${j}.data"; m=$(grep -E -n "^//EOI" "$0" | sed 's/:.*//') ; tail -n $m "$0" | tail -n +2 > "$j"; tail -n +$m "$0" > "$BACKUP_DATA"; java --source 25 "$j" "$@"'
//-
//- Lines starting with //- will be trimmed from the output.
//-
//- The shebang allows splitting the script file into a .java-named script for execution (top of the script)
//- and .java.data-named file containing the data (the bottom lines of the script).
//-
//- Note that it needs to be shorted than 255 chars!

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class Restore {
    public void run(Path data, List<String> args) throws Exception {
        List<Crypt> crypts = new ArrayList<>();
        List<Archive> archives = new ArrayList<>();
        List<File> files = new ArrayList<>();

        List<String> lines = Files.readAllLines(data);
        int iCrypts = lines.indexOf("##crypts##");
        int iArchives = lines.indexOf("##archives##");
        int iFiles = lines.indexOf("##files##");
        for (int i = iCrypts + 1; i < iArchives; i++) {
            String l = lines.get(i);
            crypts.add(new Crypt(Long.valueOf(l.substring(0, 11).trim()), l.substring(12,28), l.substring(29, 61), l.substring(6)));
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
    }
    
    public static final void main(String[] args) {
        String data = System.getenv("BACKUP_DATA");
        if (data == null) {
            err("The variable BACKUP_DATA must point to a data file!");
        }
        try {
            new Restore().run(Paths.get(data), List.of(args));
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
}

//EOI - java parser stops after this line (due to byte 0x1a, end-of-input) 
