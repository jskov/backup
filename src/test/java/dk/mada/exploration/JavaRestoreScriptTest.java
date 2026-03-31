package dk.mada.exploration;


import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dk.mada.backup.restore.java.Restore;

/**
 * Test java-based restore script.
 */
public class JavaRestoreScriptTest {
	/** Test data. */
    Path testData = Paths.get("/home/jskov/git/_ebooks_backup_2026/ebooks.sh");

    @Test
    void cmdVerifyWorks() throws Exception {
        new Restore(testData).run(new ArrayList<>(List.of("verify", "-c", "/home/jskov/git/_ebooks_backup_2026")));
    }
}
