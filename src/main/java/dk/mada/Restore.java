package dk.mada;

import java.util.List;
import java.util.stream.Collectors;

public class Restore {
    private void parse() {
        List<Crypt> crypts = CRYPTS.stream()
                .map(l -> new Crypt(Long.valueOf(l.substring(0, 11).trim()), l.substring(12,28), l.substring(29, 61), l.substring(6)))
                .toList();
        
        List<Archive> archives = ARCHIVES.stream()
                .map(l -> new Archive(Long.valueOf(l.substring(0, 11).trim()), l.substring(12,28), l.substring(29)))
                .toList();

        List<File> files = FILES.stream()
                .map(l -> new File(Long.valueOf(l.substring(0, 11).trim()), l.substring(12,28), l.substring(29)))
                .toList();

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
        new Restore().parse();
    }

    record Crypt(long size, String xxh, String md5, String name) {}
    record Archive(long size, String xxh, String name) {}
    record File(long size, String xxh, String name) {}

    // @@DATA@@
    private static final List<String> CRYPTS = List.of(
"       2630,eeaa9ecdb4961049,4f97a045c23b72a8a6413dd9395f1b0f,0_backup-readme.txt.crypt",
"     187539,8972ecd25bcf88ed,d17f317b73393c27132d4300a66be78d,0_meta.crypt",
"       2630,020c69e54109babc,bc828aa363bc1ac2a2c43eac6a94fd43,0_sync-readme.txt.crypt",
"     265915,f88ef3db6a0173a8,a492142b177b75c909cd4b08d311802b,_meta.crypt",
"    2154490,0cf0ea69e14bc355,f918ee202b60e18e4ea089c3e89ba9f9,Abrahams__Tom.crypt"
);
    private static final List<String> ARCHIVES = List.of(
"        181,c803dfcc236557b2,0_backup-readme.txt",
"     185344,64f5c95fa865afa9,./0_meta.tar",
"        487,d489cbed2b278a3c,0_sync-readme.txt",
"     263680,7efa4b13d954259d,./_meta.tar",
"    2151424,77ff1a028279dba4,./Abrahams, Tom.tar"
);
    private static final List<String> FILES = List.of(
"        181,c803dfcc236557b2,0_backup-readme.txt",
"       1933,ca08410a13372fb3,0_meta/amazon.com.txt",
"        440,1e6033d4495cf815,0_meta/bookfunnel.com.txt",
"      45388,26804c416e66d665,0_meta/origin.txt",
"     133928,236d6ddb355183e8,0_meta/storygraph-export-2025.08.24.csv",
"        487,d489cbed2b278a3c,0_sync-readme.txt",
"     127373,845e8597b8af8950,_meta/storygraph-export-2024.12.27.csv",
"     133800,80c32b25733ee5c6,_meta/storygraph-export-2025.07.19.csv",
"    1021388,73ac231869538a9d,Abrahams, Tom/Descent - Tom Abrahams.epub",
"     314598,5edf3dd1feff873a,Abrahams, Tom/Retrograde.epub",
"     812232,7a2ab3217b3baa58,Abrahams, Tom/Spaceman - Tom Abrahams.epub"
);
}
