

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

public class JavaRestoreScriptTest {

    @Test
    void canRun() throws Exception {
        Path testData = Paths.get("./src/test/resources/data/java-restore/data.txt");
        new Restore().run(testData, List.of());
    }
}
