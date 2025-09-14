package dk.mada;

import java.util.List;
import java.util.stream.Collectors;

public class Restore {
    private void parse() {
        List<Crypt> crypts = CRYPTS.lines()
                .map(l -> new Crypt(Long.valueOf(l.substring(0, 11).trim()), l.substring(12,28), l.substring(29, 61), l.substring(6)))
                .toList();
        System.out.println(" " + crypts.stream()
                    .map(Crypt::toString)
                    .collect(Collectors.joining("\n ")));
    }
    
    
    
    
    
    public static final void main(String[] args) {
        new Restore().parse();
    }

    record Crypt(long size, String md5, String xx3sum, String name) {}
    
    private static final String CRYPTS = """
       2630,eeaa9ecdb4961049,4f97a045c23b72a8a6413dd9395f1b0f,0_backup-readme.txt.crypt
     187539,8972ecd25bcf88ed,d17f317b73393c27132d4300a66be78d,0_meta.crypt         
       2630,020c69e54109babc,bc828aa363bc1ac2a2c43eac6a94fd43,0_sync-readme.txt.crypt  
     265915,f88ef3db6a0173a8,a492142b177b75c909cd4b08d311802b,_meta.crypt
    2154490,0cf0ea69e14bc355,f918ee202b60e18e4ea089c3e89ba9f9,Abrahams__Tom.crypt
""";
}
