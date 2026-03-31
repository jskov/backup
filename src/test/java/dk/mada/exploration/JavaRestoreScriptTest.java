package dk.mada.exploration;

import static org.assertj.core.api.Assertions.assertThat;

import dk.mada.backup.restore.java.Restore;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Test java-based restore script.
 */
public class JavaRestoreScriptTest {
    /** Test data. */
    Path testData = Paths.get("/home/jskov/git/_ebooks_backup_2026/ebooks.sh");

    @Test
    void cmdTesting() throws Exception {
        int res = Restore.mainReturn(new String[] {"-b", "/home/jskov/git/_ebooks_backup_2026/ebooks.sh", "info", "--full"});
        assertThat(res).isEqualTo(0);
    }

    
    @Test
    void cmdVerifyWorks() throws Exception {
        int res = Restore.mainReturn(new String[] {"-b", "/home/jskov/git/_ebooks_backup_2026/ebooks.sh", "verify"});
        assertThat(res).isEqualTo(0);
    }

    @Test
    void cmdVerifyFailExit() throws Exception {
        int res = Restore.mainReturn(
                new String[] {"-b", "/home/jskov/git/_ebooks_backup_2026/ebooks_broken.sh", "verify"});
        assertThat(res).isEqualTo(-1);
    }
}
