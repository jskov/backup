

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs validation on Jotta cloud.
 *
 * This test must be run in a context where jotta-cli is available and connected.
 * <pre>./gradlew test --tests JavaRestoreScriptVerifyJottaTest</pre>
 */
@EnabledIfEnvironmentVariable(named = "USER", matches = "jskov")
public class JavaRestoreScriptVerifyJottaTest {
	/** Test data. */
    Path testData = Paths.get("./src/test/resources/data/java-restore/data.txt");

    /** Check that files on Jotta matches. */
    @Test
    void cmdVerifyJottaWorks() throws Exception {
        new Restore(testData).run(new ArrayList<>(List.of("verify", "-j", "archive/backup/ebooks")));
    }
}
