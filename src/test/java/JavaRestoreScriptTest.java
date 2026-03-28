

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Test java-based restore script.
 */
public class JavaRestoreScriptTest {
	/** Test data. */
    Path testData = Paths.get("./src/test/resources/data/java-restore/data.txt");

    @Test
    void cmdVerifyWorks() throws Exception {
        new Restore(testData).run(new ArrayList<>(List.of("verify", "-c", "/home/jskov/git/_java_restore_ebooks")));
    }
}
